/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.OrgChartPosition;
import de.greluc.krt.iri.basetool.backend.model.OrgChartPositionType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link OrgChartPosition}. The read methods eagerly fetch the {@code
 * user} (and, for unit-scoped rows, the {@code orgUnit}) via an entity graph so {@code
 * OrgChartService} can assemble the whole chart without an N+1 storm; the count / exists methods
 * back the cardinality and one-user-per-scope guards the service enforces before a write.
 */
@Repository
public interface OrgChartPositionRepository extends JpaRepository<OrgChartPosition, UUID> {

  /**
   * Returns every area-leadership position (Bereichsleitung — {@code org_unit_id IS NULL}), ordered
   * for display, with the holding user fetched. Used by the chart-assembly read path.
   *
   * @return the area-leadership positions, ordered by sort index then creation time; never {@code
   *     null}, possibly empty.
   */
  @EntityGraph(attributePaths = {"user", "parent"})
  List<OrgChartPosition> findAllByOrgUnitIsNullOrderBySortIndexAscCreatedAtAsc();

  /**
   * Returns every position belonging to one of the given OrgUnits (the profit-eligible Staffeln +
   * SKs), ordered for display, with the user and OrgUnit fetched. Callers must pass a non-empty
   * collection — {@code OrgChartService} skips this query entirely when no profit-eligible unit
   * exists, avoiding a dialect-fragile empty {@code IN ()}.
   *
   * @param orgUnitIds the OrgUnit ids to load positions for; must be non-empty.
   * @return the matching positions, ordered by sort index then creation time; never {@code null}.
   */
  @EntityGraph(attributePaths = {"user", "orgUnit", "parent"})
  List<OrgChartPosition> findAllByOrgUnitIdInOrderBySortIndexAscCreatedAtAsc(
      Collection<UUID> orgUnitIds);

  /**
   * Counts how many positions of the given type already exist in one OrgUnit. Backs the per-Staffel
   * limits (≤4 Kommandoleiter, ≤4 Ensign) and the per-SK limit (≤2 SK-Leiter).
   *
   * @param orgUnitId the OrgUnit to count within; never {@code null}.
   * @param positionType the functional rank to count; never {@code null}.
   * @return the current number of positions of that type in that OrgUnit.
   */
  long countByOrgUnitIdAndPositionType(UUID orgUnitId, OrgChartPositionType positionType);

  /**
   * Whether an area-leadership position of the given type already exists ({@code org_unit_id IS
   * NULL}). Backs the "exactly one Bereichsleiter" guard before the {@code
   * uq_org_chart_one_area_lead} index would reject the insert.
   *
   * @param positionType the area-leadership rank to check (typically {@code AREA_LEAD}); never
   *     {@code null}.
   * @return {@code true} iff such an area-leadership position already exists.
   */
  boolean existsByOrgUnitIsNullAndPositionType(OrgChartPositionType positionType);

  /**
   * Whether a child position of the given type already hangs off the given parent. Backs the "at
   * most one Stv. Kommandoleiter per Kommandoleiter" guard, mirroring the {@code
   * uq_org_chart_one_deputy_per_command} index.
   *
   * @param parentId the parent position id; never {@code null}.
   * @param positionType the child rank to check (typically {@code DEPUTY_COMMAND_LEAD}); never
   *     {@code null}.
   * @return {@code true} iff such a child already exists under that parent.
   */
  boolean existsByParentIdAndPositionType(UUID parentId, OrgChartPositionType positionType);

  /**
   * Whether the user already holds any position in the given OrgUnit. Backs the "a user appears at
   * most once per Staffel/SK" guard, surfacing a friendly error before the {@code
   * uq_org_chart_user_per_unit} index would reject the insert.
   *
   * @param orgUnitId the OrgUnit to check; never {@code null}.
   * @param userId the candidate user; never {@code null}.
   * @return {@code true} iff the user already has a position in that OrgUnit.
   */
  boolean existsByOrgUnitIdAndUserId(UUID orgUnitId, UUID userId);

  /**
   * Whether the user already holds an area-leadership position ({@code org_unit_id IS NULL}). Backs
   * the "a user appears at most once in the Bereichsleitung" guard, mirroring the {@code
   * uq_org_chart_user_in_area} index.
   *
   * @param userId the candidate user; never {@code null}.
   * @return {@code true} iff the user already has an area-leadership position.
   */
  boolean existsByOrgUnitIsNullAndUserId(UUID userId);
}

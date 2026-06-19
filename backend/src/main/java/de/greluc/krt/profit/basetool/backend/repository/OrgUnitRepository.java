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

package de.greluc.krt.profit.basetool.backend.repository;

import de.greluc.krt.profit.basetool.backend.model.OrgUnit;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository over the polymorphic {@link OrgUnit} base entity. Unlike {@link
 * SquadronRepository} and {@link SpecialCommandRepository} — each narrowed to one discriminator
 * subtype — this repository loads {@code org_unit} rows of <em>either</em> kind through Hibernate's
 * single-table inheritance, returning the matching concrete subclass ({@code Squadron} or {@code
 * SpecialCommand}) per row.
 *
 * <p>Used by {@code MissionService} when stamping a participant's affiliations: a participant may
 * be linked to a Staffel and one or more Spezialkommandos at once, so the service resolves the
 * caller's membership org-unit ids to managed {@link OrgUnit} entities here rather than branching
 * on kind and dispatching to the two kind-specific repositories.
 */
@Repository
public interface OrgUnitRepository extends JpaRepository<OrgUnit, UUID> {

  /**
   * Counts how many of the given org-unit ids are flagged {@code is_profit_eligible} — works across
   * both kinds (Squadron + SpecialCommand) via single-table inheritance. Drives {@code
   * OwnerScopeService.canViewJobOrders()}: a caller may enter the Job-Order area iff at least one
   * of their membership org units is profit-eligible, so the service only needs the {@code > 0}
   * answer. Callers must pass a non-empty collection — an empty {@code IN ()} renders
   * inconsistently across dialects, so {@code OwnerScopeService} short-circuits the empty case
   * before calling this.
   *
   * @param ids the org-unit ids to inspect (the caller's membership ids); must be non-empty.
   * @return the number of those ids whose org unit is profit-eligible; {@code 0} when none.
   */
  @Query("SELECT COUNT(o) FROM OrgUnit o WHERE o.id IN :ids AND o.isProfitEligible = true")
  long countProfitEligibleByIdIn(@Param("ids") Collection<UUID> ids);

  /**
   * Loads every active, profit-eligible org unit across both kinds (Squadron + SpecialCommand) via
   * single-table inheritance. Backs the Profit-Bereich org chart, whose unit tier is exactly the
   * profit-eligible Staffeln + SKs; the caller splits the result by {@link OrgUnit#getKind()} into
   * the squadron and SK columns.
   *
   * @return the active profit-eligible org units in arbitrary order; never {@code null}, possibly
   *     empty.
   */
  @Query("SELECT o FROM OrgUnit o WHERE o.active = true AND o.isProfitEligible = true")
  List<OrgUnit> findActiveProfitEligible();

  /**
   * Returns the direct children of {@code parentOrgUnitId} in the org hierarchy (epic #692,
   * REQ-ORG-014): the Staffeln + SKs of a Bereich, or the Bereiche of the Organisationsleitung.
   * Returns every kind via single-table inheritance; the caller filters by {@link
   * OrgUnit#getKind()} if it needs a specific tier. The cascading-scope resolver (REQ-ORG-015)
   * walks this one level at a time to expand a leader's reach to their subordinate units.
   *
   * @param parentOrgUnitId the parent org unit whose direct children to load; never {@code null}.
   * @return the direct children in arbitrary order; never {@code null}, possibly empty.
   */
  @Query("SELECT o FROM OrgUnit o WHERE o.parent.id = :parentOrgUnitId")
  List<OrgUnit> findByParentOrgUnitId(@Param("parentOrgUnitId") UUID parentOrgUnitId);

  /**
   * Id-only projection of {@link #findByParentOrgUnitId(UUID)}: the ids of the direct children of
   * {@code parentOrgUnitId} (the Staffeln + SKs of a Bereich). Used by the cascading-scope resolver
   * ({@link de.greluc.krt.profit.basetool.backend.service.OrgUnitCascadeService}, REQ-ORG-015) to
   * expand a Bereichsleitung member's reach to their subordinate units without hydrating the full
   * {@link OrgUnit} rows. The fixed three-level hierarchy (OL &gt; Bereich &gt; Staffel/SK) means a
   * Bereich's children are exactly the leaf units, so one call yields the whole subtree below a
   * Bereich.
   *
   * @param parentOrgUnitId the parent org unit whose direct child ids to load; never {@code null}.
   * @return the direct child org-unit ids in arbitrary order; never {@code null}, possibly empty.
   */
  @Query("SELECT o.id FROM OrgUnit o WHERE o.parent.id = :parentOrgUnitId")
  List<UUID> findChildOrgUnitIds(@Param("parentOrgUnitId") UUID parentOrgUnitId);

  /**
   * Returns the id of every org unit across all kinds (Squadron, SK, Bereich, OL) via single-table
   * inheritance. Backs the Organisationsleitung branch of the cascading-scope resolver ({@link
   * de.greluc.krt.profit.basetool.backend.service.OrgUnitCascadeService}, REQ-ORG-015): an OL
   * member's reach is the concrete union of <em>every</em> org-unit id — deliberately materialised
   * rather than collapsed into an admin-all marker, so OL/Bereich leadership never inherits the
   * admin carve-outs (the HARD INVARIANT of REQ-ORG-015). Including units with a {@code null}
   * parent (the additive-soak window before the hierarchy is wired up) is intentional: OL reach is
   * "everything", not "everything reachable through a parent edge".
   *
   * @return every org-unit id in arbitrary order; never {@code null}, possibly empty.
   */
  @Query("SELECT o.id FROM OrgUnit o")
  List<UUID> findAllOrgUnitIds();

  /**
   * Loads every active {@link de.greluc.krt.profit.basetool.backend.model.Bereich} (epic #692,
   * REQ-ORG-018). Backs the multi-Bereich org chart's tier list: each Bereich renders as its own
   * leadership sub-tree, coloured by its {@link
   * de.greluc.krt.profit.basetool.backend.model.Department Department}, with its child Staffeln/SKs
   * grouped underneath. Returns the {@code BEREICH} discriminator only via the typed JPQL {@code
   * FROM Bereich}.
   *
   * @return the active Bereiche in arbitrary order; never {@code null}, possibly empty.
   */
  @Query("SELECT o FROM Bereich o WHERE o.active = true")
  List<OrgUnit> findActiveBereiche();

  /**
   * Loads the active {@link de.greluc.krt.profit.basetool.backend.model.Organisationsleitung} (epic
   * #692, REQ-ORG-018) — normally a singleton. Backs the OL root tier of the org chart.
   *
   * @return the active OL row(s) in arbitrary order; never {@code null}, normally one or zero.
   */
  @Query("SELECT o FROM Organisationsleitung o WHERE o.active = true")
  List<OrgUnit> findActiveOrganisationsleitung();
}

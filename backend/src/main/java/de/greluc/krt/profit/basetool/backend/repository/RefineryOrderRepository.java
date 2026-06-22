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

import de.greluc.krt.profit.basetool.backend.model.RefineryOrder;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Refinery Order. */
@Repository
public interface RefineryOrderRepository extends JpaRepository<RefineryOrder, UUID> {
  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * LocationId}.
   */
  boolean existsByLocationId(UUID locationId);

  /**
   * Derived Spring-Data query - returns entities matching {@code MissionId}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  List<RefineryOrder> findByMissionId(UUID missionId);

  /**
   * Derived Spring-Data query - returns entities matching {@code MissionIdAndOwnerId}. Eagerly
   * fetches the configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  List<RefineryOrder> findByMissionIdAndOwnerId(UUID missionId, UUID ownerId);

  /**
   * Org-unit-scoped variant of {@link #findByMissionId(UUID)}: returns only the refinery orders
   * linked to {@code missionId} that fall within the caller's effective scope, encoded as the
   * standard {@code isAdminAllScope} / {@code activeOrgUnitId} / {@code memberOrgUnitIds} triple
   * (see {@link de.greluc.krt.profit.basetool.backend.service.ScopePredicate}). Refinery is a
   * strict-staffel aggregate with no cross-squadron escape clause, so an order owned by a foreign
   * org unit is never returned - even when the linked mission is itself publicly visible.
   *
   * <p>This closes the BAC-004 cross-org-unit leak on the mission-scoped refinery endpoint {@code
   * GET /api/v1/refinery-orders/mission/{missionId}}, where a logistician of one squadron could
   * otherwise read another squadron's refinery financials by enumerating that squadron's public
   * missions. Eagerly fetches the configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  @Query(
      "SELECT r FROM RefineryOrder r WHERE r.mission.id = :missionId AND ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND r.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND r.owningOrgUnit.id IN :memberOrgUnitIds)"
          + " )")
  List<RefineryOrder> findByMissionIdScoped(
      @Param("missionId") UUID missionId,
      @Param("isAdminAllScope") boolean isAdminAllScope,
      @Param("activeOrgUnitId") UUID activeOrgUnitId,
      @Param("memberOrgUnitIds") java.util.Collection<UUID> memberOrgUnitIds);

  /**
   * Derived Spring-Data query - returns entities matching {@code MissionIdIn}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  List<RefineryOrder> findByMissionIdIn(List<UUID> missionIds);

  /**
   * Derived Spring-Data query - returns entities matching {@code OwnerId}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  List<RefineryOrder> findByOwnerId(UUID ownerId);

  /**
   * Derived Spring-Data query - returns entities matching {@code OwnerId}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  Page<RefineryOrder> findByOwnerId(UUID ownerId, Pageable pageable);

  /**
   * Derived Spring-Data query - returns entities matching {@code OwnerIdAndStatusIn}. Eagerly
   * fetches the configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  Page<RefineryOrder> findByOwnerIdAndStatusIn(
      UUID ownerId,
      List<de.greluc.krt.profit.basetool.backend.model.RefineryOrderStatus> statuses,
      Pageable pageable);

  /**
   * Loads the caller's own refinery orders in the given statuses with their {@code goods} (and each
   * good's {@code outputMaterial}) eagerly fetched, for the blueprint craftability calculation
   * (#781). Strictly owner-scoped ({@code ownerId}), never org-unit-scoped — craftability folds in
   * only the caller's <em>own</em> not-yet-completed refinery yield. Callers pass {@code OPEN} +
   * {@code IN_PROGRESS} ("not yet completed or cancelled"). The {@code outputMaterial} is part of
   * the graph so the units→SCU conversion in the service reads the quantity type without an N+1.
   *
   * @param ownerId the owning user; never {@code null}
   * @param statuses the statuses to include (typically {@code OPEN}, {@code IN_PROGRESS})
   * @return the matching orders with goods loaded; never {@code null}, possibly empty
   */
  @EntityGraph(attributePaths = {"goods", "goods.outputMaterial"})
  @Query(
      "SELECT DISTINCT r FROM RefineryOrder r WHERE r.owner.id = :ownerId AND r.status IN"
          + " :statuses")
  List<RefineryOrder> findOwnedWithGoodsByStatusIn(
      @Param("ownerId") UUID ownerId,
      @Param("statuses")
          java.util.Collection<de.greluc.krt.profit.basetool.backend.model.RefineryOrderStatus>
              statuses);

  /**
   * Derived Spring-Data query - returns entities matching {@code StatusIn}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  Page<RefineryOrder> findByStatusIn(
      List<de.greluc.krt.profit.basetool.backend.model.RefineryOrderStatus> statuses,
      Pageable pageable);

  /**
   * Lists every entity. Overridden here to attach an {@code @EntityGraph}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @Override
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  Page<RefineryOrder> findAll(Pageable pageable);

  /**
   * Multi-tenant variant of {@link #findAll(Pageable)}: returns every refinery order whose owning
   * squadron matches {@code owningSquadronId}, or every order when {@code owningSquadronId} is
   * {@code null} (admin "all squadrons" mode). Refinery is a strict-staffel aggregate - there is no
   * cross-squadron escape clause like Mission's {@code is_internal = false}.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  @Query(
      "SELECT r FROM RefineryOrder r WHERE ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND r.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND r.owningOrgUnit.id IN :memberOrgUnitIds)"
          + " )")
  Page<RefineryOrder> findAllScoped(
      @Param("isAdminAllScope") boolean isAdminAllScope,
      @Param("activeOrgUnitId") UUID activeOrgUnitId,
      @Param("memberOrgUnitIds") java.util.Collection<UUID> memberOrgUnitIds,
      Pageable pageable);

  /**
   * Scoped variant of {@link #findByStatusIn(List, Pageable)}: filters by org-unit scope using the
   * standard {@code isAdminAllScope} / {@code activeOrgUnitId} / {@code memberOrgUnitIds} triple.
   * Used by the refinery list page when an admin selected an active squadron via the switcher and
   * by non-admin users to see the union of their org-unit refinery orders.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  @Query(
      "SELECT r FROM RefineryOrder r WHERE r.status IN :statuses AND ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND r.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND r.owningOrgUnit.id IN :memberOrgUnitIds)"
          + " )")
  Page<RefineryOrder> findByStatusInScoped(
      @Param("statuses")
          List<de.greluc.krt.profit.basetool.backend.model.RefineryOrderStatus> statuses,
      @Param("isAdminAllScope") boolean isAdminAllScope,
      @Param("activeOrgUnitId") UUID activeOrgUnitId,
      @Param("memberOrgUnitIds") java.util.Collection<UUID> memberOrgUnitIds,
      Pageable pageable);

  /**
   * Bulk-clears the {@code mission} reference on every refinery order tied to one of the given
   * missions so the orders survive a mission delete as unassigned (see CLAUDE.md "Operation delete
   * keeps missions intact" rule for the broader pattern).
   */
  @Modifying
  @Query("UPDATE RefineryOrder r SET r.mission = null WHERE r.mission.id IN :missionIds")
  void unlinkMissions(@Param("missionIds") List<UUID> missionIds);

  /**
   * Bulk-reassigns every refinery order owned by {@code oldUser} to {@code newUser}; used by the
   * user-deletion cascade so refinery history is preserved when an account is removed.
   *
   * @param oldUser the previous owner
   * @param newUser the new owner (the fallback admin)
   * @return the number of refinery orders reassigned
   */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "UPDATE RefineryOrder r SET r.owner = :newUser WHERE r.owner = :oldUser")
  int updateOwner(
      @org.jetbrains.annotations.NotNull de.greluc.krt.profit.basetool.backend.model.User oldUser,
      @org.jetbrains.annotations.NotNull de.greluc.krt.profit.basetool.backend.model.User newUser);
}

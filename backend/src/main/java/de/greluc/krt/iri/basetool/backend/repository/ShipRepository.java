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

import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Ship;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Ship. */
@Repository
public interface ShipRepository extends JpaRepository<Ship, UUID> {

  /**
   * Flips the {@code fitted} flag back to {@code false} on every ship; used by the fleet-import
   * flow as the first step before re-applying the freshly imported fitted set. {@code
   * clearAutomatically = true} flushes the persistence context so subsequent saves in the same
   * transaction do not collide with stale {@code @Version} state.
   */
  @Modifying(clearAutomatically = true)
  @Query("UPDATE Ship s SET s.fitted = false")
  void resetAllFitted();

  /**
   * OrgUnit-scoped variant of {@link #resetAllFitted()}. Used by the admin/officer "reset fitted"
   * action so a focused-mode caller only wipes the {@code fitted} flag on ships of their own
   * OrgUnit (MULTI_SQUADRON_PLAN.md section 1: Hangar = strict eigene Staffel). Uses the R6.c
   * scope-predicate triple: admin all-scope resets every ship; pinned active OrgUnit resets only
   * that one; non-admin path resets the union of memberships.
   *
   * @param isAdminAllScope {@code true} iff the caller is admin without an active selection
   * @param activeOrgUnitId pinned OrgUnit id, or {@code null}
   * @param memberOrgUnitIds the union of OrgUnits the caller belongs to (non-admin path)
   */
  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE Ship s SET s.fitted = false WHERE ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND s.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND s.owningOrgUnit.id IN :memberOrgUnitIds)"
          + " )")
  void resetAllFittedScoped(
      @org.springframework.data.repository.query.Param("isAdminAllScope") boolean isAdminAllScope,
      @org.springframework.data.repository.query.Param("activeOrgUnitId") UUID activeOrgUnitId,
      @org.springframework.data.repository.query.Param("memberOrgUnitIds")
          java.util.Collection<UUID> memberOrgUnitIds);

  /**
   * Bulk-sets the location of every ship owned by {@code ownerId} to {@code location}; backs the
   * hangar "set home location" action. A single atomic JPQL update — it does NOT touch the
   * {@code @Version} column, so it never triggers an optimistic-lock storm; the caller reloads the
   * page afterwards to resync the displayed location. {@code clearAutomatically = true} flushes the
   * persistence context so any later read in the same transaction sees the new state. The {@code
   * owner.id} predicate enforces per-user isolation — never touches another user's ships.
   *
   * @param ownerId the owning user id whose ships are updated
   * @param location the location to set on every matching ship
   * @return the number of ships updated
   */
  @Modifying(clearAutomatically = true)
  @Query("UPDATE Ship s SET s.location = :location WHERE s.owner.id = :ownerId")
  int setLocationForOwner(
      @org.springframework.data.repository.query.Param("ownerId") UUID ownerId,
      @org.springframework.data.repository.query.Param("location") Location location);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * ShipTypeId}.
   */
  boolean existsByShipTypeId(UUID shipTypeId);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * LocationId}.
   */
  boolean existsByLocationId(UUID locationId);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * OwnerIdAndShipTypeId}.
   */
  boolean existsByOwnerIdAndShipTypeId(UUID ownerId, UUID shipTypeId);

  /**
   * Derived Spring-Data query - returns the count of rows matching {@code OwnerIdAndShipTypeId}.
   */
  long countByOwnerIdAndShipTypeId(UUID ownerId, UUID shipTypeId);

  /**
   * Derived Spring-Data query - returns entities matching {@code OwnerId}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"shipType", "location", "owner", "owningOrgUnit"})
  List<Ship> findByOwnerId(UUID ownerId);

  /**
   * Derived Spring-Data query - returns entities matching {@code OwnerId}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"shipType", "location", "owner", "owningOrgUnit"})
  Page<Ship> findByOwnerId(UUID ownerId, Pageable pageable);

  /**
   * Returns every ship owned by any of the given users, eagerly fetching the relations needed for
   * DTO projection. Unlike {@link #findAllScoped}, this is intentionally NOT OrgUnit-scoped: it
   * surfaces the ships of a mission's participants regardless of which OrgUnit each participant
   * belongs to, so a cross-OrgUnit participant's ship can be assigned to a unit.
   *
   * @param ownerIds the owner user ids to match; an empty collection yields an empty list
   * @return ships owned by those users
   */
  @EntityGraph(attributePaths = {"shipType", "location", "owner", "owningOrgUnit"})
  List<Ship> findByOwnerIdIn(java.util.Collection<UUID> ownerIds);

  /**
   * Lists every entity. Overridden here to attach an {@code @EntityGraph}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @Override
  @EntityGraph(attributePaths = {"shipType", "location", "owner", "owningOrgUnit"})
  Page<Ship> findAll(Pageable pageable);

  /**
   * Multi-tenant variant of {@link #findAll(Pageable)}: returns every ship whose owning squadron
   * matches {@code owningSquadronId}, or every ship when {@code owningSquadronId} is {@code null}
   * (admin "all squadrons" mode). Eagerly fetches {@code shipType}, {@code location} and {@code
   * owner} via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"shipType", "location", "owner", "owningOrgUnit"})
  @Query(
      "SELECT s FROM Ship s WHERE ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND s.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND s.owningOrgUnit.id IN :memberOrgUnitIds)"
          + " )")
  Page<Ship> findAllScoped(
      @org.springframework.data.repository.query.Param("isAdminAllScope") boolean isAdminAllScope,
      @org.springframework.data.repository.query.Param("activeOrgUnitId") UUID activeOrgUnitId,
      @org.springframework.data.repository.query.Param("memberOrgUnitIds")
          java.util.Collection<UUID> memberOrgUnitIds,
      Pageable pageable);

  /**
   * Aggregates ships by type for the squadron-overview page: tuple of {@code (shipType, totalCount,
   * fittedCount)} ordered alphabetically by ship-type name. Returns raw {@code Object[]} - the
   * service projects it into the squadron-overview DTO. When {@code owningSquadronId} is {@code
   * null} the aggregation spans every squadron (admin "all squadrons" mode); otherwise the row set
   * is pre-filtered to that squadron's ships only.
   */
  @Query(
      "SELECT s.shipType, COUNT(s), SUM(CASE WHEN s.fitted = true THEN 1 ELSE 0 END) FROM Ship s"
          + " WHERE ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND s.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND s.owningOrgUnit.id IN :memberOrgUnitIds)"
          + " ) GROUP BY s.shipType ORDER BY s.shipType.name ASC")
  Page<Object[]> countShipsByType(
      @org.springframework.data.repository.query.Param("isAdminAllScope") boolean isAdminAllScope,
      @org.springframework.data.repository.query.Param("activeOrgUnitId") UUID activeOrgUnitId,
      @org.springframework.data.repository.query.Param("memberOrgUnitIds")
          java.util.Collection<UUID> memberOrgUnitIds,
      Pageable pageable);

  /**
   * OrgUnit-scoped owner-detail lookup for the squadron-overview drill-down: returns the ships of
   * the given types that ALSO fall within the caller's scope, using the same R6.c scope-predicate
   * triple as {@link #countShipsByType} so the per-owner detail rows exactly match the aggregated
   * counts shown next to them. Without the scope clause the owner breakdown leaked rows of ships
   * owned by a foreign OrgUnit that merely shared a ship type with the scoped set — e.g. an admin
   * pinned to a squadron seeing an SK-only member's ship in the overview. The {@code shipType IN}
   * filter narrows the result to the current overview page's types (derived from {@link
   * #countShipsByType}); the scope clause then drops any of those ships that belong to a different
   * OrgUnit. Eagerly fetches {@code owner}, {@code location} and {@code owningOrgUnit} via
   * {@code @EntityGraph}.
   *
   * @param shipTypes the ship types to include (the current overview page's already-scoped types);
   *     an empty collection yields an empty list.
   * @param isAdminAllScope {@code true} iff the caller is an admin without an active selection —
   *     returns every ship of the given types.
   * @param activeOrgUnitId the pinned OrgUnit id, or {@code null} when no pin is active.
   * @param memberOrgUnitIds the union of OrgUnits the caller belongs to, consulted only on the
   *     non-admin, no-pin path.
   * @return the scoped ships of those types.
   */
  @EntityGraph(attributePaths = {"owner", "location", "owningOrgUnit"})
  @Query(
      "SELECT s FROM Ship s WHERE s.shipType IN :shipTypes AND ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND s.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND s.owningOrgUnit.id IN :memberOrgUnitIds)"
          + " )")
  List<Ship> findByShipTypeInScoped(
      @org.springframework.data.repository.query.Param("shipTypes")
          List<de.greluc.krt.iri.basetool.backend.model.ShipType> shipTypes,
      @org.springframework.data.repository.query.Param("isAdminAllScope") boolean isAdminAllScope,
      @org.springframework.data.repository.query.Param("activeOrgUnitId") UUID activeOrgUnitId,
      @org.springframework.data.repository.query.Param("memberOrgUnitIds")
          java.util.Collection<UUID> memberOrgUnitIds);

  /**
   * Bulk-reassigns every ship owned by {@code oldUser} to {@code newUser}; used by the user-merge
   * flow so the fleet is preserved when two Keycloak accounts get consolidated.
   */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "UPDATE Ship s SET s.owner = :newUser WHERE s.owner = :oldUser")
  void updateOwner(
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User oldUser,
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User newUser);
}

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

import de.greluc.krt.profit.basetool.backend.model.Mission;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Mission. */
@Repository
public interface MissionRepository extends JpaRepository<Mission, UUID> {

  /**
   * Returns slim {@link de.greluc.krt.profit.basetool.backend.model.dto.MissionReferenceDto}s for
   * the mission-picker dropdowns of the warehouse (Lager) views, sorted newest-first by planned
   * start (missions without a planned start sort last, then alphabetically by name), without
   * pulling the full {@link Mission} aggregate. The result contains every {@code PLANNED} / {@code
   * ACTIVE} mission visible to the caller (the live operational set, regardless of date) plus every
   * recently-closed {@code COMPLETED} / {@code CANCELLED} mission whose {@code plannedStartTime} is
   * on or after {@code cutoff} — so an inventory item can still be filtered by, or re-bound to, a
   * mission that has just wrapped up. Terminal missions older than the cut-off (and terminal
   * missions with no planned start) are dropped to keep the dropdown from ballooning with
   * historical operations.
   *
   * <p>Multi-tenant rule (MULTI_SQUADRON_PLAN.md §1, audit finding H-4 + R6.c §5.4): the lookup
   * uses the standard org-unit scope-predicate triple — admin all-scope sees every matching
   * mission; a specific {@code activeOrgUnitId} narrows to that OrgUnit's missions; the non-admin
   * path passes the union of memberships. Cross-staffel public missions ({@code isInternal=false})
   * remain visible regardless of scope — so a member from one OrgUnit can still see other OrgUnits'
   * public missions in the typeahead. An <em>ownerless</em> mission ({@code owningOrgUnit IS NULL}
   * — a leadership / "Bereichsleitung" mission) is surfaced through the {@code isInternal = false}
   * branch when public, and to organisation members-or-above (the {@code viewerIsMemberOrAbove}
   * flag) when internal.
   *
   * @param isAdminAllScope {@code true} iff the caller is admin without an active OrgUnit selection
   *     — disables the scope filter entirely.
   * @param activeOrgUnitId the single OrgUnit the caller is pinned to, or {@code null}.
   * @param memberOrgUnitIds the union of OrgUnits the caller belongs to (non-admin path); empty for
   *     admins and anonymous callers.
   * @param viewerIsMemberOrAbove {@code true} iff the caller is an organisation member or above
   *     (not an anonymous / guest outsider); lets the caller see <em>internal</em> ownerless
   *     leadership missions, which carry no owning OrgUnit to scope against.
   * @param cutoff inclusive lower bound on {@code plannedStartTime} for {@code COMPLETED} / {@code
   *     CANCELLED} missions; {@code PLANNED} / {@code ACTIVE} missions are returned regardless of
   *     it.
   * @return slim reference DTOs visible to the caller, ordered newest planned-start first.
   */
  @Query(
      "SELECT new de.greluc.krt.profit.basetool.backend.model.dto.MissionReferenceDto(m.id, m.name,"
          + " m.status, m.plannedStartTime) FROM Mission m WHERE ("
          + "  m.status IN ('PLANNED', 'ACTIVE')"
          + "  OR (m.status IN ('COMPLETED', 'CANCELLED') AND m.plannedStartTime >= :cutoff)"
          + " ) AND ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND m.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND m.owningOrgUnit.id IN :memberOrgUnitIds)"
          + "  OR m.isInternal = false"
          + "  OR (m.owningOrgUnit IS NULL AND :viewerIsMemberOrAbove = true)"
          + " ) ORDER BY m.plannedStartTime DESC NULLS LAST, m.name ASC")
  List<de.greluc.krt.profit.basetool.backend.model.dto.MissionReferenceDto> findAllActiveReference(
      @Param("isAdminAllScope") boolean isAdminAllScope,
      @Param("activeOrgUnitId") UUID activeOrgUnitId,
      @Param("memberOrgUnitIds") java.util.Collection<UUID> memberOrgUnitIds,
      @Param("viewerIsMemberOrAbove") boolean viewerIsMemberOrAbove,
      @Param("cutoff") Instant cutoff);

  /**
   * Derived Spring-Data query - returns entities matching {@code Id}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @Override
  @EntityGraph(attributePaths = {"participants", "assignedUnits"})
  Optional<Mission> findById(UUID id);

  /**
   * Returns the next upcoming mission (limit 1) whose {@code plannedStartTime} is after {@code
   * date} and whose {@code status} is one of {@code statuses} — the home-page "next mission" banner
   * passes {@code PLANNED} / {@code ACTIVE} so a {@code COMPLETED} / {@code CANCELLED} mission with
   * a future planned start never surfaces there. Deliberately NOT graphed: combining the {@code
   * limit 1} with a collection {@code @EntityGraph} ({@code participants} / {@code assignedUnits})
   * forces Hibernate to load the whole result set and paginate in memory (HHH90003004). Callers
   * that need the collections re-fetch the single hit by id through the graphed {@link
   * #findById(UUID)} — see {@code MissionService.getNextMission}.
   *
   * @param date exclusive lower bound on {@code plannedStartTime}
   * @param statuses the mission statuses to include (e.g. {@code PLANNED} / {@code ACTIVE})
   * @return the next matching mission, or empty when none upcoming
   */
  Optional<Mission> findFirstByPlannedStartTimeAfterAndStatusInOrderByPlannedStartTimeAsc(
      Instant date, java.util.Collection<String> statuses);

  /**
   * Guest variant of {@link
   * #findFirstByPlannedStartTimeAfterAndStatusInOrderByPlannedStartTimeAsc(Instant,
   * java.util.Collection)} that additionally excludes internal missions ({@code isInternal =
   * false}). Same {@code limit 1} + {@code status IN} contract and the same no-{@code @EntityGraph}
   * rationale — a {@code limit 1} plus a collection {@code @EntityGraph} triggers in-memory
   * pagination (HHH90003004); callers re-fetch the hit by id through the graphed {@link
   * #findById(UUID)}.
   *
   * @param date exclusive lower bound on {@code plannedStartTime}
   * @param statuses the mission statuses to include (e.g. {@code PLANNED} / {@code ACTIVE})
   * @return the next matching public mission, or empty when none upcoming
   */
  Optional<Mission>
      findFirstByPlannedStartTimeAfterAndIsInternalFalseAndStatusInOrderByPlannedStartTimeAsc(
          Instant date, java.util.Collection<String> statuses);

  /**
   * Org-unit-scoped next-mission lookup (REQ-MISSION-008). Returns the upcoming missions owned by
   * the caller's org units, soonest planned start first, so the home-page "next mission" banner can
   * surface only the next mission that belongs to the viewer's own unit(s) — or, for a Bereich/OL
   * leader, their subordinate units — instead of the organisation-wide next one. Pass {@code
   * PageRequest.of(0, 1)} to take only the head; the result is the soonest match.
   *
   * <p>Scope is the same org-unit-predicate shape used by {@link #searchMissions} minus the
   * cross-staffel public escape: a pinned {@code activeOrgUnitId} narrows to that single OrgUnit;
   * otherwise the mission's {@code owningOrgUnit} must be one of {@code memberOrgUnitIds} (the
   * caller's membership union already expanded with the epic #692 / REQ-ORG-015 leadership cascade
   * by {@link
   * de.greluc.krt.profit.basetool.backend.service.OwnerScopeService#currentScopePredicate()}).
   * Foreign missions — including other OrgUnits' public ones — are deliberately excluded, because
   * the banner answers "what is <em>my</em> unit heading towards". The admin-all and the
   * no-org-unit (anonymous / membershipless) cases never reach this query; the service routes them
   * to the unscoped {@link
   * #findFirstByPlannedStartTimeAfterAndStatusInOrderByPlannedStartTimeAsc(Instant,
   * java.util.Collection)} pair instead.
   *
   * <p>{@code allowInternal} gates internal missions exactly like the unscoped guest variant: a
   * member (the normal scoped caller) passes {@code true} and sees both internal and public
   * missions of their own units; the flag keeps the query defensive should a non-member ever carry
   * an org-unit scope. Deliberately NOT graphed — combining the {@code limit 1} with a collection
   * {@code @EntityGraph} forces in-memory pagination (HHH90003004); the caller re-fetches the
   * single hit by id through the graphed {@link #findById(UUID)}.
   *
   * @param now exclusive lower bound on {@code plannedStartTime}
   * @param statuses the mission statuses to include (e.g. {@code PLANNED} / {@code ACTIVE})
   * @param allowInternal {@code true} to include internal missions, {@code false} for public only
   * @param activeOrgUnitId the single pinned OrgUnit id, or {@code null} to use {@code
   *     memberOrgUnitIds}
   * @param memberOrgUnitIds the caller's effective (cascade-expanded) org-unit reach; consulted
   *     only when {@code activeOrgUnitId} is {@code null}
   * @param pageable limits the result to the head ({@code PageRequest.of(0, 1)})
   * @return the matching missions in soonest-first order (at most {@code pageable} size)
   */
  @Query(
      "SELECT m FROM Mission m WHERE m.plannedStartTime > :now AND m.status IN :statuses AND"
          + " (:allowInternal = true OR m.isInternal = false) AND ((:activeOrgUnitId IS NOT NULL"
          + " AND m.owningOrgUnit.id = :activeOrgUnitId) OR (:activeOrgUnitId IS NULL AND"
          + " m.owningOrgUnit.id IN :memberOrgUnitIds)) ORDER BY m.plannedStartTime ASC")
  List<Mission> findNextScopedMission(
      @Param("now") Instant now,
      @Param("statuses") java.util.Collection<String> statuses,
      @Param("allowInternal") boolean allowInternal,
      @Param("activeOrgUnitId") UUID activeOrgUnitId,
      @Param("memberOrgUnitIds") java.util.Collection<UUID> memberOrgUnitIds,
      Pageable pageable);

  /**
   * Full-text + date-range + status + scope search across missions. Each parameter is optional - a
   * {@code null} cast removes the corresponding clause; the {@code status IN (:status)} list is
   * always applied (pass the full enum set to disable status filtering). Result is sorted by
   * planned start ascending; the {@code @EntityGraph} pre-loads participants and assigned units to
   * avoid N+1 when the caller renders the result list.
   *
   * <p>Multi-tenant access control via the org-unit scope-predicate triple ({@code isAdminAllScope}
   * / {@code activeOrgUnitId} / {@code memberOrgUnitIds}): admin all-scope sees everything, a
   * pinned {@code activeOrgUnitId} narrows to that OrgUnit, and the non-admin path passes the
   * membership union. Non-internal missions of any OrgUnit stay visible cross-staffel (the public
   * escape). Ownerless leadership missions ({@code owningOrgUnit IS NULL}) follow the same
   * public/internal split — public to all; internal only to organisation members-or-above (the
   * {@code viewerIsMemberOrAbove} flag). See MULTI_SQUADRON_PLAN.md section 1.
   */
  @EntityGraph(attributePaths = {"participants", "assignedUnits"})
  @Query(
      "SELECT m FROM Mission m WHERE ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND m.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND m.owningOrgUnit.id IN :memberOrgUnitIds)"
          + "  OR m.isInternal = false"
          + "  OR (m.owningOrgUnit IS NULL AND :viewerIsMemberOrAbove = true)"
          + " ) AND (CAST(:query AS string) IS NULL OR m.name ILIKE CONCAT('%', CAST(:query AS"
          + " string), '%') OR CAST(m.description AS string) ILIKE CONCAT('%', CAST(:query AS"
          + " string), '%')) AND (CAST(:start AS timestamp) IS NULL OR m.plannedStartTime >="
          + " :start) AND (CAST(:end AS timestamp) IS NULL OR m.plannedStartTime <= :end) AND"
          + " (m.status IN (:status)) AND (:isInternal IS NULL OR m.isInternal = :isInternal) AND"
          + " (CAST(:operationId AS uuid) IS NULL OR m.operation.id = :operationId) ORDER BY"
          + " m.plannedStartTime ASC")
  List<Mission> searchMissions(
      @Param("query") String query,
      @Param("start") Instant start,
      @Param("end") Instant end,
      @Param("status") List<String> status,
      @Param("isInternal") Boolean isInternal,
      @Param("operationId") UUID operationId,
      @Param("isAdminAllScope") boolean isAdminAllScope,
      @Param("activeOrgUnitId") UUID activeOrgUnitId,
      @Param("memberOrgUnitIds") java.util.Collection<UUID> memberOrgUnitIds,
      @Param("viewerIsMemberOrAbove") boolean viewerIsMemberOrAbove);

  /**
   * Paged variant of {@link #searchMissions(String, Instant, Instant, List, Boolean, UUID, UUID)} -
   * same filter contract; sorting is delegated to {@link Pageable} so the caller can pick the
   * column.
   *
   * <p><strong>EntityGraph contract differs from the non-paged variant on purpose.</strong> The
   * controller maps the page result through {@code MissionMapper.toListDto}, which reads only
   * scalar columns plus the two {@code @ManyToOne} associations {@code operation} and {@code
   * owningSquadron} — it never touches {@code participants} or {@code assignedUnits}. Eager-loading
   * those collections via {@code @EntityGraph} forces Hibernate into in-memory pagination ({@code
   * HHH000104: firstResult/maxResults specified with collection fetch; applying in memory}) because
   * SQL-level {@code OFFSET}/{@code LIMIT} cannot be combined with a collection join. The resulting
   * cartesian {@code mission x participant x unit} fetch + JVM-side slicing was the dominant cost
   * of the missions list page after the multi-squadron rollout. Eager-loading only the two
   * {@code @ManyToOne} associations here keeps Hibernate on SQL pagination and resolves the per-row
   * mapping in a single query.
   */
  @EntityGraph(attributePaths = {"operation", "owningOrgUnit"})
  @Query(
      "SELECT m FROM Mission m WHERE ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND m.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND m.owningOrgUnit.id IN :memberOrgUnitIds)"
          + "  OR m.isInternal = false"
          + "  OR (m.owningOrgUnit IS NULL AND :viewerIsMemberOrAbove = true)"
          + " ) AND (CAST(:query AS string) IS NULL OR m.name ILIKE CONCAT('%', CAST(:query AS"
          + " string), '%') OR CAST(m.description AS string) ILIKE CONCAT('%', CAST(:query AS"
          + " string), '%')) AND (CAST(:start AS timestamp) IS NULL OR m.plannedStartTime >="
          + " :start) AND (CAST(:end AS timestamp) IS NULL OR m.plannedStartTime <= :end) AND"
          + " (m.status IN (:status)) AND (:isInternal IS NULL OR m.isInternal = :isInternal) AND"
          + " (CAST(:operationId AS uuid) IS NULL OR m.operation.id = :operationId)")
  Page<Mission> searchMissions(
      @Param("query") String query,
      @Param("start") Instant start,
      @Param("end") Instant end,
      @Param("status") List<String> status,
      @Param("isInternal") Boolean isInternal,
      @Param("operationId") UUID operationId,
      @Param("isAdminAllScope") boolean isAdminAllScope,
      @Param("activeOrgUnitId") UUID activeOrgUnitId,
      @Param("memberOrgUnitIds") java.util.Collection<UUID> memberOrgUnitIds,
      @Param("viewerIsMemberOrAbove") boolean viewerIsMemberOrAbove,
      Pageable pageable);

  /**
   * Lists every mission as a page. Overridden here to attach an {@code @EntityGraph} so the
   * mission-list render does not N+1 on {@code participants} and {@code assignedUnits}. The
   * unbounded {@code List<Mission> findAll()} sibling was deleted (M-9 from the performance audit)
   * because the audit found zero production callers and an ArchUnit guard now blocks anyone from
   * re-introducing a no-arg list-returning override on any repository in this package — every
   * mission read path must go through paged search, the scoped search query, or a {@code findById}
   * lookup.
   */
  @Override
  @EntityGraph(attributePaths = {"participants", "assignedUnits"})
  Page<Mission> findAll(Pageable pageable);

  /**
   * Bulk-reassigns every mission owned by {@code oldUser} to {@code newUser}; used by the
   * user-merge flow so missions are preserved when two Keycloak accounts get consolidated.
   */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "UPDATE Mission m SET m.owner = :newUser WHERE m.owner = :oldUser")
  void updateOwner(
      @org.jetbrains.annotations.NotNull de.greluc.krt.profit.basetool.backend.model.User oldUser,
      @org.jetbrains.annotations.NotNull de.greluc.krt.profit.basetool.backend.model.User newUser);

  /**
   * Removes the given user from every mission's manager set via direct delete on the join table.
   * Native query because Hibernate cannot bulk-delete a {@code @ManyToMany} association directly -
   * JPQL would require loading every mission first.
   */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      value = "DELETE FROM mission_managers WHERE user_id = :userId",
      nativeQuery = true)
  void removeManager(
      @org.springframework.data.repository.query.Param("userId") java.util.UUID userId);

  /**
   * Returns {@code true} if the given operation has at least one mission whose actual time window
   * is incomplete — either {@code actualStartTime} or {@code actualEndTime} is {@code null}. Drives
   * the "payout figures are preliminary" warning on the operation-detail page: as long as any
   * mission has not been started or has not been finalized, the operation's payout breakdown cannot
   * include the unfinished mission's checked-in time (see {@code
   * OperationService#computeParticipationBreakdown} which skips such missions), so the percentages
   * may rebalance once every mission is closed.
   *
   * <p>Implemented as a single {@code COUNT > 0} / {@code EXISTS}-equivalent JPQL query so the
   * detail-endpoint adds at most one cheap round-trip on top of the existing {@code findById} hit.
   *
   * @param operationId the operation to inspect
   * @return {@code true} if at least one mission of the operation lacks {@code actualStartTime} or
   *     {@code actualEndTime}, {@code false} otherwise (including the empty-operation case)
   */
  @Query(
      "SELECT CASE WHEN COUNT(m) > 0 THEN TRUE ELSE FALSE END FROM Mission m WHERE m.operation.id ="
          + " :operationId AND (m.actualStartTime IS NULL OR m.actualEndTime IS NULL)")
  boolean existsByOperationIdWithUnfinishedActualTime(@Param("operationId") UUID operationId);
}

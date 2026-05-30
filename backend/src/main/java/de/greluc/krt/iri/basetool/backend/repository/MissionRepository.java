package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Mission;
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
   * Returns slim {@link de.greluc.krt.iri.basetool.backend.model.dto.MissionReferenceDto}s for
   * every {@code PLANNED} / {@code ACTIVE} mission visible to the caller, sorted by planned start.
   * Drives mission-picker dropdowns without pulling the full {@link Mission} aggregate.
   *
   * <p>Multi-tenant rule (MULTI_SQUADRON_PLAN.md §1, audit finding H-4 + R6.c §5.4): the lookup
   * uses the standard org-unit scope-predicate triple — admin all-scope sees every active mission;
   * a specific {@code activeOrgUnitId} narrows to that OrgUnit's missions; the non-admin path
   * passes the union of memberships. Cross-staffel public missions ({@code isInternal=false})
   * remain visible regardless of scope — so a member from one OrgUnit can still see other OrgUnits'
   * public missions in the typeahead.
   *
   * @param isAdminAllScope {@code true} iff the caller is admin without an active OrgUnit selection
   *     — disables the scope filter entirely.
   * @param activeOrgUnitId the single OrgUnit the caller is pinned to, or {@code null}.
   * @param memberOrgUnitIds the union of OrgUnits the caller belongs to (non-admin path); empty for
   *     admins and anonymous callers.
   * @return slim reference DTOs visible to the caller.
   */
  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.dto.MissionReferenceDto(m.id, m.name,"
          + " m.status, m.plannedStartTime) FROM Mission m WHERE m.status IN ('PLANNED', 'ACTIVE')"
          + " AND ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND m.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND m.owningOrgUnit.id IN :memberOrgUnitIds)"
          + "  OR m.isInternal = false"
          + " ) ORDER BY m.plannedStartTime ASC")
  List<de.greluc.krt.iri.basetool.backend.model.dto.MissionReferenceDto> findAllActiveReference(
      @Param("isAdminAllScope") boolean isAdminAllScope,
      @Param("activeOrgUnitId") UUID activeOrgUnitId,
      @Param("memberOrgUnitIds") java.util.Collection<UUID> memberOrgUnitIds);

  /**
   * Derived Spring-Data query - returns entities matching {@code Id}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @Override
  @EntityGraph(attributePaths = {"participants", "assignedUnits"})
  Optional<Mission> findById(UUID id);

  /**
   * Returns the first matching {@code PlannedStartTimeAfterOrderByPlannedStartTimeAsc} (limit 1).
   * Deliberately NOT graphed: combining the {@code limit 1} with a collection {@code @EntityGraph}
   * ({@code participants} / {@code assignedUnits}) forces Hibernate to load the whole result set
   * and paginate in memory (HHH90003004). Callers that need the collections re-fetch the single hit
   * by id through the graphed {@link #findById(UUID)} — see {@code MissionService.getNextMission}.
   */
  Optional<Mission> findFirstByPlannedStartTimeAfterOrderByPlannedStartTimeAsc(Instant date);

  /**
   * Returns the first matching {@code
   * PlannedStartTimeAfterAndIsInternalFalseOrderByPlannedStartTimeAsc} (limit 1). Deliberately NOT
   * graphed for the same reason as {@link
   * #findFirstByPlannedStartTimeAfterOrderByPlannedStartTimeAsc(Instant)} — a {@code limit 1} plus
   * a collection {@code @EntityGraph} triggers in-memory pagination (HHH90003004); callers re-fetch
   * the hit by id through the graphed {@link #findById(UUID)}.
   */
  Optional<Mission> findFirstByPlannedStartTimeAfterAndIsInternalFalseOrderByPlannedStartTimeAsc(
      Instant date);

  /**
   * Full-text + date-range + status + scope search across missions. Each parameter is optional - a
   * {@code null} cast removes the corresponding clause; the {@code status IN (:status)} list is
   * always applied (pass the full enum set to disable status filtering). Result is sorted by
   * planned start ascending; the {@code @EntityGraph} pre-loads participants and assigned units to
   * avoid N+1 when the caller renders the result list.
   *
   * <p>Multi-tenant access control: {@code scopeSquadronId} gates which missions the caller may
   * see. When {@code null}, no scope restriction applies (admin "all squadrons" mode). When
   * non-null, the result is restricted to missions owned by that squadron PLUS any non-internal
   * mission of any other squadron (MULTI_SQUADRON_PLAN.md section 1: non-internal missions are
   * visible cross-staffel).
   */
  @EntityGraph(attributePaths = {"participants", "assignedUnits"})
  @Query(
      "SELECT m FROM Mission m WHERE ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND m.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND m.owningOrgUnit.id IN :memberOrgUnitIds)"
          + "  OR m.isInternal = false"
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
      @Param("memberOrgUnitIds") java.util.Collection<UUID> memberOrgUnitIds);

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
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User oldUser,
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User newUser);

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

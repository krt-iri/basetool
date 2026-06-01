package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Job Order. */
@Repository
public interface JobOrderRepository extends JpaRepository<JobOrder, UUID> {

  /**
   * Derived Spring-Data query - returns entities matching {@code Id}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(
      attributePaths = {
        "materials",
        "materials.material",
        "handovers",
        "handovers.items",
        "handovers.items.material",
        "assignees",
        "responsibleOrgUnit",
        "requestingOrgUnit"
      })
  @Override
  Optional<JobOrder> findById(UUID id);

  /**
   * Loads every job-order in {@code OPEN} or {@code IN_PROGRESS} status together with its materials
   * and handover items in one query, ordered newest first by {@code displayId}. Eager-fetch path
   * matches what the active-orders board renders, so there is no N+1.
   */
  @EntityGraph(
      attributePaths = {
        "materials",
        "materials.material",
        "handovers",
        "handovers.items",
        "handovers.items.material",
        "responsibleOrgUnit",
        "requestingOrgUnit"
      })
  @Query(
      "SELECT o FROM JobOrder o WHERE o.status IN ('OPEN', 'IN_PROGRESS') ORDER BY o.displayId"
          + " DESC")
  List<JobOrder> findAllActiveWithMaterials();

  /**
   * Scoped, paged job-order list — the single entry point behind the {@code GET /api/v1/orders}
   * list endpoint. Combines three concerns in one query so the service layer never has to fork its
   * query builder:
   *
   * <ol>
   *   <li><b>Visibility scope (Phase 3, #343).</b> Job Orders are a <em>conditionally</em>
   *       staffel-scoped aggregate: an order whose {@code responsibleOrgUnit} is a Spezialkommando
   *       is public to every squadron, while a squadron-responsible order is private to that
   *       squadron + admins. The requester does NOT grant visibility. The scope is expressed with
   *       the standard org-unit predicate triple ({@code isAdminAllScope} / {@code activeOrgUnitId}
   *       / {@code memberOrgUnitIds}, see {@link
   *       de.greluc.krt.iri.basetool.backend.service.ScopePredicate}) plus the SK-public escape
   *       {@code TYPE(o.responsibleOrgUnit) = SpecialCommand}.
   *   <li><b>Status filter.</b> The order's status must be in {@code statuses}. The service passes
   *       the full enum set to disable status filtering (mirroring {@code searchMissions}), so the
   *       {@code IN} clause is never bound with an empty collection.
   *   <li><b>Optional {@code squadronId} display filter.</b> A pure UI preference on top of the
   *       scope gate — the orders-index "involving my squadron" toggle, matching responsible OR
   *       requesting side. {@code null} disables it. It can only ever narrow the already-scoped
   *       result, never widen it past the security scope above.
   * </ol>
   *
   * @param statuses status values to keep; pass the full enum set to disable status filtering
   *     (never empty).
   * @param squadronId optional display filter (responsible OR requesting side); {@code null}
   *     disables it.
   * @param isAdminAllScope {@code true} iff the caller is an admin without an active selection —
   *     disables the scope filter entirely.
   * @param activeOrgUnitId the single OrgUnit the caller is pinned to, or {@code null}.
   * @param memberOrgUnitIds the union of OrgUnits the caller belongs to (non-admin path); empty for
   *     admins and anonymous callers.
   * @param pageable page request.
   * @return paged job-orders visible to the caller, matching the optional status + squadron
   *     filters.
   */
  @EntityGraph(
      attributePaths = {
        "materials",
        "assignees",
        "handovers",
        "handovers.items",
        "responsibleOrgUnit",
        "requestingOrgUnit"
      })
  @Query(
      "SELECT o FROM JobOrder o WHERE ("
          + "  :isAdminAllScope = true"
          + "  OR TYPE(o.responsibleOrgUnit) = SpecialCommand"
          + "  OR (:activeOrgUnitId IS NOT NULL AND o.responsibleOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND o.responsibleOrgUnit.id IN :memberOrgUnitIds)"
          + " ) AND o.status IN :statuses AND (:squadronId IS NULL OR o.responsibleOrgUnit.id ="
          + " :squadronId OR o.requestingOrgUnit.id = :squadronId)")
  Page<JobOrder> findScopedJobOrders(
      @Param("statuses") List<JobOrderStatus> statuses,
      @Param("squadronId") UUID squadronId,
      @Param("isAdminAllScope") boolean isAdminAllScope,
      @Param("activeOrgUnitId") UUID activeOrgUnitId,
      @Param("memberOrgUnitIds") java.util.Collection<UUID> memberOrgUnitIds,
      Pageable pageable);

  /**
   * Returns the current maximum priority across all job-orders (used to assign the next priority
   * slot when creating a new order); {@link Optional#empty} when the table is empty.
   */
  @Query("SELECT MAX(o.priority) FROM JobOrder o")
  Optional<Integer> findMaxPriority();

  /**
   * Acquires a {@link LockModeType#PESSIMISTIC_WRITE} on every job-order ordered by id. Used by the
   * bulk priority-reorder flow to serialise concurrent re-shuffles and avoid the optimistic-
   * locking conflicts that would otherwise fall out of the {@code @Version} bumps - see the
   * "Pessimistic locking for bulk reorders" note in CLAUDE.md.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT o FROM JobOrder o ORDER BY o.id")
  List<JobOrder> lockAllJobOrders();

  /** Derived Spring-Data query - returns entities matching {@code AssigneeId}. */
  @Query("SELECT j FROM JobOrder j JOIN j.assignees u WHERE u.id = :userId")
  List<JobOrder> findByAssigneeId(@Param("userId") UUID userId);

  /**
   * Removes the given user from every job-order's assignee set via a direct delete on the join
   * table. Native query because a JPQL bulk-delete on a {@code @ManyToMany} association would
   * require loading every job-order first.
   */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      value = "DELETE FROM job_order_assignees WHERE user_id = :userId",
      nativeQuery = true)
  void removeAssignee(
      @org.springframework.data.repository.query.Param("userId") java.util.UUID userId);
}

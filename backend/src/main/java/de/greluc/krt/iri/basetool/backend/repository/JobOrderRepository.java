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
   * Derived Spring-Data query - returns entities matching {@code StatusIn}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
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
  Page<JobOrder> findByStatusIn(List<JobOrderStatus> statuses, Pageable pageable);

  /**
   * List variant that additionally filters on the dual-org-unit model (MULTI_SQUADRON_PLAN.md
   * section 5.3): only orders whose {@code responsibleOrgUnit} OR {@code requestingOrgUnit} equals
   * {@code squadronId} are returned. Used by the UI's default "only my squadron" toggle on the job-
   * order list view — Job Orders themselves are a cross-staffel workspace, so this filter is a
   * display preference rather than an access-control gate (the service layer applies no implicit
   * squadron filter on Job Orders, see section 4.4).
   *
   * <p>{@code squadronId} {@code null} short-circuits the squadron predicate and behaves
   * identically to {@link #findByStatusIn(List, Pageable)} — kept as a separate method so the SpEL
   * gate on the list endpoint stays simple and call-sites with both filters do not need to fork
   * their query builder.
   *
   * @param statuses status filter; may be empty but must be non-null per the Spring Data contract.
   * @param squadronId squadron to constrain to via creating- OR requesting-side match; {@code null}
   *     disables the squadron filter.
   * @param pageable page request.
   * @return paged job-orders matching both predicates.
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
      "SELECT o FROM JobOrder o WHERE o.status IN :statuses AND (:squadronId IS NULL OR"
          + " o.responsibleOrgUnit.id = :squadronId OR o.requestingOrgUnit.id = :squadronId)")
  Page<JobOrder> findByStatusInAndSquadronInvolved(
      @Param("statuses") List<JobOrderStatus> statuses,
      @Param("squadronId") UUID squadronId,
      Pageable pageable);

  /**
   * All-status variant of {@link #findByStatusInAndSquadronInvolved(List, UUID, Pageable)}. Returns
   * every job-order whose {@code responsibleOrgUnit} OR {@code requestingOrgUnit} equals {@code
   * squadronId}; {@code null} squadronId returns everything (identical to {@code findAll(pageable)}
   * but emits the squadron-aware JPQL anyway, so the service layer's branch stays predictable).
   *
   * @param squadronId squadron to constrain to; {@code null} disables the filter.
   * @param pageable page request.
   * @return paged job-orders.
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
      "SELECT o FROM JobOrder o WHERE :squadronId IS NULL OR o.responsibleOrgUnit.id = :squadronId"
          + " OR o.requestingOrgUnit.id = :squadronId")
  Page<JobOrder> findBySquadronInvolved(@Param("squadronId") UUID squadronId, Pageable pageable);

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

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
        "assignees"
      })
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
        "handovers.items.material"
      })
  @Query(
      "SELECT o FROM JobOrder o WHERE o.status IN ('OPEN', 'IN_PROGRESS') ORDER BY o.displayId"
          + " DESC")
  List<JobOrder> findAllActiveWithMaterials();

  /**
   * Derived Spring-Data query - returns entities matching {@code StatusIn}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"materials", "assignees", "handovers", "handovers.items"})
  Page<JobOrder> findByStatusIn(List<JobOrderStatus> statuses, Pageable pageable);

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

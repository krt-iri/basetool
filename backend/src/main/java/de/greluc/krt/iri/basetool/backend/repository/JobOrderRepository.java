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
   * Custom JPQL/native query; see the {@code @Query} annotation for the projection and filter
   * clauses.
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
      "SELECT o FROM JobOrder o WHERE o.status IN ('OPEN', 'IN_PROGRESS') ORDER BY o.displayId DESC")
  List<JobOrder> findAllActiveWithMaterials();

  /**
   * Derived Spring-Data query - returns entities matching {@code StatusIn}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"materials", "assignees", "handovers", "handovers.items"})
  Page<JobOrder> findByStatusIn(List<JobOrderStatus> statuses, Pageable pageable);

  /**
   * Custom JPQL/native query; see the {@code @Query} annotation for the projection and filter
   * clauses.
   */
  @Query("SELECT MAX(o.priority) FROM JobOrder o")
  Optional<Integer> findMaxPriority();

  /**
   * Custom JPQL/native query; see the {@code @Query} annotation for the projection and filter
   * clauses.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT o FROM JobOrder o ORDER BY o.id")
  List<JobOrder> lockAllJobOrders();

  /** Derived Spring-Data query - returns entities matching {@code AssigneeId}. */
  @Query("SELECT j FROM JobOrder j JOIN j.assignees u WHERE u.id = :userId")
  List<JobOrder> findByAssigneeId(@Param("userId") UUID userId);

  /**
   * Custom JPQL/native bulk update; see the {@code @Query} annotation for the WHERE clause and the
   * {@code @Param} contract.
   */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      value = "DELETE FROM job_order_assignees WHERE user_id = :userId",
      nativeQuery = true)
  void removeAssignee(
      @org.springframework.data.repository.query.Param("userId") java.util.UUID userId);
}

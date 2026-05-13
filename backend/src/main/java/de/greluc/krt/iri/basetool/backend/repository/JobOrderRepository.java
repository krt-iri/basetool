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

@Repository
public interface JobOrderRepository extends JpaRepository<JobOrder, UUID> {

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

  @EntityGraph(attributePaths = {"materials", "assignees", "handovers", "handovers.items"})
  Page<JobOrder> findByStatusIn(List<JobOrderStatus> statuses, Pageable pageable);

  @Query("SELECT MAX(o.priority) FROM JobOrder o")
  Optional<Integer> findMaxPriority();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT o FROM JobOrder o ORDER BY o.id")
  List<JobOrder> lockAllJobOrders();

  @Query("SELECT j FROM JobOrder j JOIN j.assignees u WHERE u.id = :userId")
  List<JobOrder> findByAssigneeId(@Param("userId") UUID userId);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      value = "DELETE FROM job_order_assignees WHERE user_id = :userId",
      nativeQuery = true)
  void removeAssignee(
      @org.springframework.data.repository.query.Param("userId") java.util.UUID userId);
}

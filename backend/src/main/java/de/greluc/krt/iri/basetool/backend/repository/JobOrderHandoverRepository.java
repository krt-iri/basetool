package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.JobOrderHandover;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Job Order Handover. */
@Repository
public interface JobOrderHandoverRepository extends JpaRepository<JobOrderHandover, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code JobOrderId}. */
  List<JobOrderHandover> findByJobOrderId(UUID jobOrderId);
}

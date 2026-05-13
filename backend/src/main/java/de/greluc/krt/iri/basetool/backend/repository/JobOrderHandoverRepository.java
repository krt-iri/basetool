package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.JobOrderHandover;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobOrderHandoverRepository extends JpaRepository<JobOrderHandover, UUID> {
  List<JobOrderHandover> findByJobOrderId(UUID jobOrderId);
}

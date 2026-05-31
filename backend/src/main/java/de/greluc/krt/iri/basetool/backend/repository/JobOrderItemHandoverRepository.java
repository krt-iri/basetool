package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.JobOrderItemHandover;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link JobOrderItemHandover}. Item handovers are created during
 * fulfilment and read back for the order detail view and the PDF delivery note; no custom queries
 * are needed beyond the inherited CRUD.
 */
@Repository
public interface JobOrderItemHandoverRepository extends JpaRepository<JobOrderItemHandover, UUID> {}

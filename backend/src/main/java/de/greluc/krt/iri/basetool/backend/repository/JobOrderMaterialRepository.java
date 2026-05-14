package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Job Order Material. */
@Repository
public interface JobOrderMaterialRepository extends JpaRepository<JobOrderMaterial, UUID> {}

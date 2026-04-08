package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.JobType;
import de.greluc.krt.iri.basetool.backend.model.JobTypeArchetype;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobTypeRepository extends JpaRepository<JobType, UUID> {
    List<JobType> findByArchetype(JobTypeArchetype archetype);
    Page<JobType> findByArchetype(JobTypeArchetype archetype, Pageable pageable);

    List<JobType> findByArchetypeAndActiveTrue(JobTypeArchetype archetype);
    Page<JobType> findByArchetypeAndActiveTrue(JobTypeArchetype archetype, Pageable pageable);

    List<JobType> findByActiveTrue();
    Page<JobType> findByActiveTrue(Pageable pageable);

    boolean existsByParentId(UUID parentId);
    List<JobType> findByParentId(UUID parentId);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}

package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.JobType;
import de.greluc.krt.iri.basetool.backend.model.JobTypeArchetype;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Job Type. */
@Repository
public interface JobTypeRepository extends JpaRepository<JobType, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code Archetype}. */
  List<JobType> findByArchetype(JobTypeArchetype archetype);

  /** Derived Spring-Data query - returns entities matching {@code Archetype}. */
  Page<JobType> findByArchetype(JobTypeArchetype archetype, Pageable pageable);

  /** Derived Spring-Data query - returns entities matching {@code ArchetypeAndActiveTrue}. */
  List<JobType> findByArchetypeAndActiveTrue(JobTypeArchetype archetype);

  /** Derived Spring-Data query - returns entities matching {@code ArchetypeAndActiveTrue}. */
  Page<JobType> findByArchetypeAndActiveTrue(JobTypeArchetype archetype, Pageable pageable);

  /** Derived Spring-Data query - returns entities matching {@code ActiveTrue}. */
  List<JobType> findByActiveTrue();

  /** Derived Spring-Data query - returns entities matching {@code ActiveTrue}. */
  Page<JobType> findByActiveTrue(Pageable pageable);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code ParentId}.
   */
  boolean existsByParentId(UUID parentId);

  /** Derived Spring-Data query - returns entities matching {@code ParentId}. */
  List<JobType> findByParentId(UUID parentId);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * NameIgnoreCase}.
   */
  boolean existsByNameIgnoreCase(String name);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * NameIgnoreCaseAndIdNot}.
   */
  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}

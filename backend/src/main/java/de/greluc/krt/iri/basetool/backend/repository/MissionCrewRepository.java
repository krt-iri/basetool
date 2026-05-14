package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MissionCrew;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for Mission Crew. */
public interface MissionCrewRepository extends JpaRepository<MissionCrew, UUID> {

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * JobTypesId}.
   */
  boolean existsByJobTypesId(UUID jobTypeId);

  /** Derived Spring-Data query - returns entities matching {@code JobTypesId}. */
  List<MissionCrew> findByJobTypesId(UUID jobTypeId);
}

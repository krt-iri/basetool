package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MissionOwnership;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for Mission Ownership. */
public interface MissionOwnershipRepository extends JpaRepository<MissionOwnership, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code MissionId}. */
  Optional<MissionOwnership> findByMissionId(UUID missionId);
}

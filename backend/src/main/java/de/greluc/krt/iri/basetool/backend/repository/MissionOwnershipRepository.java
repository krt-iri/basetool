package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MissionOwnership;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionOwnershipRepository extends JpaRepository<MissionOwnership, UUID> {
  Optional<MissionOwnership> findByMissionId(UUID missionId);
}

package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MissionFrequency;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionFrequencyRepository extends JpaRepository<MissionFrequency, UUID> {
  List<MissionFrequency> findByMissionId(UUID missionId);
}

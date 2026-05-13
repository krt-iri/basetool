package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MissionFrequency;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for Mission Frequency. */
public interface MissionFrequencyRepository extends JpaRepository<MissionFrequency, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code MissionId}. */
  List<MissionFrequency> findByMissionId(UUID missionId);
}

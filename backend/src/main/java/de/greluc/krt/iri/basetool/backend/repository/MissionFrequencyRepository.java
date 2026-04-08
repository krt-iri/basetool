package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MissionFrequency;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface MissionFrequencyRepository extends JpaRepository<MissionFrequency, UUID> {
    List<MissionFrequency> findByMissionId(UUID missionId);
}
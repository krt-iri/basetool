package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MissionCrew;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionCrewRepository extends JpaRepository<MissionCrew, UUID> {

  boolean existsByJobTypesId(UUID jobTypeId);

  List<MissionCrew> findByJobTypesId(UUID jobTypeId);
}

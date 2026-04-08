package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MissionCrew;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MissionCrewRepository extends JpaRepository<MissionCrew, UUID> {

    boolean existsByJobTypesId(UUID jobTypeId);
    List<MissionCrew> findByJobTypesId(UUID jobTypeId);
}

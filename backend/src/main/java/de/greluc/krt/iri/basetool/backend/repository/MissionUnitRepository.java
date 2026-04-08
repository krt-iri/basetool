package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MissionUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MissionUnitRepository extends JpaRepository<MissionUnit, UUID> {
    List<MissionUnit> findByShipId(UUID shipId);
}

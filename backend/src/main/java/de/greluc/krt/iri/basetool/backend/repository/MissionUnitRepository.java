package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MissionUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionUnitRepository extends JpaRepository<MissionUnit, UUID> {
  List<MissionUnit> findByShipId(UUID shipId);
}

package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MissionUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for Mission Unit. */
public interface MissionUnitRepository extends JpaRepository<MissionUnit, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code ShipId}. */
  List<MissionUnit> findByShipId(UUID shipId);
}

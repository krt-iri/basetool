package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.SpaceStation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceStationRepository extends JpaRepository<SpaceStation, UUID> {
  Optional<SpaceStation> findByIdSpaceStation(Integer id);

  Optional<SpaceStation> findByName(String name);
}

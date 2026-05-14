package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.SpaceStation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for Space Station. */
public interface SpaceStationRepository extends JpaRepository<SpaceStation, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code IdSpaceStation}. */
  Optional<SpaceStation> findByIdSpaceStation(Integer id);

  /** Derived Spring-Data query - returns entities matching {@code Name}. */
  Optional<SpaceStation> findByName(String name);
}

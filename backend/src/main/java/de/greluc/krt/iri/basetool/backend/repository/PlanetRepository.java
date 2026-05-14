package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Planet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for Planet. */
public interface PlanetRepository extends JpaRepository<Planet, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code IdPlanet}. */
  Optional<Planet> findByIdPlanet(Integer id);

  /** Derived Spring-Data query - returns entities matching {@code Name}. */
  Optional<Planet> findByName(String name);
}

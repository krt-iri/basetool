package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Planet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PlanetRepository extends JpaRepository<Planet, UUID> {
    Optional<Planet> findByIdPlanet(Integer id);
    Optional<Planet> findByName(String name);
}

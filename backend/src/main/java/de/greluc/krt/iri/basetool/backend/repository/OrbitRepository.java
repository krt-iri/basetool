package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Orbit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for Orbit. */
public interface OrbitRepository extends JpaRepository<Orbit, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code IdOrbit}. */
  Optional<Orbit> findByIdOrbit(Integer id);

  /** Derived Spring-Data query - returns entities matching {@code Name}. */
  Optional<Orbit> findByName(String name);
}

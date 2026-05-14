package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.StarSystem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Star System. */
@Repository
public interface StarSystemRepository extends JpaRepository<StarSystem, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code IdSystem}. */
  Optional<StarSystem> findByIdSystem(Integer idSystem);

  /** Derived Spring-Data query - returns entities matching {@code Name}. */
  Optional<StarSystem> findByName(String name);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * NameIgnoreCase}.
   */
  boolean existsByNameIgnoreCase(String name);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * NameIgnoreCaseAndIdNot}.
   */
  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}

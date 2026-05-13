package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Moon;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for Moon. */
public interface MoonRepository extends JpaRepository<Moon, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code IdMoon}. */
  Optional<Moon> findByIdMoon(Integer id);

  /** Derived Spring-Data query - returns entities matching {@code Name}. */
  Optional<Moon> findByName(String name);
}

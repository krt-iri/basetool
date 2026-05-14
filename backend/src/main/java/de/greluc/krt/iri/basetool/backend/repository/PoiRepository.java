package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Poi;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for Poi. */
public interface PoiRepository extends JpaRepository<Poi, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code IdPoi}. */
  Optional<Poi> findByIdPoi(Integer id);

  /** Derived Spring-Data query - returns entities matching {@code Name}. */
  Optional<Poi> findByName(String name);
}

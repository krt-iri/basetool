package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.City;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for City. */
public interface CityRepository extends JpaRepository<City, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code IdCity}. */
  Optional<City> findByIdCity(Integer id);

  /** Derived Spring-Data query - returns entities matching {@code Name}. */
  Optional<City> findByName(String name);
}

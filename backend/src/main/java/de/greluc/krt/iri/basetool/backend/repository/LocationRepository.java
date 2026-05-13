package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Location;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Location. */
@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {

  /**
   * Custom JPQL/native query; see the {@code @Query} annotation for the projection and filter
   * clauses.
   */
  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.dto.LocationReferenceDto(l.id, l.name) FROM Location l WHERE l.hidden = false ORDER BY l.name")
  List<de.greluc.krt.iri.basetool.backend.model.dto.LocationReferenceDto> findAllReference();

  /** Derived Spring-Data query - returns entities matching {@code Name}. */
  Optional<Location> findByName(String name);

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

  /** Derived Spring-Data query - returns entities matching {@code CityId}. */
  Optional<Location> findByCityId(UUID cityId);

  /** Derived Spring-Data query - returns entities matching {@code SpaceStationId}. */
  Optional<Location> findBySpaceStationId(UUID spaceStationId);

  /** Derived Spring-Data query - returns entities matching {@code HiddenFalse}. */
  Page<Location> findByHiddenFalse(Pageable pageable);

  /**
   * Custom JPQL/native query; see the {@code @Query} annotation for the projection and filter
   * clauses.
   */
  @Query(
      "SELECT l FROM Location l LEFT JOIN l.city c LEFT JOIN l.spaceStation s WHERE c.hasRefinery = true OR s.hasRefinery = true")
  List<Location> findLocationsWithRefinery();
}

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

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {

  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.dto.LocationReferenceDto(l.id, l.name) FROM Location l WHERE l.hidden = false ORDER BY l.name")
  List<de.greluc.krt.iri.basetool.backend.model.dto.LocationReferenceDto> findAllReference();

  Optional<Location> findByName(String name);

  boolean existsByNameIgnoreCase(String name);

  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

  Optional<Location> findByCityId(UUID cityId);

  Optional<Location> findBySpaceStationId(UUID spaceStationId);

  Page<Location> findByHiddenFalse(Pageable pageable);

  @Query(
      "SELECT l FROM Location l LEFT JOIN l.city c LEFT JOIN l.spaceStation s WHERE c.hasRefinery = true OR s.hasRefinery = true")
  List<Location> findLocationsWithRefinery();
}

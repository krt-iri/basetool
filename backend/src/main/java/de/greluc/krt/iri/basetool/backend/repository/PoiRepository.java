package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Poi;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PoiRepository extends JpaRepository<Poi, UUID> {
  Optional<Poi> findByIdPoi(Integer id);

  Optional<Poi> findByName(String name);
}

package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Poi;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PoiRepository extends JpaRepository<Poi, UUID> {
    Optional<Poi> findByIdPoi(Integer id);
    Optional<Poi> findByName(String name);
}

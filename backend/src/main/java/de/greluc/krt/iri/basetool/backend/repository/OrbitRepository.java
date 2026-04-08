package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Orbit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface OrbitRepository extends JpaRepository<Orbit, UUID> {
    Optional<Orbit> findByIdOrbit(Integer id);
    Optional<Orbit> findByName(String name);
}

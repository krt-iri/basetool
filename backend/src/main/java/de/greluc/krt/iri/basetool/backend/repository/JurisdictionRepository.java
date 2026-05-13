package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Jurisdiction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JurisdictionRepository extends JpaRepository<Jurisdiction, UUID> {
  Optional<Jurisdiction> findByIdJurisdiction(Integer id);

  Optional<Jurisdiction> findByName(String name);
}

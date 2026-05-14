package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Jurisdiction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for Jurisdiction. */
public interface JurisdictionRepository extends JpaRepository<Jurisdiction, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code IdJurisdiction}. */
  Optional<Jurisdiction> findByIdJurisdiction(Integer id);

  /** Derived Spring-Data query - returns entities matching {@code Name}. */
  Optional<Jurisdiction> findByName(String name);
}

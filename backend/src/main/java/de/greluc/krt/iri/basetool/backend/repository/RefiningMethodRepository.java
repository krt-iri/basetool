package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.RefiningMethod;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Refining Method. */
@Repository
public interface RefiningMethodRepository extends JpaRepository<RefiningMethod, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code Name}. */
  Optional<RefiningMethod> findByName(String name);
}

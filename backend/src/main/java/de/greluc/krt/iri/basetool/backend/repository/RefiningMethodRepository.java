package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.RefiningMethod;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefiningMethodRepository extends JpaRepository<RefiningMethod, UUID> {
  Optional<RefiningMethod> findByName(String name);
}

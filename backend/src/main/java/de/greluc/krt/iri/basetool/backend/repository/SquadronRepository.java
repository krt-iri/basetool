package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Squadron;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SquadronRepository extends JpaRepository<Squadron, UUID> {
  Optional<Squadron> findByShorthand(String shorthand);

  boolean existsByNameIgnoreCase(String name);

  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

  List<Squadron> findAllByActiveTrue();

  Page<Squadron> findAllByActiveTrue(Pageable pageable);
}

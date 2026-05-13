package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Squadron;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Squadron. */
@Repository
public interface SquadronRepository extends JpaRepository<Squadron, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code Shorthand}. */
  Optional<Squadron> findByShorthand(String shorthand);

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

  /** Returns every entity matching the derived {@code findAllByActiveTrue} criteria. */
  List<Squadron> findAllByActiveTrue();

  /** Returns every entity matching the derived {@code findAllByActiveTrue} criteria. */
  Page<Squadron> findAllByActiveTrue(Pageable pageable);
}

package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Manufacturer. */
@Repository
public interface ManufacturerRepository extends JpaRepository<Manufacturer, UUID> {
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

  /** Derived Spring-Data query - returns entities matching {@code NameIgnoreCase}. */
  Optional<Manufacturer> findByNameIgnoreCase(String name);

  /** Derived Spring-Data query - returns entities matching {@code HiddenFalse}. */
  Page<Manufacturer> findByHiddenFalse(Pageable pageable);
}

package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.FrequencyType;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for Frequency Type. */
public interface FrequencyTypeRepository extends JpaRepository<FrequencyType, UUID> {
  /** Returns every entity matching the derived {@code findAllByActive} criteria. */
  @Query("SELECT f FROM FrequencyType f WHERE :active IS NULL OR f.active = :active")
  Page<FrequencyType> findAllByActive(@Param("active") Boolean active, Pageable pageable);
}

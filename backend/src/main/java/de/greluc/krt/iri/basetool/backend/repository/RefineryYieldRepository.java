package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.RefineryYield;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefineryYieldRepository extends JpaRepository<RefineryYield, UUID> {
  Optional<RefineryYield> findByTerminalIdAndMaterialId(UUID terminalId, UUID materialId);
}

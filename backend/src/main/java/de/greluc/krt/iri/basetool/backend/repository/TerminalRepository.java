package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TerminalRepository extends JpaRepository<Terminal, UUID> {
    Optional<Terminal> findByIdTerminal(Integer id);
    Optional<Terminal> findByName(String name);
}

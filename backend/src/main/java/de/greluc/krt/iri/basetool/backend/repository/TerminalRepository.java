package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Terminal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for Terminal. */
public interface TerminalRepository extends JpaRepository<Terminal, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code IdTerminal}. */
  Optional<Terminal> findByIdTerminal(Integer id);

  /** Derived Spring-Data query - returns entities matching {@code Name}. */
  Optional<Terminal> findByName(String name);
}

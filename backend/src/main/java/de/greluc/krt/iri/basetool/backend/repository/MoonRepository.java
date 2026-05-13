package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Moon;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoonRepository extends JpaRepository<Moon, UUID> {
  Optional<Moon> findByIdMoon(Integer id);

  Optional<Moon> findByName(String name);
}

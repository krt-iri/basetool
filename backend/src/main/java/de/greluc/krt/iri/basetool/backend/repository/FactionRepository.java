package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Faction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for Faction. */
public interface FactionRepository extends JpaRepository<Faction, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code IdFaction}. */
  Optional<Faction> findByIdFaction(Integer id);

  /** Derived Spring-Data query - returns entities matching {@code Name}. */
  Optional<Faction> findByName(String name);
}

package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Faction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FactionRepository extends JpaRepository<Faction, UUID> {
    Optional<Faction> findByIdFaction(Integer id);
    Optional<Faction> findByName(String name);
}

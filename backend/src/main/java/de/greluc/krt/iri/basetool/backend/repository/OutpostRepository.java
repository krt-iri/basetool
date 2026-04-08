package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Outpost;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface OutpostRepository extends JpaRepository<Outpost, UUID> {
    Optional<Outpost> findByIdOutpost(Integer id);
    Optional<Outpost> findByName(String name);
}

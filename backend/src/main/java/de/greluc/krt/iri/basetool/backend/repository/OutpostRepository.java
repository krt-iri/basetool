package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Outpost;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for Outpost. */
public interface OutpostRepository extends JpaRepository<Outpost, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code IdOutpost}. */
  Optional<Outpost> findByIdOutpost(Integer id);

  /** Derived Spring-Data query - returns entities matching {@code Name}. */
  Optional<Outpost> findByName(String name);
}

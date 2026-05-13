package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Role;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Role. */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
  /**
   * Derived Spring-Data query - returns entities matching {@code Name}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"permissions"})
  Optional<Role> findByName(String name);

  /**
   * Derived Spring-Data query - returns entities matching {@code NameIgnoreCase}. Eagerly fetches
   * the configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"permissions"})
  Optional<Role> findByNameIgnoreCase(String name);

  /**
   * Derived Spring-Data query - returns entities matching {@code Code}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"permissions"})
  Optional<Role> findByCode(String code);

  /**
   * Lists every entity. Overridden here to attach an {@code @EntityGraph}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @NotNull @EntityGraph(attributePaths = {"permissions"})
  Page<Role> findAll(@NotNull Pageable pageable);
}

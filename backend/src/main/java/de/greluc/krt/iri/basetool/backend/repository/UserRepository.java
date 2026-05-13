package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.UserReferenceDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Spring Data repository for User. */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  /**
   * Returns slim {@link UserReferenceDto}s for every user (id, username, displayName, effective
   * name with username fallback, rank) ordered by display name. Used to populate user pickers
   * without pulling the full User aggregate.
   */
  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.dto.UserReferenceDto(u.id, u.username, u.displayName, CASE WHEN (u.displayName IS NOT NULL AND u.displayName <> '') THEN u.displayName ELSE u.username END, u.rank) FROM User u ORDER BY u.displayName")
  List<UserReferenceDto> findAllReference();

  /**
   * Derived Spring-Data query - returns entities matching {@code Id}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @NotNull @EntityGraph(attributePaths = {"roles", "roles.permissions"})
  Optional<User> findById(@NotNull UUID id);

  /** Derived Spring-Data query - returns entities matching {@code Email}. */
  Optional<User> findByEmail(String email);

  /**
   * Derived Spring-Data query - returns entities matching {@code Username}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"roles", "roles.permissions"})
  Optional<User> findByUsername(String username);

  /**
   * Derived Spring-Data query - returns entities matching {@code
   * UsernameIgnoreCaseOrDisplayNameIgnoreCase}. Eagerly fetches the configured relations via
   * {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"roles"})
  Optional<User> findByUsernameIgnoreCaseOrDisplayNameIgnoreCase(
      String username, String displayName);

  /**
   * Returns every entity matching the derived {@code
   * findAllByUsernameIgnoreCaseOrDisplayNameIgnoreCase} criteria. Eagerly fetches the configured
   * relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"roles"})
  List<User> findAllByUsernameIgnoreCaseOrDisplayNameIgnoreCase(
      String username, String displayName);

  /**
   * Derived Spring-Data query - returns entities matching {@code
   * UsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase}. Eagerly fetches the configured
   * relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"roles"})
  List<User> findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
      String username, String displayName);

  /**
   * Derived Spring-Data query - returns entities matching {@code
   * UsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase}. Eagerly fetches the configured
   * relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"roles"})
  Page<User> findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
      String username, String displayName, Pageable pageable);

  /**
   * Lists every entity. Overridden here to attach an {@code @EntityGraph}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"roles"})
  Page<User> findAll(Pageable pageable);

  /**
   * Sets {@code inKeycloak = false} on every user whose id is not in the freshly-synced Keycloak id
   * list. Called by the periodic Keycloak sync so accounts removed upstream become flagged locally
   * without being deleted (preserves history and FK references).
   */
  @org.springframework.data.jpa.repository.Modifying
  @Query("UPDATE User u SET u.inKeycloak = false WHERE u.id NOT IN :ids")
  void markMissingUsers(@NotNull java.util.Collection<java.util.UUID> ids);

  /**
   * Returns every user carrying the {@code ADMIN} role (case-insensitive match), ordered by
   * username.
   */
  @Query("SELECT u FROM User u JOIN u.roles r WHERE UPPER(r.name) = 'ADMIN' ORDER BY u.username")
  List<User> findAllAdmins();
}

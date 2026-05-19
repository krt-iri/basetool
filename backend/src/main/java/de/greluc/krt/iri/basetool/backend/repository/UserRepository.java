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
   *
   * <p>Multi-tenant: {@code scopeSquadronId} restricts the result to members of that squadron
   * (MULTI_SQUADRON_PLAN.md section 4.4: list/search for normal users sees own squadron only).
   * {@code null} signals admin "all squadrons" mode and falls back to the cross-staffel list. Users
   * that have no squadron assigned (admins, guests) are always included so an admin in focused mode
   * still sees the unassigned bucket alongside the squadron members.
   */
  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.dto.UserReferenceDto(u.id, u.username,"
          + " u.displayName, CASE WHEN (u.displayName IS NOT NULL AND u.displayName <> '') THEN"
          + " u.displayName ELSE u.username END, u.rank) FROM User u WHERE :scopeSquadronId IS"
          + " NULL OR u.squadron IS NULL OR u.squadron.id = :scopeSquadronId ORDER BY"
          + " u.displayName")
  List<UserReferenceDto> findAllReferenceScoped(
      @org.springframework.data.repository.query.Param("scopeSquadronId") UUID scopeSquadronId);

  /**
   * Unscoped variant used internally by JWT sync flows where access is always implicit. Kept for
   * backwards compatibility — every caller-facing path should go through {@link
   * #findAllReferenceScoped(UUID)} instead.
   */
  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.dto.UserReferenceDto(u.id, u.username,"
          + " u.displayName, CASE WHEN (u.displayName IS NOT NULL AND u.displayName <> '') THEN"
          + " u.displayName ELSE u.username END, u.rank) FROM User u ORDER BY u.displayName")
  List<UserReferenceDto> findAllReference();

  /**
   * Squadron-scoped paged listing. Filters the {@code findAll} variant by {@code u.squadron.id =
   * :scopeSquadronId} (or no filter when {@code null}). Same null-handling rule as {@link
   * #findAllReferenceScoped(UUID)} — unassigned users are always visible so the focused admin can
   * manage them.
   */
  @EntityGraph(attributePaths = {"roles"})
  @Query(
      "SELECT u FROM User u WHERE :scopeSquadronId IS NULL OR u.squadron IS NULL OR"
          + " u.squadron.id = :scopeSquadronId")
  Page<User> findAllScoped(
      @org.springframework.data.repository.query.Param("scopeSquadronId") UUID scopeSquadronId,
      Pageable pageable);

  /**
   * Unpaged squadron-scoped listing. Same predicate as {@link #findAllScoped(UUID, Pageable)}
   * without pagination — used by the legacy {@code findAll()} call sites that still expect a plain
   * {@link List}.
   */
  @EntityGraph(attributePaths = {"roles"})
  @Query(
      "SELECT u FROM User u WHERE :scopeSquadronId IS NULL OR u.squadron IS NULL OR"
          + " u.squadron.id = :scopeSquadronId")
  List<User> findAllScopedList(
      @org.springframework.data.repository.query.Param("scopeSquadronId") UUID scopeSquadronId,
      org.springframework.data.domain.Sort sort);

  /**
   * Paged squadron-scoped listing of users eligible to be evaluated in the promotion system.
   * Excludes admins entirely — they are squadron-less by design (admins always have {@code
   * app_user.squadron_id = NULL} per V81 backfill) and must not appear in any Officer's
   * Bewertungsverwaltung even when an admin has focused a squadron via the switcher. The additional
   * explicit role-based exclusion guards against a manually mis-assigned admin row that still
   * carries a squadron FK.
   *
   * <p>When {@code scopeSquadronId} is {@code null} (admin "all squadrons" mode) the result spans
   * every squadron's members. A non-null id restricts to that squadron. Users without a squadron
   * are excluded — they are not part of any squadron's evaluation list.
   *
   * @param scopeSquadronId squadron filter; {@code null} = all squadrons.
   * @param pageable Spring Data paging and sorting parameters.
   * @return paged squadron members that an Officer / Admin may evaluate.
   */
  @EntityGraph(attributePaths = {"roles", "squadron"})
  @Query(
      "SELECT u FROM User u WHERE u.squadron IS NOT NULL AND (:scopeSquadronId IS NULL OR"
          + " u.squadron.id = :scopeSquadronId) AND NOT EXISTS (SELECT 1 FROM u.roles r WHERE"
          + " UPPER(r.name) = 'ADMIN')")
  Page<User> findEvaluatableMembers(
      @org.springframework.data.repository.query.Param("scopeSquadronId") UUID scopeSquadronId,
      Pageable pageable);

  /**
   * Squadron-scoped substring search. Mirrors {@link
   * #findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(String, String, Pageable)}
   * but adds the {@code u.squadron.id = :scopeSquadronId OR :scopeSquadronId IS NULL OR u.squadron
   * IS NULL} clause.
   */
  @EntityGraph(attributePaths = {"roles"})
  @Query(
      "SELECT u FROM User u WHERE (LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR"
          + " LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%'))) AND (:scopeSquadronId IS"
          + " NULL OR u.squadron IS NULL OR u.squadron.id = :scopeSquadronId)")
  Page<User> searchScoped(
      @org.springframework.data.repository.query.Param("query") String query,
      @org.springframework.data.repository.query.Param("scopeSquadronId") UUID scopeSquadronId,
      Pageable pageable);

  /**
   * Squadron-scoped substring search returning a plain list. Same predicate as {@link
   * #searchScoped(String, UUID, Pageable)} without pagination.
   */
  @EntityGraph(attributePaths = {"roles"})
  @Query(
      "SELECT u FROM User u WHERE (LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR"
          + " LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%'))) AND (:scopeSquadronId IS"
          + " NULL OR u.squadron IS NULL OR u.squadron.id = :scopeSquadronId)")
  List<User> searchScopedList(
      @org.springframework.data.repository.query.Param("query") String query,
      @org.springframework.data.repository.query.Param("scopeSquadronId") UUID scopeSquadronId);

  /**
   * Derived Spring-Data query - returns entities matching {@code Id}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @Override
  @NotNull
  @EntityGraph(attributePaths = {"roles", "roles.permissions"})
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
  @Override
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

package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.KeycloakUserDto;
import de.greluc.krt.iri.basetool.backend.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the local {@code app_user} mirror of Keycloak users plus everything around them (roles,
 * logistician/mission-manager flags, rank, description, displayName, joinDate, read announcement
 * state).
 *
 * <p>JWT-driven sync ({@link #syncUser(Jwt)}) runs on every authentication so the local record is
 * always in sync with Keycloak; periodic scheduled sync ({@link #syncUser(KeycloakUserDto)} +
 * {@link #markMissingUsers}) is driven by {@link UserSyncTask} and reconciles deletions. The
 * service is the architectural seam where the project's "every read filters by JWT sub" rule
 * (CLAUDE.md) is enforced — {@link #getCurrentUser()} is the canonical source for the calling
 * user's id and most other services delegate here rather than reaching for {@code
 * SecurityContextHolder} (which is forbidden outside this seam by the ArchUnit rule).
 *
 * <p>JWT subject parsing is fail-closed: a missing {@code sub} or a non-UUID subject is rejected
 * rather than falling back to a derived identifier — silently mapping different Keycloak realms
 * onto the same local id is a worse failure mode than refusing the request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final InventoryItemRepository inventoryItemRepository;
  private final ShipRepository shipRepository;
  private final RefineryOrderRepository refineryOrderRepository;
  private final MissionRepository missionRepository;
  private final JobOrderRepository jobOrderRepository;
  private final MissionParticipantRepository missionParticipantRepository;
  private final AuthHelperService authHelperService;

  /**
   * Convenience predicate: does any user have this exact name (case-insensitive) as either username
   * or displayName? Used by participant-add flows to detect "this guest name is actually a known
   * member".
   *
   * @param name candidate name
   * @return true when at least one match exists
   */
  public boolean isUsernameOrDisplayNameTaken(@NotNull String name) {
    return !findMatchesByExactName(name).isEmpty();
  }

  /**
   * Resolves a free-text participant name to existing users by case-insensitive exact match on
   * {@code username} or {@code displayName}. The input is trimmed. An empty or blank name yields an
   * empty result without hitting the database.
   *
   * <p>Used by participant-add flows to translate free-text input (when the user did not pick an
   * entry from the autocomplete dropdown) into a concrete user reference, so that a member is
   * correctly linked instead of being (wrongly) rejected as a duplicate guest name.
   */
  @NotNull public List<User> findMatchesByExactName(@NotNull String name) {
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      return List.of();
    }
    return userRepository.findAllByUsernameIgnoreCaseOrDisplayNameIgnoreCase(trimmed, trimmed);
  }

  /**
   * Extracts the user id from the JWT's {@code sub} claim.
   *
   * <p>Fail-closed validation: a missing {@code sub} or a non-UUID value throws {@link
   * org.springframework.security.authentication.AuthenticationServiceException} rather than falling
   * back to a derived id. Silently mapping different Keycloak realms (or two realms with similar
   * usernames) onto the same local id is a worse failure mode than refusing the request — see the
   * explicit AGENTS.md / CLAUDE.md guidance on stable identity.
   *
   * @param jwt validated JWT
   * @return the {@code sub} parsed as UUID
   * @throws org.springframework.security.authentication.AuthenticationServiceException when sub is
   *     missing or not a UUID
   */
  @NotNull public UUID getUserIdFromJwt(@NotNull Jwt jwt) {
    String sub = jwt.getSubject();
    if (sub == null) {
      // The OIDC standard requires `sub` on every ID token. A missing subject
      // indicates a misconfigured authorization server. Refuse rather than
      // falling back to a different claim and silently identifying users by
      // a value an admin might rename in Keycloak.
      log.error("JWT has no subject (sub). Refusing the request. Claims: {}", jwt.getClaims());
      throw new org.springframework.security.authentication.AuthenticationServiceException(
          "JWT subject (sub) must be present");
    }

    try {
      return UUID.fromString(sub);
    } catch (IllegalArgumentException e) {
      // Standard Keycloak issues UUIDs as subjects. A non-UUID sub is a
      // configuration deviation; deriving a UUID via UUID.nameUUIDFromBytes
      // would mix up identities (renaming the underlying value, two realms
      // with similar usernames, casing differences, ...). Fail-closed.
      log.error(
          "JWT subject is not a valid UUID: '{}'. Refusing the request to avoid identity mix-up.",
          sub);
      throw new org.springframework.security.authentication.AuthenticationServiceException(
          "JWT subject must be a UUID");
    }
  }

  /**
   * Reconciles the local user record with the supplied JWT — creates the row on first login,
   * updates username / email / displayName / roles when they have changed. Called by {@link
   * CustomJwtGrantedAuthoritiesConverter} on every authentication.
   *
   * <p>Two-step lookup: first by id (the UUID-shaped subject), then by {@code preferred_username} —
   * handles legacy rows where the local id pre-dated the Keycloak migration to UUID subjects. Roles
   * default to "Guest" when the JWT carries none so an unconfigured Keycloak doesn't lock everyone
   * out.
   *
   * @param jwt validated JWT from the resource server
   * @return the managed (created or updated) user
   */
  @Transactional
  @NotNull public User syncUser(@NotNull Jwt jwt) {
    final UUID finalUserId = getUserIdFromJwt(jwt);
    String username = jwt.getClaimAsString("preferred_username");

    Optional<User> existingUser = userRepository.findById(finalUserId);
    if (existingUser.isEmpty() && username != null) {
      existingUser = userRepository.findByUsername(username);
      if (existingUser.isPresent()) {
        log.warn(
            "User lookup by ID {} failed, but found by username {}. associating session with existing user.",
            finalUserId,
            username);
      }
    }

    User user =
        existingUser.orElseGet(
            () -> {
              User u = new User();
              u.setId(finalUserId);
              return u;
            });

    boolean changed = false;

    if (!Objects.equals(user.getUsername(), username)) {
      user.setUsername(username);
      changed = true;
    }

    String firstName = jwt.getClaimAsString("given_name");
    if (!Objects.equals(user.getFirstName(), firstName)) {
      user.setFirstName(firstName);
      changed = true;
    }

    String lastName = jwt.getClaimAsString("family_name");
    if (!Objects.equals(user.getLastName(), lastName)) {
      user.setLastName(lastName);
      changed = true;
    }

    String email = jwt.getClaimAsString("email");
    if (!Objects.equals(user.getEmail(), email)) {
      user.setEmail(email);
      changed = true;
    }

    // Sync Roles
    Set<String> keycloakRoles = extractRolesFromJwt(jwt);
    Set<Role> localRoles = mapRoles(keycloakRoles);

    if (!user.getRoles().equals(localRoles)) {
      user.setRoles(localRoles);
      changed = true;
    }

    if (changed || user.isNew()) {
      return userRepository.save(user);
    }

    return user;
  }

  /**
   * Reconciles the local user record from a Keycloak Admin API response (scheduled sync path).
   * Mirrors {@link #syncUser(Jwt)} but reads the data from a {@code KeycloakUserDto} instead of a
   * decoded JWT. Used by {@link UserSyncTask}.
   *
   * @param dto Keycloak admin DTO
   */
  @Transactional
  public void syncUser(@NotNull KeycloakUserDto dto) {
    if (dto.id() == null) return;

    User user =
        userRepository
            .findById(dto.id())
            .orElseGet(
                () -> {
                  User u = new User();
                  u.setId(dto.id());
                  return u;
                });

    boolean changed = false;

    if (!user.isInKeycloak()) {
      user.setInKeycloak(true);
      changed = true;
    }

    if (!Objects.equals(user.getUsername(), dto.username())) {
      user.setUsername(dto.username());
      changed = true;
    }

    if (!Objects.equals(user.getFirstName(), dto.firstName())) {
      user.setFirstName(dto.firstName());
      changed = true;
    }

    if (!Objects.equals(user.getLastName(), dto.lastName())) {
      user.setLastName(dto.lastName());
      changed = true;
    }

    if (!Objects.equals(user.getEmail(), dto.email())) {
      user.setEmail(dto.email());
      changed = true;
    }

    // Sync Roles
    Set<Role> localRoles = mapRoles(dto.roles());
    if (!user.getRoles().equals(localRoles)) {
      user.setRoles(localRoles);
      changed = true;
    }

    if (changed || user.isNew()) {
      userRepository.save(user);
    }
  }

  /**
   * Flags every local user whose id is NOT in {@code currentIds} as missing (soft-delete: kept as a
   * row for FK integrity, but excluded from active lists). Called by {@link UserSyncTask} after the
   * full Keycloak sync run; an empty {@code currentIds} short-circuits without marking anyone so a
   * Keycloak outage doesn't soft-delete the entire user base.
   *
   * @param currentIds the set of user ids currently present in Keycloak
   */
  @Transactional
  public void markMissingUsers(Collection<UUID> currentIds) {
    if (currentIds.isEmpty()) return;
    userRepository.markMissingUsers(currentIds);
  }

  private Set<Role> mapRoles(Collection<String> roleNames) {
    Set<Role> localRoles = new HashSet<>();
    if (roleNames != null) {
      for (String rName : roleNames) {
        roleRepository.findByNameIgnoreCase(rName).ifPresent(localRoles::add);
      }
    }

    if (localRoles.isEmpty()) {
      roleRepository.findByNameIgnoreCase("Guest").ifPresent(localRoles::add);
    }
    return localRoles;
  }

  /**
   * Extracts the set of realm-role names from a JWT. Reads the {@code realm_access.roles} claim —
   * Keycloak's standard location. Resource-access roles are intentionally NOT included because this
   * project's authorization model is realm-scoped.
   *
   * @param jwt validated JWT
   * @return realm role names, possibly empty
   */
  @SuppressWarnings("unchecked")
  @NotNull public Set<String> extractRolesFromJwt(@NotNull Jwt jwt) {
    Set<String> roles = new HashSet<>();
    Map<String, Object> realmAccess = jwt.getClaim("realm_access");
    if (realmAccess != null && realmAccess.containsKey("roles")) {
      roles.addAll((List<String>) realmAccess.get("roles"));
    }
    // Also check resource_access if needed, but realm_access is standard for realm roles
    return roles;
  }

  /**
   * Updates a user's editable attributes (rank, description, displayName, joinDate). Optimistic-
   * lock check is explicit when {@code version} is non-null; admins can override by passing {@code
   * null}.
   *
   * <p>Rank validation enforces the role-based range: officers get 1–12, squadron members get
   * 13–20. Out-of-range rank throws {@link IllegalArgumentException} → 400. {@code joinDate} can be
   * explicitly set to {@code null} to clear the field; the other nullable fields are only updated
   * when supplied.
   *
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when the user is unknown
   * @throws ObjectOptimisticLockingFailureException when the supplied version is stale
   * @throws IllegalArgumentException when the rank is outside the role-permitted range
   */
  @Transactional
  @NotNull public User updateUserAttributes(
      @NotNull UUID id,
      @Nullable Integer rank,
      @Nullable String description,
      @Nullable String displayName,
      @Nullable Long version,
      @Nullable java.time.LocalDate joinDate) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                        "User not found"));

    if (version != null && user.getVersion() != null && !user.getVersion().equals(version)) {
      throw new ObjectOptimisticLockingFailureException(User.class, id);
    }

    if (rank != null) {
      boolean isOfficer =
          user.getRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase("OFFICER"));
      boolean isSquadronMember =
          user.getRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase("SQUADRON_MEMBER"));

      if (isOfficer) {
        if (rank < 1 || rank > 12) {
          throw new IllegalArgumentException("Officers can only have rank 1-12");
        }
      } else if (isSquadronMember) {
        if (rank < 13 || rank > 20) {
          throw new IllegalArgumentException("Squadron members can only have rank 13-20");
        }
      }
      user.setRank(rank);
    }
    if (description != null) user.setDescription(description);
    if (displayName != null) user.setDisplayName(displayName.isBlank() ? null : displayName);
    // joinDate can be explicitly set to null (clear the date)
    user.setJoinDate(joinDate);
    return userRepository.save(user);
  }

  /**
   * Narrower update than {@link #updateUserAttributes}: covers only the profile-page editable
   * subset (description + displayName). Used by the user's own profile-edit form so a regular user
   * cannot bump their rank.
   *
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when the user is unknown
   * @throws ObjectOptimisticLockingFailureException when the supplied version is stale
   */
  @Transactional
  public User updateUserDescription(
      @NotNull UUID id,
      @Nullable String description,
      @Nullable String displayName,
      @Nullable Long version) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                        "User not found"));
    if (version != null && user.getVersion() != null && !user.getVersion().equals(version)) {
      throw new ObjectOptimisticLockingFailureException(User.class, id);
    }
    if (description != null) user.setDescription(description);
    if (displayName != null) user.setDisplayName(displayName.isBlank() ? null : displayName);
    return userRepository.save(user);
  }

  /**
   * Records that the user has read the given announcement (clears the unread badge on the home
   * page).
   *
   * @param id user id
   * @param announcementId announcement they read
   * @return the persisted user
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when the user id is
   *     unknown
   */
  @Transactional
  public User updateReadAnnouncement(@NotNull UUID id, @NotNull UUID announcementId) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                        "User not found"));
    user.setLastReadAnnouncementId(announcementId);
    return userRepository.save(user);
  }

  /**
   * @return all users sorted case-insensitively by username
   */
  public List<User> findAll() {
    return userRepository.findAll(Sort.by(Sort.Order.asc("username").ignoreCase()));
  }

  /**
   * @return lightweight reference projection used by typeaheads (id + username + displayName)
   */
  public List<de.greluc.krt.iri.basetool.backend.model.dto.UserReferenceDto> findAllReference() {
    return userRepository.findAllReference();
  }

  /**
   * @param pageable page request
   * @return paged user list
   */
  public Page<User> findAll(@NotNull Pageable pageable) {
    return userRepository.findAll(pageable);
  }

  /**
   * Unpaged username/displayName substring search.
   *
   * @param query free-text filter
   * @return matching users
   */
  public List<User> searchByUsername(@NotNull String query) {
    return userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
        query, query);
  }

  /**
   * Paged username/displayName substring search.
   *
   * @param query free-text filter
   * @param pageable page request
   * @return matching users
   */
  public Page<User> searchByUsername(@NotNull String query, @NotNull Pageable pageable) {
    return userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
        query, query, pageable);
  }

  /**
   * @param id user primary key
   * @return the user
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no match
   */
  public User findById(@NotNull UUID id) {
    return userRepository
        .findById(id)
        .orElseThrow(
            () ->
                new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                    "User not found"));
  }

  /**
   * Looks up the calling user via the JWT in the current {@link Authentication}. Returns empty for
   * unauthenticated callers (guests). The single canonical accessor for "who is calling" — other
   * services delegate here instead of reaching for {@code SecurityContextHolder} (the architectural
   * seam enforced by ArchUnit).
   *
   * @return the calling user, or empty for unauthenticated requests
   */
  public Optional<User> getCurrentUser() {
    Authentication auth = authHelperService.rawAuthentication();
    if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Jwt jwt)) {
      return Optional.empty();
    }
    UUID userId = getUserIdFromJwt(jwt);
    return userRepository.findById(userId);
  }

  /**
   * Flips the {@code is_logistician} flag on a user. The flag is independent of Keycloak realm
   * roles — admins can grant the LOGISTICIAN role via this method without a Keycloak round-trip;
   * the JWT-to-authorities converter promotes the flag to {@code ROLE_LOGISTICIAN} on the next
   * authentication.
   *
   * @param userId user primary key
   * @param isLogistician new flag value
   * @return the persisted user
   */
  @Transactional
  public User updateLogisticianStatus(UUID userId, boolean isLogistician) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NoSuchElementException("User not found with id: " + userId));
    user.setLogistician(isLogistician);
    return userRepository.save(user);
  }

  /**
   * Flips the {@code is_mission_manager} flag on a user. Mirrors {@link #updateLogisticianStatus}
   * but for the MISSION_MANAGER role.
   *
   * @param userId user primary key
   * @param isMissionManager new flag value
   * @return the persisted user
   */
  @Transactional
  public User updateMissionManagerStatus(UUID userId, boolean isMissionManager) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NoSuchElementException("User not found with id: " + userId));
    user.setMissionManager(isMissionManager);
    return userRepository.save(user);
  }

  /**
   * Deletes a user account along with all owned data (ships, inventory, refinery orders, mission
   * memberships). Used by admins to remove ex-members. The cascade is explicit (per-table delete
   * calls) so the order matches the FK constraints; auto-cascading would surface confusing FK
   * errors when the table order changes.
   *
   * @param userId user to delete
   * @throws NoSuchElementException when the user id is unknown
   */
  @Transactional
  public void deleteUser(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NoSuchElementException("User not found"));

    if (user.isInKeycloak()) {
      throw new IllegalStateException("Cannot delete user that is still in Keycloak");
    }

    User admin =
        userRepository.findAllAdmins().stream()
            .filter(u -> !u.getId().equals(userId))
            .findFirst()
            .orElseGet(
                () ->
                    getCurrentUser()
                        .filter(u -> !u.getId().equals(userId))
                        .filter(
                            u ->
                                u.getRoles().stream()
                                    .anyMatch(r -> r.getName().equalsIgnoreCase("ADMIN")))
                        .orElseThrow(
                            () ->
                                new IllegalStateException("No admin user found to reassign data")));

    // Reassign mandatory fields
    inventoryItemRepository.updateOwner(user, admin);
    shipRepository.updateOwner(user, admin);
    refineryOrderRepository.updateOwner(user, admin);
    missionRepository.updateOwner(user, admin);

    // Remove ManyToMany and nullable references
    missionRepository.removeManager(userId);
    jobOrderRepository.removeAssignee(userId);
    missionParticipantRepository.unlinkUser(userId);

    // Delete the user
    userRepository.delete(user);
    log.info("User {} deleted and references reassigned to admin {}", userId, admin.getId());
  }
}

package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.KeycloakUserDto;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.RoleRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Unit tests for the under-tested halves of {@link UserService}:
 *
 * <ul>
 *   <li>{@link UserService#getUserIdFromJwt} — JWT sub/UUID validation.
 *   <li>{@link UserService#syncUser(Jwt)} — the JWT&nbsp;-&gt;&nbsp;local-user mapping invoked on
 *       every authenticated request via the JWT interceptor.
 *   <li>{@link UserService#syncUser(KeycloakUserDto)} — the scheduled Keycloak Admin API sync (the
 *       previously completely-uncovered {@code inKeycloak} flip + change-detection branches).
 *   <li>{@link UserService#extractRolesFromJwt} — null and missing-key {@code realm_access} paths.
 *   <li>{@link UserService#updateLogisticianStatus} / {@link
 *       UserService#updateMissionManagerStatus} — admin-toggle operations called out in CLAUDE.md
 *       as the alternative to Keycloak-role assignment.
 *   <li>{@link UserService#updateUserDescription} — the version-check {@code
 *       ObjectOptimisticLockingFailureException} path.
 *   <li>{@link UserService#getCurrentUser} — null/anonymous/non-JWT principal short-circuits.
 *   <li>{@link UserService#findById} — not-found path.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceSyncTest {

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private InventoryItemRepository inventoryItemRepository;
  @Mock private ShipRepository shipRepository;
  @Mock private RefineryOrderRepository refineryOrderRepository;
  @Mock private MissionRepository missionRepository;
  @Mock private JobOrderRepository jobOrderRepository;
  @Mock private MissionParticipantRepository missionParticipantRepository;
  @Mock private AuthHelperService authHelperService;

  @InjectMocks private UserService userService;

  private static final UUID USER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

  // ---------------------------------------------------------------
  // getUserIdFromJwt
  // ---------------------------------------------------------------

  @Nested
  class GetUserIdFromJwtTests {

    @Test
    void returnsUuid_whenSubjectIsValidUuid() {
      Jwt jwt = newJwt(USER_ID.toString(), Map.of());
      assertEquals(USER_ID, userService.getUserIdFromJwt(jwt));
    }

    @Test
    void throwsAuthenticationServiceException_whenSubjectIsNull() {
      // Jwt.Builder requires a non-null subject; build with empty subject is impossible.
      // Mock to return null directly.
      Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
      when(jwt.getSubject()).thenReturn(null);

      assertThrows(AuthenticationServiceException.class, () -> userService.getUserIdFromJwt(jwt));
    }

    @Test
    void throwsAuthenticationServiceException_whenSubjectIsNotUuid() {
      Jwt jwt = newJwt("not-a-uuid", Map.of());

      AuthenticationServiceException ex =
          assertThrows(
              AuthenticationServiceException.class, () -> userService.getUserIdFromJwt(jwt));
      assertTrue(ex.getMessage().contains("must be a UUID"));
    }
  }

  // ---------------------------------------------------------------
  // syncUser(Jwt) — the hot path on every authenticated request
  // ---------------------------------------------------------------

  @Nested
  class SyncJwtUserTests {

    @Test
    void createsNewUser_whenIdAndUsernameUnknown() {
      Jwt jwt =
          newJwt(
              USER_ID.toString(),
              Map.of(
                  "preferred_username", "alice",
                  "given_name", "Alice",
                  "family_name", "Liddell",
                  "email", "alice@example.com"));

      when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
      when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
      when(roleRepository.findByNameIgnoreCase("Guest"))
          .thenReturn(Optional.of(role(99L, "Guest")));
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      User result = userService.syncUser(jwt);

      assertEquals(USER_ID, result.getId());
      assertEquals("alice", result.getUsername());
      assertEquals("Alice", result.getFirstName());
      assertEquals("Liddell", result.getLastName());
      assertEquals("alice@example.com", result.getEmail());
      assertEquals(1, result.getRoles().size());
      assertEquals("Guest", result.getRoles().iterator().next().getName());
      verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void usesUsernameFallback_whenIdLookupFails_butUsernameMatches() {
      // The "warn-and-recover" path: ID changed (rare, Keycloak realm import,
      // imports, ...) but username still matches a legacy local row.
      Jwt jwt = newJwt(USER_ID.toString(), Map.of("preferred_username", "alice"));
      User existing = newUser(UUID.randomUUID(), "alice");
      existing.setVersion(1L); // not new

      when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
      when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));
      when(roleRepository.findByNameIgnoreCase("Guest"))
          .thenReturn(Optional.of(role(99L, "Guest")));
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      User result = userService.syncUser(jwt);

      assertSame(existing, result, "must reuse the looked-up legacy user");
    }

    @Test
    void noFieldChanged_andUserNotNew_skipsSave() {
      // Every field already matches the JWT claims; the user has been seen
      // before (version != null -> isNew() == false). The service must short-
      // circuit and NOT call save().
      Jwt jwt =
          newJwt(
              USER_ID.toString(),
              Map.of(
                  "preferred_username", "alice",
                  "given_name", "Alice",
                  "family_name", "Liddell",
                  "email", "alice@example.com"));

      User existing = newUser(USER_ID, "alice");
      existing.setFirstName("Alice");
      existing.setLastName("Liddell");
      existing.setEmail("alice@example.com");
      existing.setVersion(2L);
      Role guest = role(99L, "Guest");
      existing.setRoles(new HashSet<>(Set.of(guest)));

      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
      when(roleRepository.findByNameIgnoreCase("Guest")).thenReturn(Optional.of(guest));

      User result = userService.syncUser(jwt);

      assertSame(existing, result);
      verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void detectsChange_whenUsernameDiffers() {
      assertSavedOnFieldChange(
          "preferred_username", "new-username", User::setUsername, "old-username");
    }

    @Test
    void detectsChange_whenFirstNameDiffers() {
      assertSavedOnFieldChange("given_name", "Alice-New", User::setFirstName, "Alice-Old");
    }

    @Test
    void detectsChange_whenLastNameDiffers() {
      assertSavedOnFieldChange("family_name", "Liddell-New", User::setLastName, "Liddell-Old");
    }

    @Test
    void detectsChange_whenEmailDiffers() {
      assertSavedOnFieldChange("email", "new@example.com", User::setEmail, "old@example.com");
    }

    @Test
    void detectsChange_whenRolesDiffer() {
      Jwt jwt =
          newJwt(
              USER_ID.toString(),
              Map.of(
                  "preferred_username",
                  "alice",
                  "realm_access",
                  Map.of("roles", List.of("ADMIN"))));

      User existing = newUser(USER_ID, "alice");
      existing.setVersion(1L);
      Role guest = role(99L, "Guest");
      existing.setRoles(new HashSet<>(Set.of(guest)));

      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
      when(roleRepository.findByNameIgnoreCase("ADMIN")).thenReturn(Optional.of(role(1L, "ADMIN")));
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      userService.syncUser(jwt);

      verify(userRepository, times(1)).save(any(User.class));
      assertEquals(1, existing.getRoles().size());
      assertEquals("ADMIN", existing.getRoles().iterator().next().getName());
    }

    @Test
    void newUserWithNoChangedFields_isStillSaved() {
      // The "user.isNew()" branch triggers save() even when no detected
      // changes happened — required because a brand-new entity has to be
      // persisted to acquire an ID/version.
      Jwt jwt = newJwt(USER_ID.toString(), Map.of()); // no claims at all

      when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
      when(roleRepository.findByNameIgnoreCase("Guest"))
          .thenReturn(Optional.of(role(99L, "Guest")));
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      User result = userService.syncUser(jwt);

      // Even though every JWT claim is null and every field stays null,
      // changed==true via the role-sync block (empty Keycloak roles -> Guest)
      // and additionally user.isNew()==true.
      verify(userRepository, times(1)).save(result);
    }

    /**
     * Builds a JWT where every field already matches an existing user, then flips the named claim
     * to a different value and asserts that save is called.
     */
    private void assertSavedOnFieldChange(
        String jwtClaim,
        String newValue,
        java.util.function.BiConsumer<User, String> oldFieldSetter,
        String oldValue) {
      Map<String, Object> claims =
          new java.util.HashMap<>(
              Map.of(
                  "preferred_username", "alice",
                  "given_name", "Alice",
                  "family_name", "Liddell",
                  "email", "alice@example.com"));
      claims.put(jwtClaim, newValue);
      Jwt jwt = newJwt(USER_ID.toString(), claims);

      User existing = newUser(USER_ID, "alice");
      existing.setFirstName("Alice");
      existing.setLastName("Liddell");
      existing.setEmail("alice@example.com");
      existing.setVersion(1L);
      oldFieldSetter.accept(existing, oldValue);
      Role guest = role(99L, "Guest");
      existing.setRoles(new HashSet<>(Set.of(guest)));

      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
      lenient().when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
      when(roleRepository.findByNameIgnoreCase("Guest")).thenReturn(Optional.of(guest));
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      userService.syncUser(jwt);
      verify(userRepository, times(1)).save(any(User.class));
    }
  }

  // ---------------------------------------------------------------
  // syncUser(KeycloakUserDto)
  // ---------------------------------------------------------------

  @Nested
  class SyncKeycloakUserTests {

    @Test
    void returnsEarly_whenDtoIdIsNull() {
      KeycloakUserDto dto =
          new KeycloakUserDto(null, "alice", "Alice", "L", "alice@example.com", true, Set.of());

      userService.syncUser(dto);

      verify(userRepository, never()).save(any());
    }

    @Test
    void flipsInKeycloak_whenLocalUserPreviouslyMarkedAbsent() {
      User existing = newUser(USER_ID, "alice");
      existing.setInKeycloak(false);
      existing.setVersion(1L);

      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
      when(roleRepository.findByNameIgnoreCase("Guest"))
          .thenReturn(Optional.of(role(99L, "Guest")));

      userService.syncUser(new KeycloakUserDto(USER_ID, "alice", null, null, null, true, Set.of()));

      assertTrue(existing.isInKeycloak(), "must flip back to true once seen again");
      verify(userRepository, times(1)).save(existing);
    }

    @Test
    void createsNewUser_whenIdUnknown() {
      when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
      when(roleRepository.findByNameIgnoreCase("Guest"))
          .thenReturn(Optional.of(role(99L, "Guest")));

      userService.syncUser(
          new KeycloakUserDto(USER_ID, "alice", "Alice", "L", "alice@example.com", true, Set.of()));

      verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void noFieldChanged_andUserNotNew_skipsSave() {
      User existing = newUser(USER_ID, "alice");
      existing.setFirstName("Alice");
      existing.setLastName("L");
      existing.setEmail("alice@example.com");
      existing.setInKeycloak(true);
      existing.setVersion(3L);
      Role guest = role(99L, "Guest");
      existing.setRoles(new HashSet<>(Set.of(guest)));

      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
      when(roleRepository.findByNameIgnoreCase("Guest")).thenReturn(Optional.of(guest));

      userService.syncUser(
          new KeycloakUserDto(USER_ID, "alice", "Alice", "L", "alice@example.com", true, Set.of()));

      verify(userRepository, never()).save(any());
    }
  }

  // ---------------------------------------------------------------
  // extractRolesFromJwt
  // ---------------------------------------------------------------

  @Nested
  class ExtractRolesFromJwtTests {

    @Test
    void returnsRoles_whenRealmAccessHasRolesKey() {
      Jwt jwt =
          newJwt(
              USER_ID.toString(),
              Map.of("realm_access", Map.of("roles", List.of("ADMIN", "SQUADRON_MEMBER"))));

      Set<String> roles = userService.extractRolesFromJwt(jwt);

      assertEquals(Set.of("ADMIN", "SQUADRON_MEMBER"), roles);
    }

    @Test
    void returnsEmpty_whenRealmAccessClaimMissing() {
      Jwt jwt = newJwt(USER_ID.toString(), Map.of());

      assertTrue(userService.extractRolesFromJwt(jwt).isEmpty());
    }

    @Test
    void returnsEmpty_whenRealmAccessLacksRolesKey() {
      Jwt jwt =
          newJwt(USER_ID.toString(), Map.of("realm_access", Map.of("something_else", "value")));

      assertTrue(userService.extractRolesFromJwt(jwt).isEmpty());
    }
  }

  // ---------------------------------------------------------------
  // mapRoles (via syncUser) — Guest fallback when none match
  // ---------------------------------------------------------------

  @Test
  void mapRoles_fallsBackToGuest_whenNoKeycloakRoleMatchesLocal() {
    Jwt jwt =
        newJwt(
            USER_ID.toString(),
            Map.of(
                "preferred_username",
                "alice",
                "realm_access",
                Map.of("roles", List.of("UNKNOWN_ROLE_FROM_OTHER_REALM"))));

    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
    when(roleRepository.findByNameIgnoreCase("UNKNOWN_ROLE_FROM_OTHER_REALM"))
        .thenReturn(Optional.empty());
    when(roleRepository.findByNameIgnoreCase("Guest")).thenReturn(Optional.of(role(99L, "Guest")));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    User result = userService.syncUser(jwt);

    assertEquals(1, result.getRoles().size());
    assertEquals("Guest", result.getRoles().iterator().next().getName());
  }

  @Test
  void mapRoles_nullRoleNames_alsoFallsBackToGuest() {
    // KeycloakUserDto.roles() == null is treated as empty.
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(roleRepository.findByNameIgnoreCase("Guest")).thenReturn(Optional.of(role(99L, "Guest")));

    userService.syncUser(new KeycloakUserDto(USER_ID, "alice", null, null, null, true, null));

    verify(roleRepository, times(1)).findByNameIgnoreCase("Guest");
  }

  // ---------------------------------------------------------------
  // updateUserDescription
  // ---------------------------------------------------------------

  @Nested
  class UpdateUserDescriptionTests {

    @Test
    void throwsNotFoundException_whenUserMissing() {
      when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

      assertThrows(
          NotFoundException.class,
          () -> userService.updateUserDescription(USER_ID, "desc", "display", 1L));
    }

    @Test
    void throwsOptimisticLockingFailure_whenVersionMismatch() {
      User user = newUser(USER_ID, "alice");
      user.setVersion(7L);
      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

      assertThrows(
          ObjectOptimisticLockingFailureException.class,
          () -> userService.updateUserDescription(USER_ID, "desc", "display", 3L));
    }

    @Test
    void updatesBothFields_whenProvided() {
      User user = newUser(USER_ID, "alice");
      user.setVersion(1L);
      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
      when(userRepository.save(user)).thenReturn(user);

      userService.updateUserDescription(USER_ID, "new description", "Display Name", 1L);

      assertEquals("new description", user.getDescription());
      assertEquals("Display Name", user.getDisplayName());
    }

    @Test
    void blankDisplayName_isNormalisedToNull() {
      User user = newUser(USER_ID, "alice");
      user.setVersion(1L);
      user.setDisplayName("previous");
      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
      when(userRepository.save(user)).thenReturn(user);

      userService.updateUserDescription(USER_ID, null, "   ", 1L);

      assertEquals(
          null,
          user.getDisplayName(),
          "blank-only displayName is stored as null so getEffectiveName() "
              + "falls through to username");
    }

    @Test
    void nullVersion_bypassesOptimisticCheck() {
      User user = newUser(USER_ID, "alice");
      user.setVersion(5L);
      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
      when(userRepository.save(user)).thenReturn(user);

      userService.updateUserDescription(USER_ID, "x", null, null);

      assertEquals("x", user.getDescription());
    }
  }

  // ---------------------------------------------------------------
  // updateLogisticianStatus / updateMissionManagerStatus
  // ---------------------------------------------------------------

  @Nested
  class AdminRoleToggleTests {

    @Test
    void updateLogisticianStatus_throws_whenUserMissing() {
      when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

      assertThrows(
          NoSuchElementException.class, () -> userService.updateLogisticianStatus(USER_ID, true));
    }

    @Test
    void updateLogisticianStatus_setsTrue() {
      User user = newUser(USER_ID, "alice");
      user.setLogistician(false);
      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
      when(userRepository.save(user)).thenReturn(user);

      userService.updateLogisticianStatus(USER_ID, true);

      assertTrue(user.isLogistician());
    }

    @Test
    void updateLogisticianStatus_setsFalse() {
      User user = newUser(USER_ID, "alice");
      user.setLogistician(true);
      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
      when(userRepository.save(user)).thenReturn(user);

      userService.updateLogisticianStatus(USER_ID, false);

      assertFalse(user.isLogistician());
    }

    @Test
    void updateMissionManagerStatus_throws_whenUserMissing() {
      when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

      assertThrows(
          NoSuchElementException.class,
          () -> userService.updateMissionManagerStatus(USER_ID, true));
    }

    @Test
    void updateMissionManagerStatus_setsTrue() {
      User user = newUser(USER_ID, "alice");
      user.setMissionManager(false);
      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
      when(userRepository.save(user)).thenReturn(user);

      userService.updateMissionManagerStatus(USER_ID, true);

      assertTrue(user.isMissionManager());
    }
  }

  // ---------------------------------------------------------------
  // findById
  // ---------------------------------------------------------------

  @Nested
  class FindByIdTests {

    @Test
    void returnsUser_whenPresent() {
      User user = newUser(USER_ID, "alice");
      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

      assertSame(user, userService.findById(USER_ID));
    }

    @Test
    void throwsNotFoundException_whenAbsent() {
      when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> userService.findById(USER_ID));
    }
  }

  // ---------------------------------------------------------------
  // getCurrentUser — every short-circuit branch
  // ---------------------------------------------------------------

  @Nested
  class GetCurrentUserTests {

    @Test
    void returnsEmpty_whenNoAuthenticationBound() {
      when(authHelperService.rawAuthentication()).thenReturn(null);

      assertTrue(userService.getCurrentUser().isEmpty());
    }

    @Test
    void returnsEmpty_whenAuthenticationIsNotAuthenticated() {
      Authentication auth = UsernamePasswordAuthenticationToken.unauthenticated("alice", "x");
      when(authHelperService.rawAuthentication()).thenReturn(auth);

      assertTrue(userService.getCurrentUser().isEmpty());
    }

    @Test
    void returnsEmpty_whenPrincipalIsNotJwt() {
      Authentication auth =
          new UsernamePasswordAuthenticationToken("alice", "n/a", java.util.List.of());
      when(authHelperService.rawAuthentication()).thenReturn(auth);

      assertTrue(
          userService.getCurrentUser().isEmpty(),
          "without a Jwt principal there's no Keycloak sub to look up");
    }

    @Test
    void returnsUserOptional_whenJwtPrincipalPresent() {
      Jwt jwt = newJwt(USER_ID.toString(), Map.of());
      Authentication auth =
          new UsernamePasswordAuthenticationToken(jwt, "n/a", java.util.List.of());
      when(authHelperService.rawAuthentication()).thenReturn(auth);
      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(newUser(USER_ID, "alice")));

      Optional<User> result = userService.getCurrentUser();

      assertTrue(result.isPresent());
      assertEquals(USER_ID, result.get().getId());
    }
  }

  // ---------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------

  private static Jwt newJwt(String subject, Map<String, Object> additionalClaims) {
    Map<String, Object> claims = new java.util.HashMap<>();
    claims.put("sub", subject);
    claims.putAll(additionalClaims);
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject(subject)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .claims(c -> c.putAll(claims))
        .build();
  }

  private static User newUser(UUID id, String username) {
    User u = new User();
    u.setId(id);
    u.setUsername(username);
    return u;
  }

  private static Role role(long id, String name) {
    Role r = new Role();
    r.setId(id);
    r.setName(name);
    return r;
  }
}

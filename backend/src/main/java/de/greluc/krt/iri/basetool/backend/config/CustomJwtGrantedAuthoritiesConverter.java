package de.greluc.krt.iri.basetool.backend.config;

import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Translates an incoming Keycloak JWT into the authorities Spring Security will check against
 * {@code @PreAuthorize}.
 *
 * <p>Three sources are merged: (1) Keycloak realm roles assigned to the user, mapped to {@code
 * ROLE_<UPPER_SNAKE_CASE>} authorities; (2) every permission name attached to those roles in the
 * local {@code role}/{@code permission} tables, used directly (no {@code ROLE_} prefix) for
 * fine-grained {@code hasAuthority} checks; (3) the per-OrgUnit-membership flags {@code
 * is_logistician} and {@code is_mission_manager} on {@code org_unit_membership}, promoted to flat
 * {@code ROLE_LOGISTICIAN} / {@code ROLE_MISSION_MANAGER} so an admin can grant these roles via the
 * membership-management UI without round-tripping through Keycloak.
 *
 * <p>R6.d / SPEZIALKOMMANDO_PLAN.md D3 + §6.1: the per-role flags are now sourced from {@code
 * org_unit_membership}, not from the legacy {@code app_user.is_logistician} / {@code
 * app_user.is_mission_manager} columns. The user gets the flat role iff <b>any</b> of their
 * memberships (Staffel + every SK) carries the flag — the contextual scoping ("logistician of which
 * OrgUnit") still happens at the {@code @PreAuthorize} call site through {@link
 * de.greluc.krt.iri.basetool.backend.service.OwnerScopeService}. The legacy columns are kept as a
 * fallback: if a freshly seeded user has not had their V95 membership backfill applied yet, the
 * converter degrades to the User-level flags so the user does not lose access during a staggered
 * migration. The fallback comes out together with the columns in the destructive cleanup release.
 *
 * <p>The converter calls {@link UserService#syncUser(Jwt)} on every authentication so the local row
 * is created or updated lazily — this is where new Keycloak users acquire their {@code app_user}
 * record. Optimistic-locking conflicts from concurrent first-time logins by the same user are
 * retried up to {@value #MAX_SYNC_ATTEMPTS} times with a short fixed backoff; after that the
 * authentication is rejected with {@link AuthenticationServiceException} to avoid a stuck client
 * retry loop.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomJwtGrantedAuthoritiesConverter
    implements Converter<Jwt, Collection<GrantedAuthority>> {

  private static final int MAX_SYNC_ATTEMPTS = 3;
  private static final long RETRY_BACKOFF_MILLIS = 50L;

  private final UserService userService;
  private final OrgUnitMembershipRepository orgUnitMembershipRepository;

  @Override
  public Collection<GrantedAuthority> convert(@NonNull Jwt jwt) {
    ObjectOptimisticLockingFailureException lastLockingFailure = null;
    for (int attempt = 1; attempt <= MAX_SYNC_ATTEMPTS; attempt++) {
      try {
        User user = userService.syncUser(jwt);

        Collection<GrantedAuthority> authorities =
            user.getRoles().stream()
                .flatMap(
                    role -> {
                      Stream<GrantedAuthority> roleAuth =
                          Stream.of(
                              new SimpleGrantedAuthority(
                                  "ROLE_" + role.getName().toUpperCase().replace(" ", "_")));
                      Stream<GrantedAuthority> permAuth =
                          role.getPermissions().stream().map(SimpleGrantedAuthority::new);
                      return Stream.concat(roleAuth, permAuth);
                    })
                .collect(Collectors.toCollection(ArrayList::new));

        // R6.d / Plan D3: source the per-role flags from the user's OrgUnit memberships. Any
        // membership that carries `is_logistician = true` promotes the caller to the flat
        // ROLE_LOGISTICIAN authority (same for ROLE_MISSION_MANAGER). The legacy User-level
        // columns are consulted as a fallback so a user whose memberships have not yet been
        // backfilled by V95 (impossible today but defensive for the migration window) does not
        // silently lose access.
        addMembershipDerivedRoles(user, authorities);

        return authorities;
      } catch (ObjectOptimisticLockingFailureException e) {
        lastLockingFailure = e;
        int attemptsLeft = MAX_SYNC_ATTEMPTS - attempt;
        log.warn(
            "Optimistic locking failure during user sync (attempt {}/{}). Attempts left: {}",
            attempt,
            MAX_SYNC_ATTEMPTS,
            attemptsLeft);
        if (attemptsLeft > 0) {
          try {
            Thread.sleep(RETRY_BACKOFF_MILLIS);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new AuthenticationServiceException(
                "User authority sync interrupted while retrying after optimistic locking failure",
                ie);
          }
        }
      }
    }

    log.error(
        "Failed to sync user authorities after {} attempts due to repeated optimistic locking"
            + " failures. Authentication denied.",
        MAX_SYNC_ATTEMPTS,
        lastLockingFailure);
    throw new AuthenticationServiceException(
        "Failed to resolve user authorities after " + MAX_SYNC_ATTEMPTS + " attempts",
        lastLockingFailure);
  }

  /**
   * R6.d / Plan D3 — promotes the flat {@code ROLE_LOGISTICIAN} / {@code ROLE_MISSION_MANAGER}
   * authorities based on the OR-union of every OrgUnit membership the user belongs to. A user who
   * is a Logistician in <em>any</em> of their Staffel/SK memberships qualifies for the flat role;
   * the per-OrgUnit scoping is enforced separately by {@code @PreAuthorize} expressions that
   * delegate to {@link de.greluc.krt.iri.basetool.backend.service.OwnerScopeService}.
   *
   * <p>Legacy fallback: if the membership lookup returns an empty list (e.g. the V95 backfill has
   * not run yet for an in-flight migration), the converter reads the legacy User-level {@code
   * is_logistician} / {@code is_mission_manager} columns instead. The fallback comes out together
   * with the columns themselves once the destructive cleanup release lands.
   *
   * @param user the local {@link User} record produced by {@link UserService#syncUser(Jwt)}; never
   *     {@code null}.
   * @param authorities the mutable authority list being assembled by the converter; flags are
   *     appended in place.
   */
  private void addMembershipDerivedRoles(
      @NonNull User user, @NonNull Collection<GrantedAuthority> authorities) {
    List<OrgUnitMembership> memberships =
        orgUnitMembershipRepository.findAllByIdUserId(user.getId());

    if (memberships.isEmpty()) {
      // Pre-V95-backfill fallback. Once the destructive cleanup release drops the legacy
      // columns, this branch comes out and the converter emits no flat role authority for
      // memberless users (correctly — they have no scoped authority to grant).
      if (user.isLogistician()) {
        authorities.add(new SimpleGrantedAuthority("ROLE_LOGISTICIAN"));
      }
      if (user.isMissionManager()) {
        authorities.add(new SimpleGrantedAuthority("ROLE_MISSION_MANAGER"));
      }
      return;
    }

    boolean anyLogistician = memberships.stream().anyMatch(OrgUnitMembership::isLogistician);
    boolean anyMissionManager = memberships.stream().anyMatch(OrgUnitMembership::isMissionManager);

    if (anyLogistician) {
      authorities.add(new SimpleGrantedAuthority("ROLE_LOGISTICIAN"));
    }
    if (anyMissionManager) {
      authorities.add(new SimpleGrantedAuthority("ROLE_MISSION_MANAGER"));
    }
  }
}

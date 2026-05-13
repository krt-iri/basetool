package de.greluc.krt.iri.basetool.backend.config;

import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import java.util.ArrayList;
import java.util.Collection;
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
 * fine-grained {@code hasAuthority} checks; (3) the DB flags {@code is_logistician} and {@code
 * is_mission_manager} on {@code app_user}, promoted to {@code ROLE_LOGISTICIAN} / {@code
 * ROLE_MISSION_MANAGER} so an admin can grant these roles via the user-management UI without
 * round-tripping through Keycloak's role management.
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

        if (user.isLogistician()) {
          authorities.add(new SimpleGrantedAuthority("ROLE_LOGISTICIAN"));
        }

        if (user.isMissionManager()) {
          authorities.add(new SimpleGrantedAuthority("ROLE_MISSION_MANAGER"));
        }

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
        "Failed to sync user authorities after {} attempts due to repeated optimistic locking failures. Authentication denied.",
        MAX_SYNC_ATTEMPTS,
        lastLockingFailure);
    throw new AuthenticationServiceException(
        "Failed to resolve user authorities after " + MAX_SYNC_ATTEMPTS + " attempts",
        lastLockingFailure);
  }
}

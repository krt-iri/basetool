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

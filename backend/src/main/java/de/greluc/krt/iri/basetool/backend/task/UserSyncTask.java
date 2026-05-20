package de.greluc.krt.iri.basetool.backend.task;

import de.greluc.krt.iri.basetool.backend.model.dto.KeycloakUserDto;
import de.greluc.krt.iri.basetool.backend.service.KeycloakService;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task that mirrors the Keycloak user directory into the local {@code app_user} table.
 *
 * <p>Runs every {@code app.keycloak.sync.interval} (default {@code PT5M}). Pulls the full non-paged
 * user list from Keycloak Admin API, upserts each user via {@link UserService#syncUser}, collects
 * the set of Keycloak {@code id}s that were observed in this run, and then asks the service to mark
 * every local user that is NOT in that set as missing — that is how deletions in Keycloak get
 * reflected locally without ever issuing a hard {@code DELETE}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserSyncTask {

  private final KeycloakService keycloakService;
  private final UserService userService;

  /**
   * Fetches the current Keycloak user list and reconciles it into the local table.
   *
   * <p>Failures on individual users are logged and swallowed so a single bad row does not abort the
   * batch. After the loop, {@link UserService#markMissingUsers(java.util.Set)} flags every local
   * user whose Keycloak id did not appear in this run.
   */
  @Scheduled(fixedDelayString = "${app.keycloak.sync.interval:PT5M}")
  public void syncUsers() {
    log.info("Starting scheduled user sync from Keycloak...");
    List<KeycloakUserDto> users = keycloakService.fetchUsers();
    if (users.isEmpty()) {
      log.info("No users fetched from Keycloak.");
      return;
    }

    int count = 0;
    java.util.Set<java.util.UUID> keycloakUserIds = new java.util.HashSet<>();
    for (KeycloakUserDto user : users) {
      try {
        userService.syncUser(user);
        keycloakUserIds.add(user.id());
        count++;
      } catch (Exception e) {
        // Audit finding M-4 (2026-05-20): Keycloak {@code username} can be email-shaped (caught by
        // PiiMasker) or a real-name handle (not caught). Log the JWT-sub UUID instead — sufficient
        // to correlate with the user row on the next sync run, and free of PII.
        log.error("Failed to sync user {}", user.id(), e);
      }
    }
    userService.markMissingUsers(keycloakUserIds);
    log.info("User sync finished. Synced {} users.", count);
  }
}

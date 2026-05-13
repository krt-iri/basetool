package de.greluc.krt.iri.basetool.backend.task;

import de.greluc.krt.iri.basetool.backend.model.dto.KeycloakUserDto;
import de.greluc.krt.iri.basetool.backend.service.KeycloakService;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserSyncTask {

  private final KeycloakService keycloakService;
  private final UserService userService;

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
        log.error("Failed to sync user {}", user.username(), e);
      }
    }
    userService.markMissingUsers(keycloakUserIds);
    log.info("User sync finished. Synced {} users.", count);
  }
}

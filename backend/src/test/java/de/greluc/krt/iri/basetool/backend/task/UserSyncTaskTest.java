package de.greluc.krt.iri.basetool.backend.task;

import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.model.dto.KeycloakUserDto;
import de.greluc.krt.iri.basetool.backend.service.KeycloakService;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserSyncTaskTest {

  @Mock private KeycloakService keycloakService;

  @Mock private UserService userService;

  @InjectMocks private UserSyncTask userSyncTask;

  @Test
  void syncUsers_shouldFetchAndSyncUsers() {
    KeycloakUserDto user1 =
        new KeycloakUserDto(
            UUID.randomUUID(),
            "user1",
            "First",
            "Last",
            "u1@test.com",
            true,
            java.util.Collections.emptySet());
    KeycloakUserDto user2 =
        new KeycloakUserDto(
            UUID.randomUUID(),
            "user2",
            "First2",
            "Last2",
            "u2@test.com",
            true,
            java.util.Collections.emptySet());

    when(keycloakService.fetchUsers()).thenReturn(List.of(user1, user2));

    userSyncTask.syncUsers();

    verify(keycloakService).fetchUsers();
    verify(userService).syncUser(user1);
    verify(userService).syncUser(user2);
  }

  @Test
  void syncUsers_shouldHandleEmptyList() {
    when(keycloakService.fetchUsers()).thenReturn(Collections.emptyList());

    userSyncTask.syncUsers();

    verify(keycloakService).fetchUsers();
    verifyNoInteractions(userService);
  }

  @Test
  void syncUsers_shouldContinueOnException() {
    KeycloakUserDto user1 =
        new KeycloakUserDto(
            UUID.randomUUID(),
            "user1",
            "First",
            "Last",
            "u1@test.com",
            true,
            java.util.Collections.emptySet());
    KeycloakUserDto user2 =
        new KeycloakUserDto(
            UUID.randomUUID(),
            "user2",
            "First2",
            "Last2",
            "u2@test.com",
            true,
            java.util.Collections.emptySet());

    when(keycloakService.fetchUsers()).thenReturn(List.of(user1, user2));
    doThrow(new RuntimeException("Sync failed")).when(userService).syncUser(user1);

    userSyncTask.syncUsers();

    verify(userService).syncUser(user1);
    verify(userService).syncUser(user2);
  }
}

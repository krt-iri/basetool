/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.greluc.krt.profit.basetool.backend.model.User;
import de.greluc.krt.profit.basetool.backend.repository.*;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceDeleteTest {

  @Mock private UserRepository userRepository;
  @Mock private InventoryItemRepository inventoryItemRepository;
  @Mock private ShipRepository shipRepository;
  @Mock private RefineryOrderRepository refineryOrderRepository;
  @Mock private MissionRepository missionRepository;
  @Mock private MissionOwnershipRepository missionOwnershipRepository;
  @Mock private JobOrderRepository jobOrderRepository;
  @Mock private MissionParticipantRepository missionParticipantRepository;
  @Mock private MaterialClaimRepository materialClaimRepository;
  @Mock private RoleRepository roleRepository;
  // Required so the AuthHelperService constructor parameter of UserService is
  // satisfied. shouldThrowExceptionIfNoAdminFound exercises the deleteUser
  // fallback path through getCurrentUser(), which dereferences authHelperService —
  // a null mock would surface as a NullPointerException instead of the expected
  // IllegalStateException. Other tests in this class never reach getCurrentUser()
  // so they do not need any stubbing on the mock.
  @Mock private AuthHelperService authHelperService;
  @Mock private OrgUnitMembershipService orgUnitMembershipService;

  @InjectMocks private UserService userService;

  private User user;
  private User admin;
  private UUID userId;
  private UUID adminId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    adminId = UUID.randomUUID();

    user = new User();
    user.setId(userId);
    user.setInKeycloak(false);

    admin = new User();
    admin.setId(adminId);
    admin.setInKeycloak(true);
  }

  @Test
  void shouldDeleteUserAndReassignReferences() {
    // Given
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userRepository.findAllAdmins()).thenReturn(List.of(admin));

    // When
    userService.deleteUser(userId);

    // Then
    verify(inventoryItemRepository).updateOwner(user, admin);
    verify(shipRepository).updateOwner(user, admin);
    verify(refineryOrderRepository).updateOwner(user, admin);
    verify(missionRepository).updateOwner(user, admin);
    verify(missionOwnershipRepository).updateOwner(user, admin);
    verify(missionRepository).removeManager(userId);
    verify(jobOrderRepository).removeAssignee(userId);
    verify(missionParticipantRepository).unlinkUser(userId);
    verify(materialClaimRepository).unlinkClaimedByUser(userId);
    verify(userRepository).delete(user);
  }

  @Test
  void shouldReassignMissionOwnershipCompanionBeforeDeletingUser() {
    // Given a user that owns missions: the mission_ownership companion (its owner_id FK has no
    // ON DELETE clause) must be reassigned in lock-step with mission.owner and strictly before the
    // app_user row is removed, otherwise the dangling owner_id FK-fails (23503) on delete.
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userRepository.findAllAdmins()).thenReturn(List.of(admin));

    // When
    userService.deleteUser(userId);

    // Then the companion is reassigned alongside the mission owner, the audit-only material-claim
    // stamp is cleared, and all of that happens strictly before the app_user row is removed.
    var inOrder =
        inOrder(
            missionRepository, missionOwnershipRepository, materialClaimRepository, userRepository);
    inOrder.verify(missionRepository).updateOwner(user, admin);
    inOrder.verify(missionOwnershipRepository).updateOwner(user, admin);
    inOrder.verify(materialClaimRepository).unlinkClaimedByUser(userId);
    inOrder.verify(userRepository).delete(user);
  }

  @Test
  void shouldNotDeleteUserStillInKeycloak() {
    // Given
    user.setInKeycloak(true);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    // When & Then
    assertThrows(IllegalStateException.class, () -> userService.deleteUser(userId));
    verify(userRepository, never()).delete(any());
  }

  @Test
  void shouldThrowExceptionIfNoAdminFound() {
    // Given
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userRepository.findAllAdmins()).thenReturn(Collections.emptyList());

    // When & Then
    assertThrows(IllegalStateException.class, () -> userService.deleteUser(userId));
    verify(userRepository, never()).delete(any());
  }

  @Test
  void shouldThrowExceptionIfUserNotFound() {
    // Given
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    // When & Then
    assertThrows(NoSuchElementException.class, () -> userService.deleteUser(userId));
  }
}

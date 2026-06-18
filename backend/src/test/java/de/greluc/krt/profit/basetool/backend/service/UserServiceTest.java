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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.profit.basetool.backend.model.PayoutPreference;
import de.greluc.krt.profit.basetool.backend.model.User;
import de.greluc.krt.profit.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.profit.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.profit.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.profit.basetool.backend.repository.MissionRepository;
import de.greluc.krt.profit.basetool.backend.repository.RefineryOrderRepository;
import de.greluc.krt.profit.basetool.backend.repository.RoleRepository;
import de.greluc.krt.profit.basetool.backend.repository.ShipRepository;
import de.greluc.krt.profit.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.profit.basetool.backend.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression tests for the optimistic-lock {@code @Version} write-back on the in-place profile
 * edits ({@code updateUserDescription}, {@code updateUserDefaultPayoutPreference}). Both are
 * {@code @Transactional}, so the commit — and thus the {@code @Version} increment — happens after
 * the method returns. The profile page writes the returned version back onto every hidden version
 * input in place via {@code syncAllVersions} (no reload), so the response must be mapped from a
 * {@code saveAndFlush}, not a plain {@code save}: a plain {@code save} leaves the version
 * unflushed, so the response carries the STALE version and the user's next consecutive profile edit
 * fails with {@code ObjectOptimisticLockingFailureException} (HTTP 409). These tests pin {@code
 * saveAndFlush}, mirroring {@code InventoryItemServiceVersionFlushTest}.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private InventoryItemRepository inventoryItemRepository;
  @Mock private ShipRepository shipRepository;
  @Mock private RefineryOrderRepository refineryOrderRepository;
  @Mock private MissionRepository missionRepository;
  @Mock private JobOrderRepository jobOrderRepository;
  @Mock private MissionParticipantRepository missionParticipantRepository;
  @Mock private SquadronRepository squadronRepository;
  @Mock private AuthHelperService authHelperService;
  @Mock private OwnerScopeService ownerScopeService;
  @Mock private OrgUnitMembershipService orgUnitMembershipService;

  @InjectMocks private UserService userService;

  /**
   * Builds a managed-looking user with the given id and version 0, the starting state for an
   * in-place profile edit.
   *
   * @param id the user id
   * @return a user stamped with {@code id} and version 0
   */
  private User userWithId(UUID id) {
    User user = new User();
    user.setId(id);
    user.setVersion(0L);
    return user;
  }

  /**
   * Pins that {@code updateUserDescription} returns the user from a {@code saveAndFlush}, not a
   * plain {@code save}: the profile page writes the returned {@code @Version} back onto every
   * hidden version input in place via {@code syncAllVersions} (no reload), so a stale {@code save}
   * version would 409 the next consecutive profile edit.
   */
  @Test
  void updateUserDescription_flushesSoVersionIsFresh() {
    UUID id = UUID.randomUUID();
    User user = userWithId(id);
    when(userRepository.findById(id)).thenReturn(Optional.of(user));
    when(userRepository.saveAndFlush(user)).thenReturn(user);

    userService.updateUserDescription(id, "a new bio", "Display Name", 0L);

    verify(userRepository).saveAndFlush(user);
    verify(userRepository, never()).save(user);
  }

  /**
   * Pins that {@code updateUserDefaultPayoutPreference} returns the user from a {@code
   * saveAndFlush}, not a plain {@code save}: the profile payout-preference dropdown writes the
   * returned {@code @Version} back in place via {@code syncAllVersions} (no reload), so a stale
   * {@code save} version would 409 the next consecutive change.
   */
  @Test
  void updateUserDefaultPayoutPreference_flushesSoVersionIsFresh() {
    UUID id = UUID.randomUUID();
    User user = userWithId(id);
    when(userRepository.findById(id)).thenReturn(Optional.of(user));
    when(userRepository.saveAndFlush(user)).thenReturn(user);

    userService.updateUserDefaultPayoutPreference(id, PayoutPreference.DONATE, 0L);

    verify(userRepository).saveAndFlush(user);
    verify(userRepository, never()).save(user);
  }
}

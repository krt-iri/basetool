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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.profit.basetool.backend.mapper.BankAuditEventMapper;
import de.greluc.krt.profit.basetool.backend.model.BankAuditEvent;
import de.greluc.krt.profit.basetool.backend.model.BankAuditEventType;
import de.greluc.krt.profit.basetool.backend.model.User;
import de.greluc.krt.profit.basetool.backend.repository.BankAccountRepository;
import de.greluc.krt.profit.basetool.backend.repository.BankAuditEventRepository;
import de.greluc.krt.profit.basetool.backend.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link BankAuditService#record}: the actor is resolved from the security context
 * and snapshotted by handle (the trail must survive user deletion, REQ-BANK-012), every call
 * persists exactly one row, and callers without a resolvable user fall back to the {@code system}
 * actor.
 */
@ExtendWith(MockitoExtension.class)
class BankAuditServiceTest {

  @Mock private BankAuditEventRepository auditEventRepository;
  @Mock private AuthHelperService authHelperService;
  @Mock private UserRepository userRepository;
  @Mock private BankAccountRepository accountRepository;
  @Mock private BankAuditEventMapper bankAuditEventMapper;

  @InjectMocks private BankAuditService bankAuditService;

  @Test
  void record_persistsExactlyOneRowWithActorSnapshot() {
    // Given
    UUID actorId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    User actor = new User();
    actor.setId(actorId);
    actor.setUsername("banker_jo");
    when(authHelperService.currentUserId()).thenReturn(Optional.of(actorId));
    when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
    when(auditEventRepository.save(any(BankAuditEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    bankAuditService.record(
        BankAuditEventType.DEPOSIT_BOOKED, accountId, null, null, "+100 aUEC @greluc");

    // Then
    ArgumentCaptor<BankAuditEvent> saved = ArgumentCaptor.forClass(BankAuditEvent.class);
    verify(auditEventRepository).save(saved.capture());
    BankAuditEvent row = saved.getValue();
    assertEquals(actorId, row.getActorUserId());
    assertEquals("banker_jo", row.getActorHandle());
    assertEquals(BankAuditEventType.DEPOSIT_BOOKED, row.getEventType());
    assertEquals(accountId, row.getAccountId());
    assertEquals("+100 aUEC @greluc", row.getDetails());
    assertNotNull(row.getOccurredAt());
  }

  @Test
  void record_fallsBackToSystemActorWithoutResolvableUser() {
    // Given
    when(authHelperService.currentUserId()).thenReturn(Optional.empty());
    when(auditEventRepository.save(any(BankAuditEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    bankAuditService.record(BankAuditEventType.WIPE_RESET_EXECUTED, null, null, null, "x");

    // Then
    ArgumentCaptor<BankAuditEvent> saved = ArgumentCaptor.forClass(BankAuditEvent.class);
    verify(auditEventRepository).save(saved.capture());
    assertEquals("system", saved.getValue().getActorHandle());
  }
}

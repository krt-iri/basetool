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

package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.mapper.BankHolderMapper;
import de.greluc.krt.iri.basetool.backend.model.BankAuditEventType;
import de.greluc.krt.iri.basetool.backend.model.BankHolder;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.request.RegisterBankHolderRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.request.UpdateBankHolderRequest;
import de.greluc.krt.iri.basetool.backend.repository.BankHolderRepository;
import de.greluc.krt.iri.basetool.backend.repository.BankPostingRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Unit tests for {@link BankHolderService}: registration with the effective-name handle snapshot
 * (REQ-BANK-003 — the snapshot is what survives user deletion), the one-holder-per-user pre-check,
 * the activity toggle with optimistic-lock check, and the audit rows.
 */
@ExtendWith(MockitoExtension.class)
class BankHolderServiceTest {

  @Mock private BankHolderRepository holderRepository;
  @Mock private BankPostingRepository postingRepository;
  @Mock private UserRepository userRepository;
  @Mock private BankHolderMapper bankHolderMapper;
  @Mock private BankAuditService bankAuditService;

  @InjectMocks private BankHolderService bankHolderService;

  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void quietMapper() {
    lenient()
        .when(bankHolderMapper.toDto(any(BankHolder.class), any(BigDecimal.class), anyLong()))
        .thenReturn(null);
  }

  @Test
  void registerHolder_snapshotsTheEffectiveNameAsHandle() {
    // Given: a user whose display name differs from the username — the snapshot must capture
    // the effective name so the ledger stays readable after user deletion
    User user = new User();
    user.setId(userId);
    user.setUsername("greluc_raw");
    user.setDisplayName("greluc");
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(holderRepository.existsByUserId(userId)).thenReturn(false);
    when(holderRepository.save(any(BankHolder.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    bankHolderService.registerHolder(new RegisterBankHolderRequest(userId));

    // Then
    ArgumentCaptor<BankHolder> saved = ArgumentCaptor.forClass(BankHolder.class);
    verify(holderRepository).save(saved.capture());
    assertEquals("greluc", saved.getValue().getHandle());
    assertTrue(saved.getValue().isActive());
    verify(bankAuditService)
        .record(eq(BankAuditEventType.HOLDER_REGISTERED), any(), any(), eq(userId), eq("greluc"));
  }

  @Test
  void registerHolder_rejectsSecondRowForSameUser() {
    // Given
    User user = new User();
    user.setId(userId);
    user.setUsername("greluc");
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(holderRepository.existsByUserId(userId)).thenReturn(true);

    // When / Then
    assertThrows(
        DuplicateEntityException.class,
        () -> bankHolderService.registerHolder(new RegisterBankHolderRequest(userId)));
    verify(holderRepository, never()).save(any());
  }

  @Test
  void updateHolder_deactivationIsAudited() {
    // Given
    UUID holderId = UUID.randomUUID();
    BankHolder holder = new BankHolder();
    holder.setId(holderId);
    holder.setHandle("carol");
    holder.setActive(true);
    holder.setVersion(4L);
    when(holderRepository.findById(holderId)).thenReturn(Optional.of(holder));
    when(holderRepository.save(any(BankHolder.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(postingRepository.holderTotals()).thenReturn(List.of());

    // When
    bankHolderService.updateHolder(holderId, new UpdateBankHolderRequest(false, 4L));

    // Then
    verify(bankAuditService)
        .record(eq(BankAuditEventType.HOLDER_DEACTIVATED), any(), any(), any(), eq("carol"));
  }

  @Test
  void updateHolder_staleVersionFailsFastWith409() {
    // Given
    UUID holderId = UUID.randomUUID();
    BankHolder holder = new BankHolder();
    holder.setId(holderId);
    holder.setHandle("carol");
    holder.setVersion(9L);
    when(holderRepository.findById(holderId)).thenReturn(Optional.of(holder));

    // When / Then
    assertThrows(
        ObjectOptimisticLockingFailureException.class,
        () -> bankHolderService.updateHolder(holderId, new UpdateBankHolderRequest(false, 8L)));
    verify(holderRepository, never()).save(any());
  }
}

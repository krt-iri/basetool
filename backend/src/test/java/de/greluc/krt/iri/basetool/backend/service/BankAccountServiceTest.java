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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.BankConflictException;
import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.mapper.BankAccountMapper;
import de.greluc.krt.iri.basetool.backend.model.BankAccount;
import de.greluc.krt.iri.basetool.backend.model.BankAccountStatus;
import de.greluc.krt.iri.basetool.backend.model.BankAccountType;
import de.greluc.krt.iri.basetool.backend.model.BankAuditEventType;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.dto.request.BankAccountLifecycleRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.request.CreateBankAccountRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.request.RenameBankAccountRequest;
import de.greluc.krt.iri.basetool.backend.repository.BankAccountRepository;
import de.greluc.krt.iri.basetool.backend.repository.BankPostingRepository;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Unit tests for {@link BankAccountService}: the type-specific owner-reference validation, the
 * singleton/per-org-unit uniqueness 409s, the {@code KB-} number generation, the zero-balance close
 * rule and the explicit optimistic-lock checks (REQ-BANK-001/-002).
 */
@ExtendWith(MockitoExtension.class)
class BankAccountServiceTest {

  @Mock private BankAccountRepository accountRepository;
  @Mock private BankPostingRepository postingRepository;
  @Mock private OrgUnitRepository orgUnitRepository;
  @Mock private BankAccountMapper bankAccountMapper;
  @Mock private BankAuditService bankAuditService;
  @Mock private BankBookingRequestService bankBookingRequestService;

  @InjectMocks private BankAccountService bankAccountService;

  @Test
  void createAccount_orgUnit_requiresReferenceAndUniqueness() {
    // Given
    UUID orgUnitId = UUID.randomUUID();
    Squadron squadron = new Squadron();
    squadron.setId(orgUnitId);
    when(orgUnitRepository.findById(orgUnitId)).thenReturn(Optional.of(squadron));
    when(accountRepository.existsByOrgUnitId(orgUnitId)).thenReturn(false);
    when(accountRepository.nextAccountNoValue()).thenReturn(7L);
    when(accountRepository.save(any(BankAccount.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    bankAccountService.createAccount(
        new CreateBankAccountRequest("Staffel IRIDIUM", BankAccountType.ORG_UNIT, orgUnitId, null));

    // Then
    ArgumentCaptor<BankAccount> saved = ArgumentCaptor.forClass(BankAccount.class);
    verify(accountRepository).save(saved.capture());
    assertEquals("KB-0007", saved.getValue().getAccountNo());
    assertEquals(BankAccountStatus.ACTIVE, saved.getValue().getStatus());
    verify(bankAuditService)
        .record(eq(BankAuditEventType.ACCOUNT_CREATED), any(), any(), any(), any());
  }

  @Test
  void createAccount_orgUnit_withoutReferenceIsBadRequest() {
    // When / Then
    assertThrows(
        BadRequestException.class,
        () ->
            bankAccountService.createAccount(
                new CreateBankAccountRequest("X", BankAccountType.ORG_UNIT, null, null)));
    verify(accountRepository, never()).save(any());
  }

  @Test
  void createAccount_secondAccountForSameOrgUnitIsRejected() {
    // Given
    UUID orgUnitId = UUID.randomUUID();
    Squadron squadron = new Squadron();
    squadron.setId(orgUnitId);
    when(orgUnitRepository.findById(orgUnitId)).thenReturn(Optional.of(squadron));
    when(accountRepository.existsByOrgUnitId(orgUnitId)).thenReturn(true);

    // When / Then
    assertThrows(
        DuplicateEntityException.class,
        () ->
            bankAccountService.createAccount(
                new CreateBankAccountRequest("X", BankAccountType.ORG_UNIT, orgUnitId, null)));
  }

  @Test
  void createAccount_secondCartelSingletonIsRejected() {
    // Given
    when(accountRepository.existsByType(BankAccountType.CARTEL)).thenReturn(true);

    // When / Then
    assertThrows(
        DuplicateEntityException.class,
        () ->
            bankAccountService.createAccount(
                new CreateBankAccountRequest("DAS KARTELL", BankAccountType.CARTEL, null, null)));
  }

  @Test
  void createAccount_area_requiresAreaNameAndForbidsOrgUnit() {
    // When / Then: missing area name
    assertThrows(
        BadRequestException.class,
        () ->
            bankAccountService.createAccount(
                new CreateBankAccountRequest("Bereich Profit", BankAccountType.AREA, null, null)));
    // And: org unit on an AREA account
    assertThrows(
        BadRequestException.class,
        () ->
            bankAccountService.createAccount(
                new CreateBankAccountRequest(
                    "Bereich Profit", BankAccountType.AREA, UUID.randomUUID(), "Profit")));
  }

  @Test
  void closeAccount_rejectsNonZeroBalanceWithStableCode() {
    // Given
    UUID accountId = UUID.randomUUID();
    BankAccount account = accountWithVersion(accountId, 3L);
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(postingRepository.accountBalance(accountId)).thenReturn(new BigDecimal("120"));

    // When
    BankConflictException ex =
        assertThrows(
            BankConflictException.class,
            () -> bankAccountService.closeAccount(accountId, new BankAccountLifecycleRequest(3L)));

    // Then
    assertEquals(BankConflictException.CODE_BANK_ACCOUNT_NOT_EMPTY, ex.getCode());
    verify(accountRepository, never()).save(any());
  }

  @Test
  void closeAccount_rejectsOpenPendingRequestsWithStableCode() {
    // Given a zero-balance account that still has an open pending booking request (REQ-BANK-025)
    UUID accountId = UUID.randomUUID();
    BankAccount account = accountWithVersion(accountId, 2L);
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(postingRepository.accountBalance(accountId)).thenReturn(BigDecimal.ZERO);
    when(bankBookingRequestService.hasOpenRequests(accountId)).thenReturn(true);

    // When
    BankConflictException ex =
        assertThrows(
            BankConflictException.class,
            () -> bankAccountService.closeAccount(accountId, new BankAccountLifecycleRequest(2L)));

    // Then
    assertEquals(BankConflictException.CODE_BANK_ACCOUNT_HAS_PENDING_REQUESTS, ex.getCode());
    verify(accountRepository, never()).save(any());
  }

  @Test
  void closeAccount_staleVersionFailsFastWith409() {
    // Given
    UUID accountId = UUID.randomUUID();
    BankAccount account = accountWithVersion(accountId, 5L);
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

    // When / Then
    assertThrows(
        ObjectOptimisticLockingFailureException.class,
        () -> bankAccountService.closeAccount(accountId, new BankAccountLifecycleRequest(4L)));
    verify(postingRepository, never()).accountBalance(any());
  }

  @Test
  void renameAccount_auditsOldAndNewName() {
    // Given
    UUID accountId = UUID.randomUUID();
    BankAccount account = accountWithVersion(accountId, 1L);
    account.setName("Alt");
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(accountRepository.save(any(BankAccount.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(postingRepository.accountBalance(accountId)).thenReturn(BigDecimal.ZERO);

    // When
    bankAccountService.renameAccount(accountId, new RenameBankAccountRequest("Neu", 1L));

    // Then
    verify(bankAuditService)
        .record(
            eq(BankAuditEventType.ACCOUNT_RENAMED),
            eq(accountId),
            any(),
            any(),
            eq("'Alt' -> 'Neu'"));
  }

  @Test
  void reopenAccount_restoresActiveStatusAndAuditsReopen() {
    // Given: a closed account
    UUID accountId = UUID.randomUUID();
    BankAccount account = accountWithVersion(accountId, 2L);
    account.setStatus(BankAccountStatus.CLOSED);
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(accountRepository.save(any(BankAccount.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(postingRepository.accountBalance(accountId)).thenReturn(BigDecimal.ZERO);

    // When
    bankAccountService.reopenAccount(accountId, new BankAccountLifecycleRequest(2L));

    // Then: status flips back to ACTIVE and the reopen is audited (REQ-BANK-002)
    ArgumentCaptor<BankAccount> saved = ArgumentCaptor.forClass(BankAccount.class);
    verify(accountRepository).save(saved.capture());
    assertEquals(BankAccountStatus.ACTIVE, saved.getValue().getStatus());
    verify(bankAuditService)
        .record(eq(BankAuditEventType.ACCOUNT_REOPENED), eq(accountId), any(), any(), any());
  }

  @Test
  void reopenAccount_staleVersionFailsFastWith409() {
    // Given
    UUID accountId = UUID.randomUUID();
    BankAccount account = accountWithVersion(accountId, 5L);
    account.setStatus(BankAccountStatus.CLOSED);
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

    // When / Then
    assertThrows(
        ObjectOptimisticLockingFailureException.class,
        () -> bankAccountService.reopenAccount(accountId, new BankAccountLifecycleRequest(4L)));
    verify(accountRepository, never()).save(any());
  }

  /** Builds a SPECIAL account stub with a given version (reflection-free, via setters). */
  private static BankAccount accountWithVersion(UUID id, Long version) {
    BankAccount account = new BankAccount();
    account.setId(id);
    account.setAccountNo("KB-0001");
    account.setName("Konto");
    account.setType(BankAccountType.SPECIAL);
    account.setStatus(BankAccountStatus.ACTIVE);
    account.setVersion(version);
    return account;
  }
}

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.BankAccountGrant;
import de.greluc.krt.iri.basetool.backend.model.BankAccountGrantId;
import de.greluc.krt.iri.basetool.backend.repository.BankAccountGrantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

/**
 * Unit tests for {@link BankSecurityService}: the capability matrix (REQ-BANK-009/-010) and the
 * org-unit-independence contract (REQ-BANK-008) — the service consults exactly two inputs (bank
 * roles via {@link AuthHelperService} and the grant table) and nothing else, so org-unit
 * memberships, contextual authorities and the admin pin cannot influence any decision by
 * construction. {@code ArchitectureTest} pins the absence of an {@code OwnerScopeService}
 * dependency at the bytecode level; the matrix e2e test pins it end to end.
 */
@ExtendWith(MockitoExtension.class)
class BankSecurityServiceTest {

  @Mock private AuthHelperService authHelperService;
  @Mock private BankAccountGrantRepository grantRepository;
  @Mock private Authentication authentication;

  @InjectMocks private BankSecurityService bankSecurityService;

  private final UUID accountId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void authenticatedByDefault() {
    lenient().when(authentication.isAuthenticated()).thenReturn(true);
  }

  @Test
  void canSee_deniesNonBankStaff_evenWithOrgRoles() {
    // Given: a caller with arbitrary org roles but no bank role
    when(authHelperService.hasReachableRole("ROLE_BANK_EMPLOYEE")).thenReturn(false);

    // When / Then: denied, and the grant table is never consulted
    assertFalse(bankSecurityService.canSee(accountId, authentication));
    verifyNoInteractions(grantRepository);
  }

  @Test
  void canSee_allowsManagementWithoutAnyGrantRow() {
    // Given
    when(authHelperService.hasReachableRole("ROLE_BANK_EMPLOYEE")).thenReturn(true);
    when(authHelperService.hasReachableRole("ROLE_BANK_MANAGEMENT")).thenReturn(true);

    // When / Then: management sees everything (REQ-BANK-010), no grant lookup needed
    assertTrue(bankSecurityService.canSee(accountId, authentication));
    verify(grantRepository, never()).findById(any(BankAccountGrantId.class));
  }

  @Test
  void canSee_allowsEmployeeWithViewOnlyGrantRow() {
    // Given: a grant row with all flags false = view-only (REQ-BANK-009)
    employeeWithGrant(false, false, false);

    // When / Then
    assertTrue(bankSecurityService.canSee(accountId, authentication));
  }

  @Test
  void canSee_deniesEmployeeWithoutGrantRow() {
    // Given
    employeeWithoutGrant();

    // When / Then
    assertFalse(bankSecurityService.canSee(accountId, authentication));
  }

  @Test
  void capabilityFlags_gateExactlyTheirBooking() {
    // Given: deposit-only grant
    employeeWithGrant(true, false, false);

    // When / Then
    assertTrue(bankSecurityService.canDeposit(accountId, authentication));
    assertFalse(bankSecurityService.canWithdraw(accountId, authentication));
    assertFalse(bankSecurityService.canTransfer(accountId, authentication));
  }

  @Test
  void canWithdraw_and_canTransfer_followTheirFlags() {
    // Given: withdraw+transfer grant
    employeeWithGrant(false, true, true);

    // When / Then
    assertFalse(bankSecurityService.canDeposit(accountId, authentication));
    assertTrue(bankSecurityService.canWithdraw(accountId, authentication));
    assertTrue(bankSecurityService.canTransfer(accountId, authentication));
  }

  @Test
  void capabilities_allowManagementUnrestricted() {
    // Given
    when(authHelperService.hasReachableRole("ROLE_BANK_EMPLOYEE")).thenReturn(true);
    when(authHelperService.hasReachableRole("ROLE_BANK_MANAGEMENT")).thenReturn(true);

    // When / Then
    assertTrue(bankSecurityService.canDeposit(accountId, authentication));
    assertTrue(bankSecurityService.canWithdraw(accountId, authentication));
    assertTrue(bankSecurityService.canTransfer(accountId, authentication));
  }

  @Test
  void canSee_deniesNullOrUnauthenticatedCallers() {
    // Given
    when(authentication.isAuthenticated()).thenReturn(false);

    // When / Then
    assertFalse(bankSecurityService.canSee(accountId, null));
    assertFalse(bankSecurityService.canSee(accountId, authentication));
    verifyNoInteractions(grantRepository);
  }

  /**
   * Pins the independence contract structurally (REQ-BANK-008): the decision consumes only the
   * role-hierarchy lookup and the grant row. No org-unit input exists in the signature or the
   * collaborator set, so joining/leaving an org unit cannot change any outcome.
   */
  @Test
  void decision_consultsOnlyRolesAndGrantTable() {
    // Given
    employeeWithGrant(true, true, true);

    // When
    bankSecurityService.canDeposit(accountId, authentication);

    // Then: exactly these collaborators were consulted
    verify(authHelperService).hasReachableRole("ROLE_BANK_EMPLOYEE");
    verify(authHelperService).hasReachableRole("ROLE_BANK_MANAGEMENT");
    verify(authHelperService).currentUserId();
    verify(grantRepository).findById(new BankAccountGrantId(userId, accountId));
  }

  /** Stubs an authenticated employee (not management) holding a grant with the given flags. */
  private void employeeWithGrant(boolean deposit, boolean withdraw, boolean transfer) {
    when(authHelperService.hasReachableRole("ROLE_BANK_EMPLOYEE")).thenReturn(true);
    when(authHelperService.hasReachableRole("ROLE_BANK_MANAGEMENT")).thenReturn(false);
    when(authHelperService.currentUserId()).thenReturn(Optional.of(userId));
    BankAccountGrant grant = new BankAccountGrant();
    grant.setId(new BankAccountGrantId(userId, accountId));
    grant.setCanDeposit(deposit);
    grant.setCanWithdraw(withdraw);
    grant.setCanTransfer(transfer);
    when(grantRepository.findById(new BankAccountGrantId(userId, accountId)))
        .thenReturn(Optional.of(grant));
  }

  /** Stubs an authenticated employee (not management) without any grant row. */
  private void employeeWithoutGrant() {
    when(authHelperService.hasReachableRole("ROLE_BANK_EMPLOYEE")).thenReturn(true);
    when(authHelperService.hasReachableRole("ROLE_BANK_MANAGEMENT")).thenReturn(false);
    when(authHelperService.currentUserId()).thenReturn(Optional.of(userId));
    when(grantRepository.findById(any(BankAccountGrantId.class))).thenReturn(Optional.empty());
  }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.greluc.krt.profit.basetool.backend.model.BankAccount;
import de.greluc.krt.profit.basetool.backend.model.BankAccountStatus;
import de.greluc.krt.profit.basetool.backend.model.BankAccountType;
import de.greluc.krt.profit.basetool.backend.model.BankBookingRequestStatus;
import de.greluc.krt.profit.basetool.backend.model.BankBookingRequestType;
import de.greluc.krt.profit.basetool.backend.model.Bereich;
import de.greluc.krt.profit.basetool.backend.model.OrgUnit;
import de.greluc.krt.profit.basetool.backend.model.Squadron;
import de.greluc.krt.profit.basetool.backend.model.dto.BankBookingRequestDto;
import de.greluc.krt.profit.basetool.backend.model.dto.OrgUnitBankBalanceDto;
import de.greluc.krt.profit.basetool.backend.model.dto.request.CreateBankBookingRequest;
import de.greluc.krt.profit.basetool.backend.model.projection.BankAccountBalance;
import de.greluc.krt.profit.basetool.backend.repository.BankAccountRepository;
import de.greluc.krt.profit.basetool.backend.repository.BankPostingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/**
 * Unit tests for {@link OrgUnitBankAccessService} — the F1 officer/lead balance view
 * (REQ-BANK-021). Verifies that the oversight scope drives exactly which org-unit accounts are
 * returned, that area / cartel accounts (no owning org unit) never leak, that a caller with no
 * oversight is given an empty list without even issuing the balance query, and that an account
 * without postings reads as zero.
 */
@ExtendWith(MockitoExtension.class)
class OrgUnitBankAccessServiceTest {

  @Mock private OwnerScopeService ownerScopeService;
  @Mock private BankAccountRepository bankAccountRepository;
  @Mock private BankPostingRepository bankPostingRepository;
  @Mock private BankBookingRequestService bankBookingRequestService;

  @InjectMocks private OrgUnitBankAccessService service;

  private static OrgUnit squadron(UUID id, String name, String shorthand) {
    Squadron squadron = new Squadron();
    squadron.setId(id);
    squadron.setName(name);
    squadron.setShorthand(shorthand);
    return squadron;
  }

  private static BankAccount account(UUID id, String accountNo, OrgUnit orgUnit) {
    BankAccount account = new BankAccount();
    account.setId(id);
    account.setAccountNo(accountNo);
    account.setName(accountNo + " account");
    account.setType(orgUnit == null ? BankAccountType.AREA : BankAccountType.ORG_UNIT);
    account.setStatus(BankAccountStatus.ACTIVE);
    account.setOrgUnit(orgUnit);
    return account;
  }

  private static OrgUnit bereich(UUID id, String name, String shorthand) {
    Bereich bereich = new Bereich();
    bereich.setId(id);
    bereich.setName(name);
    bereich.setShorthand(shorthand);
    return bereich;
  }

  /** An AREA account linked to its Bereich via the org_unit FK (epic #692 Phase 6). */
  private static BankAccount areaAccount(UUID id, String accountNo, OrgUnit bereich) {
    BankAccount account = new BankAccount();
    account.setId(id);
    account.setAccountNo(accountNo);
    account.setName(accountNo + " area account");
    account.setType(BankAccountType.AREA);
    account.setStatus(BankAccountStatus.ACTIVE);
    account.setOrgUnit(bereich);
    return account;
  }

  @Test
  void listOverseenOrgUnitBalances_returnsOnlyAccountsTheOfficerOversees() {
    // Given an officer who oversees exactly one Staffel that owns an account
    UUID ownStaffelId = UUID.randomUUID();
    UUID foreignStaffelId = UUID.randomUUID();
    UUID ownAccountId = UUID.randomUUID();
    OrgUnit ownStaffel = squadron(ownStaffelId, "Own Staffel", "OWN");
    BankAccount ownAccount = account(ownAccountId, "KB-0001", ownStaffel);
    BankAccount foreignAccount =
        account(UUID.randomUUID(), "KB-0002", squadron(foreignStaffelId, "Foreign", "FRG"));
    BankAccount areaAccount = account(UUID.randomUUID(), "KB-0003", null);
    when(ownerScopeService.currentOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of(ownStaffelId)));
    when(ownerScopeService.currentOwnLevelOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of(ownStaffelId)));
    when(bankAccountRepository.findAllByOrderByAccountNoAsc())
        .thenReturn(List.of(ownAccount, foreignAccount, areaAccount));
    when(bankPostingRepository.accountBalances(List.of(ownAccountId)))
        .thenReturn(List.of(new BankAccountBalance(ownAccountId, new BigDecimal("12345"))));

    // When
    List<OrgUnitBankBalanceDto> result = service.listOverseenOrgUnitBalances();

    // Then only the overseen org-unit account is returned, with its balance
    assertThat(result).hasSize(1);
    OrgUnitBankBalanceDto dto = result.getFirst();
    assertThat(dto.accountId()).isEqualTo(ownAccountId);
    assertThat(dto.orgUnitId()).isEqualTo(ownStaffelId);
    assertThat(dto.orgUnitShorthand()).isEqualTo("OWN");
    assertThat(dto.balance()).isEqualByComparingTo("12345");
    // The officer's own account is requestable (own-level).
    assertThat(dto.canRequest()).isTrue();
  }

  @Test
  void listOverseenOrgUnitBalances_emptyForCallerWithoutOversight() {
    // Given a plain member: empty oversight scope
    when(ownerScopeService.currentOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of()));
    when(bankAccountRepository.findAllByOrderByAccountNoAsc())
        .thenReturn(
            List.of(account(UUID.randomUUID(), "KB-0001", squadron(UUID.randomUUID(), "S", "S"))));

    // When
    List<OrgUnitBankBalanceDto> result = service.listOverseenOrgUnitBalances();

    // Then an empty list and no balance query is issued
    assertThat(result).isEmpty();
    verify(bankPostingRepository, never()).accountBalances(anyCollection());
  }

  @Test
  void listOverseenOrgUnitBalances_adminAllScopeSeesEveryOrgUnitAccountAndZeroWhenNoPostings() {
    // Given an admin with no active pin: all-scope
    UUID accountAId = UUID.randomUUID();
    UUID accountBId = UUID.randomUUID();
    BankAccount accountA = account(accountAId, "KB-0001", squadron(UUID.randomUUID(), "A", "AAA"));
    BankAccount accountB = account(accountBId, "KB-0002", squadron(UUID.randomUUID(), "B", "BBB"));
    BankAccount areaAccount = account(UUID.randomUUID(), "KB-0003", null);
    when(ownerScopeService.currentOversightScope())
        .thenReturn(new ScopePredicate(true, null, Set.of()));
    when(ownerScopeService.currentOwnLevelOversightScope())
        .thenReturn(new ScopePredicate(true, null, Set.of()));
    when(bankAccountRepository.findAllByOrderByAccountNoAsc())
        .thenReturn(List.of(accountA, accountB, areaAccount));
    // Only account A has postings; account B has none -> must read as zero
    when(bankPostingRepository.accountBalances(List.of(accountAId, accountBId)))
        .thenReturn(List.of(new BankAccountBalance(accountAId, new BigDecimal("500"))));

    // When
    List<OrgUnitBankBalanceDto> result = service.listOverseenOrgUnitBalances();

    // Then both org-unit accounts are present (area excluded), B reads as zero
    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(OrgUnitBankBalanceDto::accountId)
        .containsExactlyInAnyOrder(accountAId, accountBId);
    assertThat(result)
        .filteredOn(dto -> dto.accountId().equals(accountBId))
        .singleElement()
        .satisfies(dto -> assertThat(dto.balance()).isEqualByComparingTo("0"));
  }

  @Test
  void createBookingRequest_withinOversightScope_resolvesAccountAndDelegates() {
    UUID orgUnitId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    BankAccount account = account(accountId, "KB-0001", squadron(orgUnitId, "Own", "OWN"));
    CreateBankBookingRequest request =
        new CreateBankBookingRequest(
            orgUnitId, BankBookingRequestType.DEPOSIT, new BigDecimal("500"), "from sale");
    BankBookingRequestDto expected = dto(accountId, orgUnitId);
    // F2 (booking request) gates on the OWN-LEVEL scope (owner decision Q4), not the cascading
    // view scope that F1 uses — a subordinate account is view-only.
    when(ownerScopeService.currentOwnLevelOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of(orgUnitId)));
    when(bankAccountRepository.findByOrgUnitId(orgUnitId)).thenReturn(Optional.of(account));
    when(bankBookingRequestService.create(
            eq(accountId), eq(BankBookingRequestType.DEPOSIT), eq(new BigDecimal("500")), any()))
        .thenReturn(expected);

    BankBookingRequestDto result = service.createBookingRequest(request);

    assertThat(result).isSameAs(expected);
  }

  @Test
  void createBookingRequest_outsideOversightScope_throwsAccessDeniedAndNeverResolvesAccount() {
    UUID orgUnitId = UUID.randomUUID();
    CreateBankBookingRequest request =
        new CreateBankBookingRequest(
            orgUnitId, BankBookingRequestType.WITHDRAWAL, new BigDecimal("500"), null);
    when(ownerScopeService.currentOwnLevelOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of()));

    assertThrows(AccessDeniedException.class, () -> service.createBookingRequest(request));
    verify(bankAccountRepository, never()).findByOrgUnitId(any());
    verifyNoInteractions(bankBookingRequestService);
  }

  @Test
  void listOverseenOrgUnitBalances_bereichLeader_includesAreaAndChildAccountsNotForeign() {
    // Epic #692 Phase 6 (F1, cascading view): a Bereichsleitung sees its AREA account (org_unit FK
    // = Bereich) AND every child Staffel/SK ORG_UNIT account, but never a foreign Bereich's units.
    UUID bereichId = UUID.randomUUID();
    UUID childStaffelId = UUID.randomUUID();
    UUID areaAccountId = UUID.randomUUID();
    UUID childAccountId = UUID.randomUUID();
    BankAccount areaAccount =
        areaAccount(areaAccountId, "KB-0001", bereich(bereichId, "Profit", "PRF"));
    BankAccount childAccount =
        account(childAccountId, "KB-0002", squadron(childStaffelId, "Child", "CHD"));
    BankAccount foreignAccount =
        account(UUID.randomUUID(), "KB-0003", squadron(UUID.randomUUID(), "Foreign", "FRG"));
    when(ownerScopeService.currentOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of(bereichId, childStaffelId)));
    // Own-level is the Bereich only — the AREA account is requestable, the child account is not.
    when(ownerScopeService.currentOwnLevelOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of(bereichId)));
    when(bankAccountRepository.findAllByOrderByAccountNoAsc())
        .thenReturn(List.of(areaAccount, childAccount, foreignAccount));
    when(bankPostingRepository.accountBalances(List.of(areaAccountId, childAccountId)))
        .thenReturn(List.of(new BankAccountBalance(areaAccountId, new BigDecimal("100"))));

    List<OrgUnitBankBalanceDto> result = service.listOverseenOrgUnitBalances();

    assertThat(result)
        .extracting(OrgUnitBankBalanceDto::accountId)
        .containsExactlyInAnyOrder(areaAccountId, childAccountId);
    // The Bereich's own AREA account is requestable; the cascaded child account is view-only.
    assertThat(result)
        .filteredOn(dto -> dto.accountId().equals(areaAccountId))
        .singleElement()
        .satisfies(dto -> assertThat(dto.canRequest()).isTrue());
    assertThat(result)
        .filteredOn(dto -> dto.accountId().equals(childAccountId))
        .singleElement()
        .satisfies(dto -> assertThat(dto.canRequest()).isFalse());
  }

  @Test
  void createBookingRequest_bereichOwnLevel_resolvesAreaAccountAndDelegates() {
    UUID bereichId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    BankAccount areaAccount =
        areaAccount(accountId, "KB-0001", bereich(bereichId, "Profit", "PRF"));
    CreateBankBookingRequest request =
        new CreateBankBookingRequest(
            bereichId, BankBookingRequestType.WITHDRAWAL, new BigDecimal("75"), "rent");
    BankBookingRequestDto expected = dto(accountId, bereichId);
    when(ownerScopeService.currentOwnLevelOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of(bereichId)));
    when(bankAccountRepository.findByOrgUnitId(bereichId)).thenReturn(Optional.of(areaAccount));
    when(bankBookingRequestService.create(
            eq(accountId), eq(BankBookingRequestType.WITHDRAWAL), eq(new BigDecimal("75")), any()))
        .thenReturn(expected);

    assertThat(service.createBookingRequest(request)).isSameAs(expected);
  }

  @Test
  void createBookingRequest_subordinateAccount_rejectedAsViewOnly() {
    // Owner decision Q4: subordinate accounts reached by the F1 drill-down are VIEW-ONLY — a
    // Bereichsleitung may NOT raise a request against a child Staffel's account.
    UUID bereichId = UUID.randomUUID();
    UUID childStaffelId = UUID.randomUUID();
    CreateBankBookingRequest request =
        new CreateBankBookingRequest(
            childStaffelId, BankBookingRequestType.DEPOSIT, new BigDecimal("50"), null);
    // Own-level scope is the Bereich only; the child is not in it.
    when(ownerScopeService.currentOwnLevelOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of(bereichId)));

    assertThrows(AccessDeniedException.class, () -> service.createBookingRequest(request));
    verify(bankAccountRepository, never()).findByOrgUnitId(any());
    verifyNoInteractions(bankBookingRequestService);
  }

  private static BankBookingRequestDto dto(UUID accountId, UUID orgUnitId) {
    return new BankBookingRequestDto(
        UUID.randomUUID(),
        accountId,
        "KB-0001",
        orgUnitId,
        "Own",
        "OWN",
        BankBookingRequestType.DEPOSIT,
        new BigDecimal("500"),
        "from sale",
        BankBookingRequestStatus.PENDING,
        "requester",
        null,
        null,
        null,
        null,
        null,
        null,
        Instant.now(),
        0L);
  }
}

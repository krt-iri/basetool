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
import de.greluc.krt.profit.basetool.backend.model.BankAccountViewGrant;
import de.greluc.krt.profit.basetool.backend.model.BankAccountViewGranteeKind;
import de.greluc.krt.profit.basetool.backend.model.BankAuditEventType;
import de.greluc.krt.profit.basetool.backend.model.BankBookingRequestStatus;
import de.greluc.krt.profit.basetool.backend.model.BankBookingRequestType;
import de.greluc.krt.profit.basetool.backend.model.BankTransactionType;
import de.greluc.krt.profit.basetool.backend.model.Bereich;
import de.greluc.krt.profit.basetool.backend.model.MembershipRole;
import de.greluc.krt.profit.basetool.backend.model.OrgUnit;
import de.greluc.krt.profit.basetool.backend.model.Organisationsleitung;
import de.greluc.krt.profit.basetool.backend.model.Squadron;
import de.greluc.krt.profit.basetool.backend.model.dto.BankBookingDto;
import de.greluc.krt.profit.basetool.backend.model.dto.BankBookingRequestDto;
import de.greluc.krt.profit.basetool.backend.model.dto.OrgUnitBankBalanceDto;
import de.greluc.krt.profit.basetool.backend.model.dto.request.CreateBankBookingRequest;
import de.greluc.krt.profit.basetool.backend.model.projection.BankAccountBalance;
import de.greluc.krt.profit.basetool.backend.repository.BankAccountRepository;
import de.greluc.krt.profit.basetool.backend.repository.BankAccountViewGrantRepository;
import de.greluc.krt.profit.basetool.backend.repository.BankPostingRepository;
import de.greluc.krt.profit.basetool.backend.repository.BereichRepository;
import de.greluc.krt.profit.basetool.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

/**
 * Unit tests for {@link OrgUnitBankAccessService} (REQ-BANK-021/-027/-028/-034..038). Covers the
 * card list ({@code canView} per account type), the derived responsible-holder resolution, the
 * read-only drill-in with Halter redaction, the balance-target gate, and a visibility grant.
 * Lenient strictness keeps the shared per-test stubs (e.g. {@code isAdmin=false}, empty grant
 * batch) from tripping the unnecessary-stubbing check across the many independent scenarios.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrgUnitBankAccessServiceTest {

  @Mock private OwnerScopeService ownerScopeService;
  @Mock private AuthHelperService authHelperService;
  @Mock private BankAccountRepository bankAccountRepository;
  @Mock private BankPostingRepository bankPostingRepository;
  @Mock private BankAccountViewGrantRepository viewGrantRepository;
  @Mock private BereichRepository bereichRepository;
  @Mock private UserRepository userRepository;
  @Mock private BankAccountService bankAccountService;
  @Mock private BankStatementReportService bankStatementReportService;
  @Mock private BankBookingRequestService bankBookingRequestService;
  @Mock private BankAuditService bankAuditService;

  @InjectMocks private OrgUnitBankAccessService service;

  @BeforeEach
  void defaultStubs() {
    when(authHelperService.isAdmin()).thenReturn(false);
    when(viewGrantRepository.findByAccountIdIn(anyCollection())).thenReturn(List.of());
    when(viewGrantRepository.findByAccountId(any())).thenReturn(List.of());
    when(bankPostingRepository.postingSlicesSince(anyCollection(), any())).thenReturn(List.of());
  }

  private static OrgUnit squadron(UUID id, String name, String shorthand) {
    Squadron squadron = new Squadron();
    squadron.setId(id);
    squadron.setName(name);
    squadron.setShorthand(shorthand);
    return squadron;
  }

  private static OrgUnit bereich(UUID id, String name, String shorthand) {
    Bereich bereich = new Bereich();
    bereich.setId(id);
    bereich.setName(name);
    bereich.setShorthand(shorthand);
    return bereich;
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

  private static BankAccount typedAccount(
      UUID id, String accountNo, BankAccountType type, OrgUnit orgUnit) {
    BankAccount account = account(id, accountNo, orgUnit);
    account.setType(type);
    return account;
  }

  private static BankAccount specialAccount(UUID id, String accountNo, BankAccountStatus status) {
    BankAccount account = new BankAccount();
    account.setId(id);
    account.setAccountNo(accountNo);
    account.setName(accountNo + " special account");
    account.setType(BankAccountType.SPECIAL);
    account.setStatus(status);
    account.setOrgUnit(null);
    return account;
  }

  @Test
  void listOverseenOrgUnitBalances_returnsOnlyAccountsTheOfficerOversees() {
    UUID ownStaffelId = UUID.randomUUID();
    UUID ownAccountId = UUID.randomUUID();
    OrgUnit ownStaffel = squadron(ownStaffelId, "Own Staffel", "OWN");
    BankAccount ownAccount = account(ownAccountId, "KB-0001", ownStaffel);
    BankAccount foreignAccount =
        account(UUID.randomUUID(), "KB-0002", squadron(UUID.randomUUID(), "Foreign", "FRG"));
    when(ownerScopeService.currentOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of(ownStaffelId)));
    when(ownerScopeService.currentOwnLevelOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of(ownStaffelId)));
    when(bankAccountRepository.findAllByOrderByAccountNoAsc())
        .thenReturn(List.of(ownAccount, foreignAccount));
    when(bankPostingRepository.accountBalances(List.of(ownAccountId)))
        .thenReturn(List.of(new BankAccountBalance(ownAccountId, new BigDecimal("12345"))));

    List<OrgUnitBankBalanceDto> result = service.listOverseenOrgUnitBalances();

    assertThat(result).hasSize(1);
    OrgUnitBankBalanceDto dto = result.getFirst();
    assertThat(dto.accountId()).isEqualTo(ownAccountId);
    assertThat(dto.orgUnitId()).isEqualTo(ownStaffelId);
    assertThat(dto.balance()).isEqualByComparingTo("12345");
    assertThat(dto.canRequest()).isTrue();
  }

  @Test
  void listOverseenOrgUnitBalances_emptyForCallerWithoutOversight() {
    when(ownerScopeService.currentOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of()));
    when(authHelperService.isMemberOrAbove()).thenReturn(true);
    when(bankAccountRepository.findAllByOrderByAccountNoAsc())
        .thenReturn(
            List.of(account(UUID.randomUUID(), "KB-0001", squadron(UUID.randomUUID(), "S", "S"))));

    List<OrgUnitBankBalanceDto> result = service.listOverseenOrgUnitBalances();

    assertThat(result).isEmpty();
    verify(bankPostingRepository, never()).accountBalances(anyCollection());
  }

  @Test
  void listOverseenOrgUnitBalances_adminSeesEveryOrgUnitAccountAndZeroWhenNoPostings() {
    when(authHelperService.isAdmin()).thenReturn(true);
    UUID accountAId = UUID.randomUUID();
    UUID accountBId = UUID.randomUUID();
    BankAccount accountA = account(accountAId, "KB-0001", squadron(UUID.randomUUID(), "A", "AAA"));
    BankAccount accountB = account(accountBId, "KB-0002", squadron(UUID.randomUUID(), "B", "BBB"));
    when(ownerScopeService.currentOversightScope())
        .thenReturn(new ScopePredicate(true, null, Set.of()));
    when(ownerScopeService.currentOwnLevelOversightScope())
        .thenReturn(new ScopePredicate(true, null, Set.of()));
    when(bankAccountRepository.findAllByOrderByAccountNoAsc())
        .thenReturn(List.of(accountA, accountB));
    when(bankPostingRepository.accountBalances(List.of(accountAId, accountBId)))
        .thenReturn(List.of(new BankAccountBalance(accountAId, new BigDecimal("500"))));

    List<OrgUnitBankBalanceDto> result = service.listOverseenOrgUnitBalances();

    assertThat(result)
        .extracting(OrgUnitBankBalanceDto::accountId)
        .containsExactlyInAnyOrder(accountAId, accountBId);
    assertThat(result)
        .filteredOn(dto -> dto.accountId().equals(accountBId))
        .singleElement()
        .satisfies(dto -> assertThat(dto.balance()).isEqualByComparingTo("0"));
  }

  @Test
  void listOverseenOrgUnitBalances_holderGrantedMembershipRole_makesAccountVisible() {
    // REQ-BANK-035: a member with no oversight still sees an account where the holder granted their
    // membership role on the owning unit.
    UUID staffelId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    OrgUnit staffel = squadron(staffelId, "Own", "OWN");
    BankAccount account = account(accountId, "KB-0001", staffel);
    BankAccountViewGrant grant = new BankAccountViewGrant();
    grant.setAccount(account);
    grant.setGranteeKind(BankAccountViewGranteeKind.MEMBERSHIP_ROLE);
    grant.setRoleCode(MembershipRole.ENSIGN.name());
    when(ownerScopeService.currentOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of()));
    when(ownerScopeService.currentOwnLevelOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of()));
    when(viewGrantRepository.findByAccountIdIn(anyCollection())).thenReturn(List.of(grant));
    when(ownerScopeService.currentUserHoldsRoleOnOrgUnit(staffelId, MembershipRole.ENSIGN))
        .thenReturn(true);
    when(bankAccountRepository.findAllByOrderByAccountNoAsc()).thenReturn(List.of(account));
    when(bankPostingRepository.accountBalances(List.of(accountId)))
        .thenReturn(List.of(new BankAccountBalance(accountId, new BigDecimal("7"))));

    List<OrgUnitBankBalanceDto> result = service.listOverseenOrgUnitBalances();

    assertThat(result).extracting(OrgUnitBankBalanceDto::accountId).containsExactly(accountId);
    assertThat(result.getFirst().canRequest()).isFalse();
  }

  @Test
  void listOverseenOrgUnitBalances_cartelAccountVisibleToAnyMember() {
    // REQ-BANK-037: the CARTEL/KRT account is always visible to every KRT member.
    UUID olId = UUID.randomUUID();
    UUID cartelId = UUID.randomUUID();
    Organisationsleitung ol = new Organisationsleitung();
    ol.setId(olId);
    ol.setName("OL");
    ol.setShorthand("OL");
    BankAccount cartel = typedAccount(cartelId, "KB-0001", BankAccountType.CARTEL, ol);
    when(ownerScopeService.currentOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of()));
    when(ownerScopeService.currentOwnLevelOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of()));
    when(authHelperService.isMemberOrAbove()).thenReturn(true);
    when(bankAccountRepository.findAllByOrderByAccountNoAsc()).thenReturn(List.of(cartel));
    when(bankPostingRepository.accountBalances(List.of(cartelId)))
        .thenReturn(List.of(new BankAccountBalance(cartelId, new BigDecimal("42"))));

    List<OrgUnitBankBalanceDto> result = service.listOverseenOrgUnitBalances();

    assertThat(result).extracting(OrgUnitBankBalanceDto::accountId).containsExactly(cartelId);
  }

  @Test
  void listOverseenOrgUnitBalances_cartelBankVisibleOnlyToProfitBereichsleiter() {
    // REQ-BANK-037: CARTEL_BANK is held by the Profit-Bereichsleiter; a non-holder does not see it.
    UUID profitBereichId = UUID.randomUUID();
    UUID cartelBankId = UUID.randomUUID();
    Bereich profit = new Bereich();
    profit.setId(profitBereichId);
    profit.setName("Profit");
    BankAccount cartelBank =
        typedAccount(cartelBankId, "KB-0001", BankAccountType.CARTEL_BANK, null);
    when(ownerScopeService.currentOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of()));
    when(ownerScopeService.currentOwnLevelOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of()));
    when(bereichRepository.findByDepartment(any())).thenReturn(List.of(profit));
    when(ownerScopeService.currentUserHoldsRoleOnOrgUnit(
            profitBereichId, MembershipRole.BEREICHSLEITER))
        .thenReturn(true);
    when(bankAccountRepository.findAllByOrderByAccountNoAsc()).thenReturn(List.of(cartelBank));
    when(bankPostingRepository.accountBalances(List.of(cartelBankId)))
        .thenReturn(List.of(new BankAccountBalance(cartelBankId, new BigDecimal("9"))));

    List<OrgUnitBankBalanceDto> result = service.listOverseenOrgUnitBalances();

    assertThat(result).extracting(OrgUnitBankBalanceDto::accountId).containsExactly(cartelBankId);
  }

  @Test
  void listOverseenOrgUnitBalances_specialAccountsSeenByBereichsleiterNotByOfficer() {
    // REQ-BANK-037 (tightened REQ-BANK-028): Sonderkonten auto-visible to OL members and
    // Bereichsleiter; an officer (squadron rank only) does not see them.
    UUID specialId = UUID.randomUUID();
    BankAccount special = specialAccount(specialId, "KB-0001", BankAccountStatus.ACTIVE);
    when(ownerScopeService.currentOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of()));
    when(ownerScopeService.currentOwnLevelOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of()));
    when(ownerScopeService.currentUserIsBereichsleiter()).thenReturn(true);
    when(bankAccountRepository.findAllByOrderByAccountNoAsc()).thenReturn(List.of(special));
    when(bankPostingRepository.accountBalances(List.of(specialId)))
        .thenReturn(List.of(new BankAccountBalance(specialId, new BigDecimal("999"))));

    List<OrgUnitBankBalanceDto> result = service.listOverseenOrgUnitBalances();

    assertThat(result).extracting(OrgUnitBankBalanceDto::accountId).containsExactly(specialId);
    assertThat(result.getFirst().canRequest()).isFalse();
    assertThat(result.getFirst().orgUnitId()).isNull();
  }

  @Test
  void listOverseenOrgUnitBalances_specialAccountsHiddenFromOfficer() {
    UUID staffelId = UUID.randomUUID();
    UUID ownAccountId = UUID.randomUUID();
    BankAccount ownAccount = account(ownAccountId, "KB-0001", squadron(staffelId, "Own", "OWN"));
    BankAccount special = specialAccount(UUID.randomUUID(), "KB-0002", BankAccountStatus.ACTIVE);
    when(ownerScopeService.currentOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of(staffelId)));
    when(ownerScopeService.currentOwnLevelOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of(staffelId)));
    when(ownerScopeService.currentUserIsOlMember()).thenReturn(false);
    when(ownerScopeService.currentUserIsBereichsleiter()).thenReturn(false);
    when(bankAccountRepository.findAllByOrderByAccountNoAsc())
        .thenReturn(List.of(ownAccount, special));
    when(bankPostingRepository.accountBalances(List.of(ownAccountId)))
        .thenReturn(List.of(new BankAccountBalance(ownAccountId, new BigDecimal("10"))));

    List<OrgUnitBankBalanceDto> result = service.listOverseenOrgUnitBalances();

    assertThat(result).extracting(OrgUnitBankBalanceDto::accountId).containsExactly(ownAccountId);
  }

  @Test
  void getViewableAccountBookings_redactsHolderHandles() {
    // REQ-BANK-038: the player-custody columns are nulled before they cross the wire.
    UUID staffelId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    BankAccount account = account(accountId, "KB-0001", squadron(staffelId, "Own", "OWN"));
    when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(ownerScopeService.currentOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of(staffelId)));
    BankBookingDto raw =
        new BankBookingDto(
            UUID.randomUUID(),
            UUID.randomUUID(),
            BankTransactionType.TRANSFER,
            new BigDecimal("-100"),
            "greluc",
            "note",
            Instant.now(),
            null,
            "KB-0002",
            "Other",
            "counterHolder",
            false,
            BigDecimal.ZERO);
    when(bankAccountService.getBookings(eq(accountId), any()))
        .thenReturn(new PageImpl<>(List.of(raw)));

    var page = service.getViewableAccountBookings(accountId, PageRequest.of(0, 20));

    BankBookingDto redacted = page.getContent().getFirst();
    assertThat(redacted.holderHandle()).isNull();
    assertThat(redacted.counterHolderHandle()).isNull();
    assertThat(redacted.counterAccountNo()).isEqualTo("KB-0002");
    assertThat(redacted.amount()).isEqualByComparingTo("-100");
  }

  @Test
  void getViewableAccountBookings_deniedWhenCallerMayNotView() {
    UUID accountId = UUID.randomUUID();
    BankAccount account = account(accountId, "KB-0001", squadron(UUID.randomUUID(), "Own", "OWN"));
    when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(ownerScopeService.currentOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of()));

    assertThrows(
        AccessDeniedException.class,
        () -> service.getViewableAccountBookings(accountId, PageRequest.of(0, 20)));
    verifyNoInteractions(bankStatementReportService);
  }

  @Test
  void getViewableAccountDetail_deniedWhenCallerMayNotView() {
    // The backend endpoint is only isAuthenticated()-gated (it diverges from the page controller's
    // MEMBER_OR_ABOVE gate), so a non-member — e.g. a GUEST hitting GET
    // /api/v1/org-units/bank/accounts/{id} directly — must be stopped by the seam's canView guard
    // before any account detail is loaded.
    UUID accountId = UUID.randomUUID();
    BankAccount account = account(accountId, "KB-0001", squadron(UUID.randomUUID(), "Own", "OWN"));
    when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(ownerScopeService.currentOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of()));

    assertThrows(AccessDeniedException.class, () -> service.getViewableAccountDetail(accountId));
    verify(bankAccountService, never()).getAccountDetail(any(), any());
  }

  @Test
  void exportViewableStatement_authorizesAndPassesRedactionFlag() {
    // REQ-BANK-038: the seam authorizes view access, then asks for the Halter-redacted variant.
    UUID staffelId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    BankAccount account = account(accountId, "KB-0001", squadron(staffelId, "Own", "OWN"));
    Instant from = Instant.now().minusSeconds(3600);
    Instant to = Instant.now();
    when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(ownerScopeService.currentOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of(staffelId)));
    when(bankStatementReportService.generateStatement(
            eq(accountId), eq(from), eq(to), any(), eq(true)))
        .thenReturn(new byte[] {1, 2, 3});

    byte[] pdf = service.exportViewableStatement(accountId, from, to, null);

    assertThat(pdf).hasSize(3);
    verify(bankStatementReportService)
        .generateStatement(eq(accountId), eq(from), eq(to), any(), eq(true));
  }

  @Test
  void setBalanceTarget_byResponsibleHolder_savesAndAudits() {
    UUID staffelId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    BankAccount account = account(accountId, "KB-0001", squadron(staffelId, "Own", "OWN"));
    account.setVersion(3L);
    when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(ownerScopeService.currentUserHoldsRoleOnOrgUnit(staffelId, MembershipRole.STAFFELLEITER))
        .thenReturn(true);
    when(bankAccountRepository.saveAndFlush(account)).thenReturn(account);

    var settings = service.setBalanceTarget(accountId, new BigDecimal("10000"), 3L);

    assertThat(settings.balanceTarget()).isEqualByComparingTo("10000");
    assertThat(account.getBalanceTarget()).isEqualByComparingTo("10000");
    verify(bankAuditService)
        .record(eq(BankAuditEventType.BALANCE_TARGET_SET), eq(accountId), any(), any(), any());
  }

  @Test
  void setBalanceTarget_byNonHolder_throwsAccessDenied() {
    UUID staffelId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    BankAccount account = account(accountId, "KB-0001", squadron(staffelId, "Own", "OWN"));
    account.setVersion(1L);
    when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(ownerScopeService.currentUserHoldsRoleOnOrgUnit(staffelId, MembershipRole.STAFFELLEITER))
        .thenReturn(false);

    assertThrows(
        AccessDeniedException.class,
        () -> service.setBalanceTarget(accountId, new BigDecimal("10000"), 1L));
    verify(bankAccountRepository, never()).saveAndFlush(any());
  }

  @Test
  void setBalanceTarget_versionMismatch_throwsOptimisticLock() {
    UUID staffelId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    BankAccount account = account(accountId, "KB-0001", squadron(staffelId, "Own", "OWN"));
    account.setVersion(5L);
    when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(ownerScopeService.currentUserHoldsRoleOnOrgUnit(staffelId, MembershipRole.STAFFELLEITER))
        .thenReturn(true);

    assertThrows(
        ObjectOptimisticLockingFailureException.class,
        () -> service.setBalanceTarget(accountId, new BigDecimal("1"), 4L));
  }

  @Test
  void admin_maySetTargetAndConfigureVisibilityOnAnyAccountWithoutBeingHolder() {
    // The admin override: an admin who is not the responsible holder may still set the balance
    // target and configure the visibility of a Staffel account.
    UUID staffelId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    BankAccount account = account(accountId, "KB-0001", squadron(staffelId, "Own", "OWN"));
    account.setVersion(1L);
    when(authHelperService.isAdmin()).thenReturn(true);
    when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(bankAccountRepository.saveAndFlush(account)).thenReturn(account);
    when(viewGrantRepository.existsByAccountIdAndGranteeKindAndRoleCode(
            accountId, BankAccountViewGranteeKind.MEMBERSHIP_ROLE, MembershipRole.ENSIGN.name()))
        .thenReturn(false);
    // The admin holds no STAFFELLEITER role on the unit.
    when(ownerScopeService.currentUserHoldsRoleOnOrgUnit(staffelId, MembershipRole.STAFFELLEITER))
        .thenReturn(false);

    service.setBalanceTarget(accountId, new BigDecimal("100"), 1L);
    service.addRoleVisibility(accountId, MembershipRole.ENSIGN.name());

    verify(bankAuditService)
        .record(eq(BankAuditEventType.BALANCE_TARGET_SET), eq(accountId), any(), any(), any());
    verify(viewGrantRepository).save(any(BankAccountViewGrant.class));
    verify(bankAuditService)
        .record(
            eq(BankAuditEventType.BALANCE_VISIBILITY_GRANTED), eq(accountId), any(), any(), any());
  }

  @Test
  void addRoleVisibility_byHolder_savesGrantAndAudits() {
    UUID staffelId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    BankAccount account = account(accountId, "KB-0001", squadron(staffelId, "Own", "OWN"));
    when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(ownerScopeService.currentUserHoldsRoleOnOrgUnit(staffelId, MembershipRole.STAFFELLEITER))
        .thenReturn(true);
    when(viewGrantRepository.existsByAccountIdAndGranteeKindAndRoleCode(
            accountId, BankAccountViewGranteeKind.MEMBERSHIP_ROLE, MembershipRole.ENSIGN.name()))
        .thenReturn(false);

    service.addRoleVisibility(accountId, MembershipRole.ENSIGN.name());

    verify(viewGrantRepository).save(any(BankAccountViewGrant.class));
    verify(bankAuditService)
        .record(
            eq(BankAuditEventType.BALANCE_VISIBILITY_GRANTED), eq(accountId), any(), any(), any());
  }

  @Test
  void addRoleVisibility_unknownBucket_throwsBadRequest() {
    UUID staffelId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    BankAccount account = account(accountId, "KB-0001", squadron(staffelId, "Own", "OWN"));
    when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(ownerScopeService.currentUserHoldsRoleOnOrgUnit(staffelId, MembershipRole.STAFFELLEITER))
        .thenReturn(true);

    // A squadron account has no BEREICHSKOORDINATOR bucket.
    assertThrows(
        de.greluc.krt.profit.basetool.backend.exception.BadRequestException.class,
        () -> service.addRoleVisibility(accountId, MembershipRole.BEREICHSKOORDINATOR.name()));
    verify(viewGrantRepository, never()).save(any());
  }

  @Test
  void createBookingRequest_withinOwnLevelScope_resolvesAccountAndDelegates() {
    UUID orgUnitId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    BankAccount account = account(accountId, "KB-0001", squadron(orgUnitId, "Own", "OWN"));
    CreateBankBookingRequest request =
        new CreateBankBookingRequest(
            orgUnitId, BankBookingRequestType.DEPOSIT, new BigDecimal("500"), "from sale");
    BankBookingRequestDto expected = requestDto(accountId, orgUnitId);
    when(ownerScopeService.currentOwnLevelOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of(orgUnitId)));
    when(bankAccountRepository.findByOrgUnitId(orgUnitId)).thenReturn(Optional.of(account));
    when(bankBookingRequestService.create(
            eq(accountId), eq(BankBookingRequestType.DEPOSIT), eq(new BigDecimal("500")), any()))
        .thenReturn(expected);

    assertThat(service.createBookingRequest(request)).isSameAs(expected);
  }

  @Test
  void createBookingRequest_outsideOwnLevelScope_throwsAccessDenied() {
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

  private static BankBookingRequestDto requestDto(UUID accountId, UUID orgUnitId) {
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

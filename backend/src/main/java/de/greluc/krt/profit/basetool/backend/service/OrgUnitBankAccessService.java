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

import de.greluc.krt.profit.basetool.backend.exception.NotFoundException;
import de.greluc.krt.profit.basetool.backend.model.BankAccount;
import de.greluc.krt.profit.basetool.backend.model.BankAccountStatus;
import de.greluc.krt.profit.basetool.backend.model.BankAccountType;
import de.greluc.krt.profit.basetool.backend.model.OrgUnit;
import de.greluc.krt.profit.basetool.backend.model.dto.BankBookingRequestDto;
import de.greluc.krt.profit.basetool.backend.model.dto.OrgUnitBankBalanceDto;
import de.greluc.krt.profit.basetool.backend.model.dto.request.CreateBankBookingRequest;
import de.greluc.krt.profit.basetool.backend.model.projection.BankAccountBalance;
import de.greluc.krt.profit.basetool.backend.model.projection.BankPostingSlice;
import de.greluc.krt.profit.basetool.backend.repository.BankAccountRepository;
import de.greluc.krt.profit.basetool.backend.repository.BankPostingRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The org-unit-aware bridge between the bank and the org-unit oversight scope (ADR-0020).
 *
 * <p>The bank's own authorization ({@code BankSecurityService}) is, by construction, blind to
 * org-unit membership — it decides visibility solely from the bank roles and {@code
 * bank_account_grant} rows (REQ-BANK-008, ADR-0011), and the {@code
 * bankClassesMustNotConsultOrgUnitScope} ArchUnit rule pins that by forbidding every {@code
 * Bank*}-named class from depending on {@link OwnerScopeService}. The two org-unit features
 * (officer/lead balance view F1, confirm-before-post booking requests F2) need exactly the opposite
 * input: who oversees which org unit. This class is the single, deliberately non-{@code
 * Bank*}-named seam that carries that org-unit logic, so it may inject {@link OwnerScopeService}
 * without weakening the bank's independence — the {@code BankSecurityService} stays 100%
 * org-unit-blind.
 *
 * <p>Two oversight scopes feed the two features, split since epic #692 Phase 6 (owner decision Q4):
 *
 * <ul>
 *   <li><b>F1 view — cascading</b> ({@link OwnerScopeService#currentOversightScope()}): an officer
 *       oversees their own Staffel; an SK lead the SK(s) they lead; a Bereichsleitung its Bereich's
 *       AREA account <em>and</em> every child Staffel/SK account; an OL member the CARTEL account
 *       plus every AREA/ORG_UNIT account; an admin all (or the pinned one). A plain member resolves
 *       to an empty scope and sees nothing. On top of that, a Bereich/OL overseer (or admin)
 *       additionally sees the cartel-wide <b>special accounts</b> (Sonderkonten, REQ-BANK-028),
 *       view-only, since those belong to no org unit. Only active accounts are listed.
 *   <li><b>F2 request — own-level only</b> ({@link
 *       OwnerScopeService#currentOwnLevelOversightScope()}): the same callers may raise a
 *       confirm-before-post request only against their <em>own-level</em> account (officer →
 *       Staffel, Bereichsleitung → AREA, OL → CARTEL); subordinate accounts reached by the F1
 *       drill-down are view-only.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrgUnitBankAccessService {

  private final OwnerScopeService ownerScopeService;
  private final BankAccountRepository bankAccountRepository;
  private final BankPostingRepository bankPostingRepository;
  private final BankBookingRequestService bankBookingRequestService;

  /**
   * Lists the current balance of every bank account the caller may view on the org-unit bank page
   * (REQ-BANK-021/-027/-028, F1).
   *
   * <p>Two visibility rules combine into the listed set, and only {@link BankAccountStatus#ACTIVE}
   * accounts are ever included — closed accounts are filtered out so the page shows live accounts
   * only (REQ-BANK-028):
   *
   * <ul>
   *   <li><b>Org-unit accounts by the cascading oversight scope</b> ({@link
   *       OwnerScopeService#currentOversightScope()}): keeps the accounts whose owning org unit the
   *       scope permits. Since epic #692 Phase 6 (REQ-ORG-019) the owning org unit is the uniform
   *       {@code org_unit_id} FK across all linked account kinds — a Staffel/SK for {@code
   *       ORG_UNIT}, the Bereich for {@code AREA}, the Organisationsleitung for {@code CARTEL} — so
   *       a Bereichsleitung sees its AREA account <em>and</em> every child Staffel/SK account, and
   *       an OL member sees the CARTEL account plus every AREA and ORG_UNIT account, all through
   *       the same filter with no per-kind branching. Strict silo holds: the cascade only
   *       contributes the caller's own subtree, so a foreign Bereich's accounts are never matched.
   *       Legacy {@code area_name}-only AREA accounts (no FK) carry no owning org unit and are
   *       excluded.
   *   <li><b>Cartel-wide special accounts (Sonderkonten) for Bereich/OL overseers</b>
   *       (REQ-BANK-028): a caller who holds a Bereich- or OL-level oversight seat (or an admin)
   *       additionally sees every {@code SPECIAL} account — these belong to no org unit, so they
   *       are added by type, not by scope. They are strictly <b>view-only</b>: {@code canRequest}
   *       is always {@code false} and the F2 create path rejects them (no owning org unit to
   *       scope). Officers and SK leads (no Bereich/OL seat) do not see special accounts.
   * </ul>
   *
   * <p>The balance is the compute-on-read posting sum (ADR-0010), fetched for all matched accounts
   * in a single grouped query (no N+1). Each card also carries the same 30-day trend the bank
   * dashboard shows (REQ-BANK-016) — the signed delta and the daily end-of-day sparkline series,
   * derived via {@link BankTrendCalculator} from one additional windowed posting-slice query (again
   * no per-account N+1). It still exposes no transaction history, no holder distribution and no
   * audit — those stay a bank-staff surface. A caller who can see nothing (plain member) receives
   * an empty list rather than an error, so the endpoint never leaks the existence of accounts the
   * caller may not see.
   *
   * @return the visible account balances, ordered by account number; never {@code null}, empty when
   *     the caller may view no active account
   */
  @NotNull
  @Transactional(readOnly = true)
  public List<OrgUnitBankBalanceDto> listOverseenOrgUnitBalances() {
    var scope = ownerScopeService.currentOversightScope();
    boolean canViewSpecialAccounts = ownerScopeService.currentUserHasAreaOrOlOversight();
    List<BankAccount> visible =
        bankAccountRepository.findAllByOrderByAccountNoAsc().stream()
            .filter(account -> account.getStatus() == BankAccountStatus.ACTIVE)
            .filter(
                account ->
                    isOverseenOrgUnitAccount(account, scope)
                        || (canViewSpecialAccounts && account.getType() == BankAccountType.SPECIAL))
            .toList();
    if (visible.isEmpty()) {
      return List.of();
    }
    // The own-level (non-cascading) scope decides which of the visible accounts the caller may also
    // raise a booking request against (F2, owner decision Q4): their own-level account is
    // requestable
    // while subordinate accounts reached by the cascade and the view-only special accounts are not.
    var requestScope = ownerScopeService.currentOwnLevelOversightScope();
    Map<UUID, BigDecimal> balances = balancesByAccountId(visible);
    Map<UUID, List<BankPostingSlice>> slicesByAccount = slicesByAccountId(visible);
    return visible.stream()
        .map(
            account -> {
              BigDecimal balance = balances.getOrDefault(account.getId(), BigDecimal.ZERO);
              List<BankPostingSlice> slices =
                  slicesByAccount.getOrDefault(account.getId(), List.of());
              BigDecimal delta = BankTrendCalculator.windowDelta(slices);
              boolean canRequest =
                  account.getOrgUnit() != null
                      && requestScope.permits(account.getOrgUnit().getId());
              return toDto(
                  account,
                  balance,
                  canRequest,
                  delta,
                  BankTrendCalculator.sparkline(balance, delta, slices));
            })
        .toList();
  }

  /**
   * {@code true} iff the account is an org-unit account whose owning org unit the caller's
   * cascading oversight scope permits. A special account (no owning org unit) is never matched here
   * — those are admitted separately, by type, for Bereich/OL overseers (REQ-BANK-028).
   *
   * @param account the candidate account
   * @param scope the caller's cascading oversight scope
   * @return {@code true} iff the account has an owning org unit the scope permits
   */
  private static boolean isOverseenOrgUnitAccount(
      @NotNull BankAccount account, @NotNull ScopePredicate scope) {
    return account.getOrgUnit() != null && scope.permits(account.getOrgUnit().getId());
  }

  /**
   * Raises a confirm-before-post booking request for the caller's <b>own-level</b> org unit
   * (REQ-BANK-022, F2). Enforces the org-unit half of the authorization here — the caller must hold
   * the named org unit as an own-level oversight seat (officer of their Staffel / lead of an SK /
   * Bereichsleitung of their Bereich → its AREA account / OL member → the CARTEL account / admin) —
   * then resolves the org unit to its bank account and delegates the actual persistence to the
   * org-unit-blind {@link BankBookingRequestService}. The scope check runs <em>before</em> the
   * account lookup so an out-of-scope org unit never even reveals whether it owns an account.
   *
   * <p>Crucially this uses the <em>own-level</em> scope ({@link
   * OwnerScopeService#currentOwnLevelOversightScope()}), NOT the cascading view scope of {@link
   * #listOverseenOrgUnitBalances()}: a Bereichsleitung/OL may <em>view</em> every subordinate
   * account but may raise a request only against their own-level account (owner decision Q4). A
   * request targeting a subordinate account is therefore rejected as out-of-scope, and the
   * epic-#666 officer flow (request against the officer's own Staffel) is unchanged.
   *
   * @param request the create payload (org unit, type, amount, note)
   * @return the created pending request
   * @throws AccessDeniedException when the caller does not hold the named org unit at their own
   *     level
   * @throws NotFoundException when the org unit owns no bank account
   */
  @NotNull
  @Transactional
  public BankBookingRequestDto createBookingRequest(@NotNull CreateBankBookingRequest request) {
    var scope = ownerScopeService.currentOwnLevelOversightScope();
    if (!scope.permits(request.orgUnitId())) {
      throw new AccessDeniedException(
          "The caller may not raise a booking request for the requested org unit");
    }
    BankAccount account =
        bankAccountRepository
            .findByOrgUnitId(request.orgUnitId())
            .orElseThrow(() -> new NotFoundException("The org unit has no bank account"));
    return bankBookingRequestService.create(
        account.getId(), request.type(), request.amount(), request.note());
  }

  /**
   * Lists the caller's own booking requests (REQ-BANK-022). Per-user isolation lives in the
   * delegate; this method adds no org-unit scope (a requester always sees their own requests
   * regardless of current oversight).
   *
   * @return the caller's requests, newest first
   */
  @NotNull
  public List<BankBookingRequestDto> listOwnBookingRequests() {
    return bankBookingRequestService.listForCurrentRequester();
  }

  /**
   * Cancels the caller's own pending booking request (REQ-BANK-022). The delegate enforces that the
   * request belongs to the caller and is still pending.
   *
   * @param requestId the request to cancel
   * @param version the echoed optimistic-locking version
   * @return the cancelled request
   */
  @NotNull
  public BankBookingRequestDto cancelOwnBookingRequest(@NotNull UUID requestId, long version) {
    return bankBookingRequestService.cancelOwn(requestId, version);
  }

  /**
   * Batch-computes the balances of the given accounts in a single grouped query (REQ-DATA-003) and
   * indexes them by account id. Accounts without postings produce no row and are treated as zero by
   * the caller.
   *
   * @param accounts the accounts to sum
   * @return a map of account id to balance for accounts that have at least one posting
   */
  @NotNull
  private Map<UUID, BigDecimal> balancesByAccountId(@NotNull List<BankAccount> accounts) {
    List<UUID> ids = accounts.stream().map(BankAccount::getId).toList();
    Map<UUID, BigDecimal> byAccount = new HashMap<>();
    for (BankAccountBalance row : bankPostingRepository.accountBalances(ids)) {
      byAccount.put(row.accountId(), row.balance());
    }
    return byAccount;
  }

  /**
   * Fetches the last 30 days of posting slices for the given accounts in a single windowed query
   * (REQ-DATA-003, mirroring the dashboard's no-N+1 read) and groups them by account id, so the
   * 30-day delta and sparkline series can be derived per account in memory (REQ-BANK-016). Accounts
   * with no postings in the window produce no entry and are treated as an empty list by the caller.
   *
   * @param accounts the visible accounts
   * @return a map of account id to its in-window posting slices (only accounts with slices appear)
   */
  @NotNull
  private Map<UUID, List<BankPostingSlice>> slicesByAccountId(@NotNull List<BankAccount> accounts) {
    List<UUID> ids = accounts.stream().map(BankAccount::getId).toList();
    return bankPostingRepository
        .postingSlicesSince(ids, BankTrendCalculator.windowCutoff())
        .stream()
        .collect(Collectors.groupingBy(BankPostingSlice::accountId));
  }

  /**
   * Projects one visible account and its resolved balance into the balance-only wire shape. Handles
   * both flavours: an org-unit account (its {@code orgUnit} is non-null, the fields are populated)
   * and a special account (Sonderkonto, REQ-BANK-028) whose {@code orgUnit} is {@code null}, in
   * which case the org-unit fields are emitted as {@code null} and the frontend labels the card by
   * {@link BankAccount#getType()}.
   *
   * @param account the visible account
   * @param balance the account's resolved balance, zero when it has no postings
   * @param canRequest {@code true} iff the account is the caller's own-level org-unit account (the
   *     F2 request affordance applies); {@code false} for a view-only subordinate or special
   *     account
   * @param delta30d the net balance change over the last 30 days (signed)
   * @param sparkline the 30 end-of-day balances of the window, oldest first (last entry = balance)
   * @return the balance-only DTO for the account, carrying the 30-day trend figures (REQ-BANK-016)
   */
  @NotNull
  private OrgUnitBankBalanceDto toDto(
      @NotNull BankAccount account,
      @NotNull BigDecimal balance,
      boolean canRequest,
      @NotNull BigDecimal delta30d,
      @NotNull List<BigDecimal> sparkline) {
    OrgUnit orgUnit = account.getOrgUnit();
    return new OrgUnitBankBalanceDto(
        account.getId(),
        account.getAccountNo(),
        account.getName(),
        account.getStatus(),
        account.getType(),
        orgUnit == null ? null : orgUnit.getId(),
        orgUnit == null ? null : orgUnit.getName(),
        orgUnit == null ? null : orgUnit.getShorthand(),
        orgUnit == null ? null : orgUnit.getKind(),
        balance,
        canRequest,
        delta30d,
        sparkline);
  }
}

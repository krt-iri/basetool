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

import de.greluc.krt.profit.basetool.backend.exception.BadRequestException;
import de.greluc.krt.profit.basetool.backend.exception.NotFoundException;
import de.greluc.krt.profit.basetool.backend.model.BankAccount;
import de.greluc.krt.profit.basetool.backend.model.BankAccountStatus;
import de.greluc.krt.profit.basetool.backend.model.BankAccountType;
import de.greluc.krt.profit.basetool.backend.model.BankAccountViewGrant;
import de.greluc.krt.profit.basetool.backend.model.BankAccountViewGranteeKind;
import de.greluc.krt.profit.basetool.backend.model.BankAuditEventType;
import de.greluc.krt.profit.basetool.backend.model.Bereich;
import de.greluc.krt.profit.basetool.backend.model.Department;
import de.greluc.krt.profit.basetool.backend.model.MembershipRole;
import de.greluc.krt.profit.basetool.backend.model.OrgUnit;
import de.greluc.krt.profit.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.profit.basetool.backend.model.User;
import de.greluc.krt.profit.basetool.backend.model.dto.BankAccountDetailDto;
import de.greluc.krt.profit.basetool.backend.model.dto.BankBookingDto;
import de.greluc.krt.profit.basetool.backend.model.dto.BankBookingRequestDto;
import de.greluc.krt.profit.basetool.backend.model.dto.BankCapabilitiesDto;
import de.greluc.krt.profit.basetool.backend.model.dto.OrgUnitBankAccountDetailDto;
import de.greluc.krt.profit.basetool.backend.model.dto.OrgUnitBankAccountSettingsDto;
import de.greluc.krt.profit.basetool.backend.model.dto.OrgUnitBankBalanceDto;
import de.greluc.krt.profit.basetool.backend.model.dto.OrgUnitBankViewUserDto;
import de.greluc.krt.profit.basetool.backend.model.dto.request.CreateBankBookingRequest;
import de.greluc.krt.profit.basetool.backend.model.projection.BankAccountBalance;
import de.greluc.krt.profit.basetool.backend.model.projection.BankPostingSlice;
import de.greluc.krt.profit.basetool.backend.repository.BankAccountRepository;
import de.greluc.krt.profit.basetool.backend.repository.BankAccountViewGrantRepository;
import de.greluc.krt.profit.basetool.backend.repository.BankPostingRepository;
import de.greluc.krt.profit.basetool.backend.repository.BereichRepository;
import de.greluc.krt.profit.basetool.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The org-unit-aware bridge between the bank and the org-unit oversight scope (ADR-0020/0028/0043).
 *
 * <p>The bank's own authorization ({@code BankSecurityService}) is, by construction, blind to
 * org-unit membership — it decides visibility solely from the bank roles and {@code
 * bank_account_grant} rows (REQ-BANK-008, ADR-0011), and the {@code
 * bankClassesMustNotConsultOrgUnitScope} ArchUnit rule pins that by forbidding every {@code
 * Bank*}-named class from depending on {@link OwnerScopeService}. The org-unit features need
 * exactly the opposite input: who oversees / is responsible for which org unit. This class is the
 * single, deliberately non-{@code Bank*}-named seam that carries that org-unit logic; it authorizes
 * here and then reuses the bank's own org-unit-blind read/PDF code ({@link BankAccountService},
 * {@link BankStatementReportService}), so the bank stays 100% org-unit-blind.
 *
 * <p>It serves four things on the org-unit bank page:
 *
 * <ul>
 *   <li><b>The card list</b> (F1, REQ-BANK-021/-027/-028): the balance — and, since REQ-BANK-036,
 *       the balance target — of every account the caller may view ({@link #canView}).
 *   <li><b>The read-only drill-in</b> (REQ-BANK-038): the account detail + history + a
 *       Halter-redacted Kontoauszug for any account the caller may view; no booking actions.
 *   <li><b>Booking requests</b> (F2, REQ-BANK-022): own-level only.
 *   <li><b>The responsibility settings</b> (REQ-BANK-035/-036): the derived responsible holder
 *       (and, for Sonderkonten, the OL) configures who else may view the account and sets the
 *       balance target.
 * </ul>
 *
 * <p><b>Who may view an account</b> ({@link #canView}) — admins see all; otherwise by type:
 *
 * <ul>
 *   <li>{@code ORG_UNIT}/{@code AREA}: the cascading oversight scope ({@link
 *       OwnerScopeService#currentOversightScope()}) <em>or</em> a holder-configured view grant
 *       (membership-role bucket / all-members / individual user).
 *   <li>{@code CARTEL} (the KRT account): every KRT member (fixed, REQ-BANK-037), plus the OL via
 *       oversight.
 *   <li>{@code CARTEL_BANK}: only its responsible holder — the {@code BEREICHSLEITER} of a {@code
 *       Department.PROFIT} Bereich (bank staff use the bank surface).
 *   <li>{@code SPECIAL} (Sonderkonten, REQ-BANK-037): every OL member and every {@code
 *       BEREICHSLEITER} automatically, plus OL-configured grants (global-role / all-members /
 *       individual user). Bereichskoordinatoren/-operatoren and officers do <em>not</em> see them.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrgUnitBankAccessService {

  /**
   * Read-only capabilities handed to org-unit viewers — no deposit/withdraw/transfer (management).
   */
  private static final BankCapabilitiesDto READ_ONLY_CAPABILITIES =
      new BankCapabilitiesDto(false, false, false, false);

  /**
   * Visibility role buckets a Staffel account's Staffelleiter may toggle (MembershipRole names).
   */
  private static final List<String> SQUADRON_ROLE_BUCKETS =
      List.of(
          MembershipRole.KOMMANDOLEITER.name(),
          MembershipRole.STELLV_KOMMANDOLEITER.name(),
          MembershipRole.ENSIGN.name());

  /**
   * Visibility role buckets a Bereich account's Bereichsleiter may toggle (MembershipRole names).
   */
  private static final List<String> BEREICH_ROLE_BUCKETS =
      List.of(MembershipRole.BEREICHSKOORDINATOR.name(), MembershipRole.BEREICHSOPERATOR.name());

  /**
   * Global-role buckets the OL may toggle for a Sonderkonto (no owning unit ⇒ no membership ranks).
   * Stored without the {@code ROLE_} prefix; evaluated via {@code hasReachableRole("ROLE_" +
   * code)}.
   */
  private static final List<String> SPECIAL_GLOBAL_ROLE_BUCKETS =
      List.of("OFFICER", "LOGISTICIAN", "MISSION_MANAGER");

  private final OwnerScopeService ownerScopeService;
  private final AuthHelperService authHelperService;
  private final BankAccountRepository bankAccountRepository;
  private final BankPostingRepository bankPostingRepository;
  private final BankAccountViewGrantRepository viewGrantRepository;
  private final BereichRepository bereichRepository;
  private final UserRepository userRepository;
  private final BankAccountService bankAccountService;
  private final BankStatementReportService bankStatementReportService;
  private final BankBookingRequestService bankBookingRequestService;
  private final BankAuditService bankAuditService;

  // ---------------------------------------------------------------------------------------------
  // F1 — card list
  // ---------------------------------------------------------------------------------------------

  /**
   * Lists the balance of every active bank account the caller may view on the org-unit bank page
   * (REQ-BANK-021/-027/-028/-035/-037, F1). The visible set is decided by {@link #canView} per
   * account; the view grants of the candidate accounts are loaded in one batched query so the
   * filter adds no per-account N+1 (REQ-DATA-003). Each card also carries the 30-day trend
   * (REQ-BANK-016), the balance target (REQ-BANK-036) and whether the caller may open the account's
   * settings.
   *
   * @return the visible account balances, ordered by account number; never {@code null}, empty when
   *     the caller may view no active account
   */
  @NotNull
  @Transactional(readOnly = true)
  public List<OrgUnitBankBalanceDto> listOverseenOrgUnitBalances() {
    boolean admin = authHelperService.isAdmin();
    ScopePredicate viewScope = ownerScopeService.currentOversightScope();
    List<BankAccount> active =
        bankAccountRepository.findAllByOrderByAccountNoAsc().stream()
            .filter(account -> account.getStatus() == BankAccountStatus.ACTIVE)
            .toList();
    if (active.isEmpty()) {
      return List.of();
    }
    Map<UUID, List<BankAccountViewGrant>> grantsByAccount =
        viewGrantRepository
            .findByAccountIdIn(active.stream().map(BankAccount::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(grant -> grant.getAccount().getId()));
    List<BankAccount> visible =
        active.stream()
            .filter(
                account ->
                    admin
                        || canViewInternal(
                            account,
                            viewScope,
                            grantsByAccount.getOrDefault(account.getId(), List.of())))
            .toList();
    if (visible.isEmpty()) {
      return List.of();
    }
    ScopePredicate requestScope = ownerScopeService.currentOwnLevelOversightScope();
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
                  BankTrendCalculator.sparkline(balance, delta, slices),
                  canManageSettings(account));
            })
        .toList();
  }

  // ---------------------------------------------------------------------------------------------
  // Read-only drill-in (REQ-BANK-038)
  // ---------------------------------------------------------------------------------------------

  /**
   * Returns the read-only account detail for an account the caller may view (REQ-BANK-038). Reuses
   * the bank-staff detail aggregate ({@link BankAccountService#getAccountDetail}) but with
   * all-false capabilities, and adds the org-unit-side affordances (export statement, manage
   * settings, raise a request).
   *
   * @param accountId the account to open
   * @return the read-only detail
   * @throws NotFoundException when the account does not exist
   * @throws AccessDeniedException when the caller may not view the account
   */
  @NotNull
  @Transactional(readOnly = true)
  public OrgUnitBankAccountDetailDto getViewableAccountDetail(@NotNull UUID accountId) {
    BankAccount account = requireViewableAccount(accountId);
    BankAccountDetailDto detail =
        bankAccountService.getAccountDetail(accountId, READ_ONLY_CAPABILITIES);
    boolean canRequest =
        account.getOrgUnit() != null
            && ownerScopeService
                .currentOwnLevelOversightScope()
                .permits(account.getOrgUnit().getId());
    return new OrgUnitBankAccountDetailDto(
        detail, true, canSetTarget(account), canConfigureVisibility(account), canRequest);
  }

  /**
   * Returns one page of an account's booking history for an org-unit viewer (REQ-BANK-038), with
   * the player-custody ("Halter") columns <b>redacted</b> — {@code holderHandle} and {@code
   * counterHolderHandle} are nulled so custody never crosses the wire to org-unit viewers.
   *
   * @param accountId the account to read
   * @param pageable page, size and whitelisted sort
   * @return one page of redacted booking rows
   * @throws NotFoundException when the account does not exist
   * @throws AccessDeniedException when the caller may not view the account
   */
  @NotNull
  @Transactional(readOnly = true)
  public Page<BankBookingDto> getViewableAccountBookings(
      @NotNull UUID accountId, @NotNull Pageable pageable) {
    requireViewableAccount(accountId);
    return bankAccountService
        .getBookings(accountId, pageable)
        .map(OrgUnitBankAccessService::redact);
  }

  /**
   * Generates the Halter-redacted account statement PDF for an org-unit viewer (REQ-BANK-038). The
   * org-unit authorization is enforced here; the generation reuses {@link
   * BankStatementReportService#generateStatement(UUID, Instant, Instant, ZoneId, boolean)} with
   * redaction on, which also records the {@code STATEMENT_EXPORTED} audit event for the org-unit
   * actor.
   *
   * @param accountId the account
   * @param from period start (inclusive)
   * @param to period end (inclusive)
   * @param userZone the zone to render timestamps in, or {@code null} for UTC
   * @return the redacted PDF bytes
   * @throws NotFoundException when the account does not exist
   * @throws AccessDeniedException when the caller may not view the account
   */
  @NotNull
  @Transactional
  public byte[] exportViewableStatement(
      @NotNull UUID accountId,
      @NotNull Instant from,
      @NotNull Instant to,
      @Nullable ZoneId userZone) {
    requireViewableAccount(accountId);
    return bankStatementReportService.generateStatement(accountId, from, to, userZone, true);
  }

  // ---------------------------------------------------------------------------------------------
  // Settings — balance target + configurable visibility (REQ-BANK-035/-036)
  // ---------------------------------------------------------------------------------------------

  /**
   * Returns the responsibility settings of one account for its holder/OL settings panel
   * (REQ-BANK-035/-036): the current balance target, the configurable role/all-members/user grants,
   * and which controls the caller may use.
   *
   * @param accountId the account
   * @return the settings snapshot
   * @throws NotFoundException when the account does not exist
   * @throws AccessDeniedException when the caller may neither set the target nor configure
   *     visibility
   */
  @NotNull
  @Transactional(readOnly = true)
  public OrgUnitBankAccountSettingsDto getAccountSettings(@NotNull UUID accountId) {
    BankAccount account = requireAccount(accountId);
    if (!canSetTarget(account) && !canConfigureVisibility(account)) {
      throw new AccessDeniedException("The caller may not manage this account's settings");
    }
    return toSettingsDto(account);
  }

  /**
   * Sets, changes or clears an account's balance target (REQ-BANK-036). Only the responsible holder
   * may do this from the org-unit side; bank staff use the bank surface. A {@code null} target
   * clears the goal; a present target is a positive whole amount — enforced at the API boundary by
   * the {@code @Valid} {@code OrgUnitBalanceTargetRequest}'s
   * {@code @DecimalMin("1")}/{@code @WholeNumber} constraints (same gate the bank-surface path
   * relies on), so the service trusts the validated input rather than re-checking it. {@code
   * saveAndFlush} writes back the bumped {@code @Version} within the transaction so the form can
   * echo it next time (REQ-FE-003).
   *
   * @param accountId the account
   * @param target the new target, or {@code null} to clear it
   * @param version the echoed optimistic-locking version
   * @return the refreshed settings
   * @throws NotFoundException when the account does not exist
   * @throws AccessDeniedException when the caller may not set the target
   * @throws ObjectOptimisticLockingFailureException on a version mismatch (409)
   */
  @NotNull
  @Transactional
  public OrgUnitBankAccountSettingsDto setBalanceTarget(
      @NotNull UUID accountId, @Nullable BigDecimal target, long version) {
    BankAccount account = requireAccount(accountId);
    requireCanSetTarget(account);
    requireVersionMatch(account, version);
    account.setBalanceTarget(target);
    bankAccountRepository.saveAndFlush(account);
    bankAuditService.record(
        target == null
            ? BankAuditEventType.BALANCE_TARGET_CLEARED
            : BankAuditEventType.BALANCE_TARGET_SET,
        accountId,
        null,
        null,
        target == null ? null : "target=" + target.stripTrailingZeros().toPlainString());
    return toSettingsDto(account);
  }

  /**
   * Adds a role-bucket view grant to an account (REQ-BANK-035). The bucket kind is derived from the
   * account type — a {@code MembershipRole} on the owning unit for org-unit accounts, a global role
   * code for a Sonderkonto — and the role code is validated against the buckets that apply to this
   * account. Idempotent: granting an already-granted bucket is a no-op.
   *
   * @param accountId the account
   * @param roleCode the role bucket to grant (a {@code MembershipRole} name or a global role code)
   * @return the refreshed settings
   * @throws NotFoundException when the account does not exist
   * @throws AccessDeniedException when the caller may not configure visibility
   * @throws BadRequestException when the role code is not a valid bucket for this account
   */
  @NotNull
  @Transactional
  public OrgUnitBankAccountSettingsDto addRoleVisibility(
      @NotNull UUID accountId, @NotNull String roleCode) {
    BankAccount account = requireAccount(accountId);
    requireCanConfigureVisibility(account);
    BankAccountViewGranteeKind kind = requireValidRoleBucket(account, roleCode);
    if (!viewGrantRepository.existsByAccountIdAndGranteeKindAndRoleCode(
        accountId, kind, roleCode)) {
      BankAccountViewGrant grant = new BankAccountViewGrant();
      grant.setAccount(account);
      grant.setGranteeKind(kind);
      grant.setRoleCode(roleCode);
      viewGrantRepository.save(grant);
      bankAuditService.record(
          BankAuditEventType.BALANCE_VISIBILITY_GRANTED,
          accountId,
          null,
          null,
          kind.name() + ":" + roleCode);
    }
    return toSettingsDto(account);
  }

  /**
   * Removes a role-bucket view grant from an account (REQ-BANK-035).
   *
   * @param accountId the account
   * @param roleCode the role bucket to revoke
   * @return the refreshed settings
   * @throws NotFoundException when the account does not exist
   * @throws AccessDeniedException when the caller may not configure visibility
   * @throws BadRequestException when the role code is not a valid bucket for this account
   */
  @NotNull
  @Transactional
  public OrgUnitBankAccountSettingsDto removeRoleVisibility(
      @NotNull UUID accountId, @NotNull String roleCode) {
    BankAccount account = requireAccount(accountId);
    requireCanConfigureVisibility(account);
    BankAccountViewGranteeKind kind = requireValidRoleBucket(account, roleCode);
    long removed =
        viewGrantRepository.deleteByAccountIdAndGranteeKindAndRoleCode(accountId, kind, roleCode);
    if (removed > 0) {
      bankAuditService.record(
          BankAuditEventType.BALANCE_VISIBILITY_REVOKED,
          accountId,
          null,
          null,
          kind.name() + ":" + roleCode);
    }
    return toSettingsDto(account);
  }

  /**
   * Enables or disables the all-members view grant of an account (REQ-BANK-035): every member of
   * the owning unit (org-unit accounts) or every KRT member (Sonderkonten) may view it. Idempotent.
   *
   * @param accountId the account
   * @param enabled whether all members may view the account
   * @return the refreshed settings
   * @throws NotFoundException when the account does not exist
   * @throws AccessDeniedException when the caller may not configure visibility
   * @throws BadRequestException when the account type has no all-members bucket
   */
  @NotNull
  @Transactional
  public OrgUnitBankAccountSettingsDto setAllMembersVisibility(
      @NotNull UUID accountId, boolean enabled) {
    BankAccount account = requireAccount(accountId);
    requireCanConfigureVisibility(account);
    if (!allMembersSupported(account.getType())) {
      throw new BadRequestException("This account type has no all-members visibility bucket");
    }
    boolean exists =
        viewGrantRepository.existsByAccountIdAndGranteeKind(
            accountId, BankAccountViewGranteeKind.ALL_MEMBERS);
    if (enabled && !exists) {
      BankAccountViewGrant grant = new BankAccountViewGrant();
      grant.setAccount(account);
      grant.setGranteeKind(BankAccountViewGranteeKind.ALL_MEMBERS);
      viewGrantRepository.save(grant);
      bankAuditService.record(
          BankAuditEventType.BALANCE_VISIBILITY_GRANTED, accountId, null, null, "ALL_MEMBERS");
    } else if (!enabled && exists) {
      viewGrantRepository.deleteByAccountIdAndGranteeKind(
          accountId, BankAccountViewGranteeKind.ALL_MEMBERS);
      bankAuditService.record(
          BankAuditEventType.BALANCE_VISIBILITY_REVOKED, accountId, null, null, "ALL_MEMBERS");
    }
    return toSettingsDto(account);
  }

  /**
   * Grants an individual user view access to an account (REQ-BANK-035). Idempotent.
   *
   * @param accountId the account
   * @param userId the user to grant
   * @return the refreshed settings
   * @throws NotFoundException when the account or the user does not exist
   * @throws AccessDeniedException when the caller may not configure visibility
   */
  @NotNull
  @Transactional
  public OrgUnitBankAccountSettingsDto addUserVisibility(
      @NotNull UUID accountId, @NotNull UUID userId) {
    BankAccount account = requireAccount(accountId);
    requireCanConfigureVisibility(account);
    if (!userRepository.existsById(userId)) {
      throw new NotFoundException("User not found");
    }
    if (!viewGrantRepository.existsByAccountIdAndGranteeUserId(accountId, userId)) {
      BankAccountViewGrant grant = new BankAccountViewGrant();
      grant.setAccount(account);
      grant.setGranteeKind(BankAccountViewGranteeKind.USER);
      grant.setGranteeUserId(userId);
      viewGrantRepository.save(grant);
      bankAuditService.record(
          BankAuditEventType.BALANCE_VISIBILITY_GRANTED, accountId, null, userId, "USER");
    }
    return toSettingsDto(account);
  }

  /**
   * Revokes an individual user's view access to an account (REQ-BANK-035).
   *
   * @param accountId the account
   * @param userId the user to revoke
   * @return the refreshed settings
   * @throws NotFoundException when the account does not exist
   * @throws AccessDeniedException when the caller may not configure visibility
   */
  @NotNull
  @Transactional
  public OrgUnitBankAccountSettingsDto removeUserVisibility(
      @NotNull UUID accountId, @NotNull UUID userId) {
    BankAccount account = requireAccount(accountId);
    requireCanConfigureVisibility(account);
    long removed = viewGrantRepository.deleteByAccountIdAndGranteeUserId(accountId, userId);
    if (removed > 0) {
      bankAuditService.record(
          BankAuditEventType.BALANCE_VISIBILITY_REVOKED, accountId, null, userId, "USER");
    }
    return toSettingsDto(account);
  }

  // ---------------------------------------------------------------------------------------------
  // F2 — booking requests (unchanged)
  // ---------------------------------------------------------------------------------------------

  /**
   * Raises a confirm-before-post booking request for the caller's <b>own-level</b> org unit
   * (REQ-BANK-022, F2). Enforces the org-unit half of the authorization here (own-level oversight
   * seat) and delegates persistence to the org-unit-blind {@link BankBookingRequestService}.
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
    ScopePredicate scope = ownerScopeService.currentOwnLevelOversightScope();
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
   * Lists the caller's own booking requests (REQ-BANK-022).
   *
   * @return the caller's requests, newest first
   */
  @NotNull
  public List<BankBookingRequestDto> listOwnBookingRequests() {
    return bankBookingRequestService.listForCurrentRequester();
  }

  /**
   * Cancels the caller's own pending booking request (REQ-BANK-022).
   *
   * @param requestId the request to cancel
   * @param version the echoed optimistic-locking version
   * @return the cancelled request
   */
  @NotNull
  public BankBookingRequestDto cancelOwnBookingRequest(@NotNull UUID requestId, long version) {
    return bankBookingRequestService.cancelOwn(requestId, version);
  }

  // ---------------------------------------------------------------------------------------------
  // Capability predicates
  // ---------------------------------------------------------------------------------------------

  /**
   * {@code true} iff the current caller may view the given account (balance + read-only drill-in).
   * Loads the account's view grants on demand — for the batched card-list path use {@link
   * #canViewInternal}.
   *
   * @param account the account to test
   * @return {@code true} iff the caller may view it
   */
  @Transactional(readOnly = true)
  public boolean canView(@NotNull BankAccount account) {
    if (authHelperService.isAdmin()) {
      return true;
    }
    return canViewInternal(
        account,
        ownerScopeService.currentOversightScope(),
        viewGrantRepository.findByAccountId(account.getId()));
  }

  /**
   * Non-admin view decision over a single account given the caller's oversight scope and that
   * account's view grants — the shared core of {@link #canView} and the batched card list. Admins
   * are short-circuited by the callers.
   *
   * @param account the account
   * @param viewScope the caller's cascading oversight scope
   * @param grants the account's view grants
   * @return {@code true} iff the caller may view the account
   */
  private boolean canViewInternal(
      @NotNull BankAccount account,
      @NotNull ScopePredicate viewScope,
      @NotNull List<BankAccountViewGrant> grants) {
    UUID owner = owningOrgUnitId(account);
    return switch (account.getType()) {
      case ORG_UNIT, AREA ->
          owner != null && (viewScope.permits(owner) || matchesOrgUnitGrant(owner, grants));
      case CARTEL ->
          (owner != null && viewScope.permits(owner)) || authHelperService.isMemberOrAbove();
      case CARTEL_BANK -> isCartelBankHolder();
      case SPECIAL -> currentUserCanAutoViewSpecial() || matchesSpecialGrant(grants);
    };
  }

  /**
   * {@code true} iff a view grant on an <em>org-unit</em> account (Staffel/SK/Bereich) matches the
   * caller: a membership-role bucket they hold on the owning unit, an all-members grant when they
   * are a member of the owning unit, or an individual-user grant naming them.
   *
   * @param owningOrgUnitId the account's owning org unit id
   * @param grants the account's view grants
   * @return {@code true} iff a grant admits the caller
   */
  private boolean matchesOrgUnitGrant(
      @NotNull UUID owningOrgUnitId, @NotNull List<BankAccountViewGrant> grants) {
    Optional<UUID> userId = authHelperService.currentUserId();
    for (BankAccountViewGrant grant : grants) {
      boolean match =
          switch (grant.getGranteeKind()) {
            case MEMBERSHIP_ROLE -> {
              MembershipRole role = parseMembershipRole(grant.getRoleCode());
              yield role != null
                  && ownerScopeService.currentUserHoldsRoleOnOrgUnit(owningOrgUnitId, role);
            }
            case ALL_MEMBERS -> ownerScopeService.currentUserIsMemberOfOrgUnit(owningOrgUnitId);
            case USER -> userId.isPresent() && userId.get().equals(grant.getGranteeUserId());
            case GLOBAL_ROLE -> false;
          };
      if (match) {
        return true;
      }
    }
    return false;
  }

  /**
   * {@code true} iff a view grant on a {@code SPECIAL} account matches the caller: a global-role
   * bucket they reach, an all-members grant (any KRT member), or an individual-user grant naming
   * them.
   *
   * @param grants the account's view grants
   * @return {@code true} iff a grant admits the caller
   */
  private boolean matchesSpecialGrant(@NotNull List<BankAccountViewGrant> grants) {
    Optional<UUID> userId = authHelperService.currentUserId();
    for (BankAccountViewGrant grant : grants) {
      boolean match =
          switch (grant.getGranteeKind()) {
            case GLOBAL_ROLE -> authHelperService.hasReachableRole("ROLE_" + grant.getRoleCode());
            case ALL_MEMBERS -> authHelperService.isMemberOrAbove();
            case USER -> userId.isPresent() && userId.get().equals(grant.getGranteeUserId());
            case MEMBERSHIP_ROLE -> false;
          };
      if (match) {
        return true;
      }
    }
    return false;
  }

  /**
   * {@code true} iff the caller auto-views Sonderkonten (REQ-BANK-037): an OL member or any
   * Bereichsleiter. Admins are handled separately by the callers.
   *
   * @return {@code true} iff the caller is an OL member or a Bereichsleiter
   */
  private boolean currentUserCanAutoViewSpecial() {
    return ownerScopeService.currentUserIsOlMember()
        || ownerScopeService.currentUserIsBereichsleiter();
  }

  /**
   * {@code true} iff the caller is the responsible holder of the {@code CARTEL_BANK} account — the
   * {@code BEREICHSLEITER} of a {@code Department.PROFIT} Bereich (REQ-BANK-037).
   *
   * @return {@code true} iff the caller leads a PROFIT Bereich
   */
  private boolean isCartelBankHolder() {
    Set<UUID> profitBereichIds =
        bereichRepository.findByDepartment(Department.PROFIT).stream()
            .map(Bereich::getId)
            .collect(Collectors.toSet());
    return profitBereichIds.stream()
        .anyMatch(
            id ->
                ownerScopeService.currentUserHoldsRoleOnOrgUnit(id, MembershipRole.BEREICHSLEITER));
  }

  /**
   * {@code true} iff the caller is the derived responsible holder of the account (REQ-BANK-034):
   * Staffelleiter / SK-Leiter (ORG_UNIT), Bereichsleiter (AREA), any OL member (CARTEL), the
   * Profit-Bereichsleiter (CARTEL_BANK). Sonderkonten have no responsible holder.
   *
   * @param account the account
   * @return {@code true} iff the caller is its responsible holder
   */
  private boolean isResponsibleHolder(@NotNull BankAccount account) {
    UUID owner = owningOrgUnitId(account);
    return switch (account.getType()) {
      case ORG_UNIT -> {
        if (owner == null) {
          yield false;
        }
        MembershipRole holderRole =
            account.getOrgUnit().getKind() == OrgUnitKind.SPECIAL_COMMAND
                ? MembershipRole.SK_LEAD
                : MembershipRole.STAFFELLEITER;
        yield ownerScopeService.currentUserHoldsRoleOnOrgUnit(owner, holderRole);
      }
      case AREA ->
          owner != null
              && ownerScopeService.currentUserHoldsRoleOnOrgUnit(
                  owner, MembershipRole.BEREICHSLEITER);
      case CARTEL -> ownerScopeService.currentUserIsOlMember();
      case CARTEL_BANK -> isCartelBankHolder();
      case SPECIAL -> false;
    };
  }

  /**
   * {@code true} iff the caller may configure who else may view the account (REQ-BANK-035): the
   * responsible holder for an org-unit account; for a Sonderkonto an OL member <em>or</em> bank
   * management (REQ-BANK-037 — "neben den Bankmitarbeitern/-verwaltung"). {@code CARTEL} (fixed
   * all-members) and {@code CARTEL_BANK} (internal) are not configurable.
   *
   * @param account the account
   * @return {@code true} iff the caller may add/remove view grants
   */
  private boolean canConfigureVisibility(@NotNull BankAccount account) {
    // Admin override: an admin manages the permissions of every account whose visibility is
    // configurable at all. CARTEL (all-members) and CARTEL_BANK (internal) audiences are fixed
    // (REQ-BANK-037) — nothing to configure there, not even for an admin.
    if (authHelperService.isAdmin()) {
      return visibilityConfigurable(account.getType());
    }
    return switch (account.getType()) {
      case ORG_UNIT, AREA -> isResponsibleHolder(account);
      case SPECIAL ->
          ownerScopeService.currentUserIsOlMember()
              || authHelperService.hasReachableRole("ROLE_BANK_MANAGEMENT");
      case CARTEL, CARTEL_BANK -> false;
    };
  }

  /**
   * {@code true} iff the caller may set/clear the account's balance target from the org-unit side
   * (REQ-BANK-036): the responsible holder. Sonderkonto targets are bank-staff-only.
   *
   * @param account the account
   * @return {@code true} iff the caller may set the target
   */
  private boolean canSetTarget(@NotNull BankAccount account) {
    // An admin may set the target on any account (admin override); otherwise the responsible
    // holder.
    // Sonderkonto targets are bank-staff-only for non-admins (set via the bank surface).
    if (authHelperService.isAdmin()) {
      return true;
    }
    return switch (account.getType()) {
      case ORG_UNIT, AREA, CARTEL, CARTEL_BANK -> isResponsibleHolder(account);
      case SPECIAL -> false;
    };
  }

  /**
   * {@code true} iff the caller may open the account's settings panel at all (set the target and/or
   * configure visibility). Drives the per-card settings affordance.
   *
   * @param account the account
   * @return {@code true} iff the caller may manage the account's settings
   */
  private boolean canManageSettings(@NotNull BankAccount account) {
    return canSetTarget(account) || canConfigureVisibility(account);
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  /**
   * Builds the settings snapshot for one account.
   *
   * @param account the account
   * @return the settings DTO
   */
  @NotNull
  private OrgUnitBankAccountSettingsDto toSettingsDto(@NotNull BankAccount account) {
    List<BankAccountViewGrant> grants = viewGrantRepository.findByAccountId(account.getId());
    List<String> grantedRoleCodes =
        grants.stream()
            .filter(
                grant ->
                    grant.getGranteeKind() == BankAccountViewGranteeKind.MEMBERSHIP_ROLE
                        || grant.getGranteeKind() == BankAccountViewGranteeKind.GLOBAL_ROLE)
            .map(BankAccountViewGrant::getRoleCode)
            .toList();
    boolean allMembersGranted =
        grants.stream()
            .anyMatch(grant -> grant.getGranteeKind() == BankAccountViewGranteeKind.ALL_MEMBERS);
    List<UUID> grantedUserIds =
        grants.stream()
            .filter(grant -> grant.getGranteeKind() == BankAccountViewGranteeKind.USER)
            .map(BankAccountViewGrant::getGranteeUserId)
            .toList();
    Map<UUID, String> names =
        grantedUserIds.isEmpty()
            ? Map.of()
            : userRepository.findAllById(grantedUserIds).stream()
                .collect(Collectors.toMap(User::getId, User::getEffectiveName));
    List<OrgUnitBankViewUserDto> grantedUsers =
        grantedUserIds.stream()
            .map(id -> new OrgUnitBankViewUserDto(id, names.getOrDefault(id, "")))
            .toList();
    OrgUnit orgUnit = account.getOrgUnit();
    return new OrgUnitBankAccountSettingsDto(
        account.getId(),
        account.getAccountNo(),
        account.getName(),
        account.getType(),
        orgUnit == null ? null : orgUnit.getKind(),
        account.getBalanceTarget(),
        account.getVersion(),
        canSetTarget(account),
        canConfigureVisibility(account),
        visibilityConfigurable(account.getType()),
        allMembersSupported(account.getType()),
        roleBucketsGlobal(account.getType()),
        availableRoleCodes(account),
        grantedRoleCodes,
        allMembersGranted,
        grantedUsers);
  }

  /**
   * The role-bucket codes the caller may toggle for an account, in display order: the squadron /
   * Bereich sub-ranks for org-unit accounts, the global roles for a Sonderkonto, none otherwise.
   *
   * @param account the account
   * @return the available role codes (possibly empty)
   */
  @NotNull
  private static List<String> availableRoleCodes(@NotNull BankAccount account) {
    if (account.getType() == BankAccountType.SPECIAL) {
      return SPECIAL_GLOBAL_ROLE_BUCKETS;
    }
    OrgUnit orgUnit = account.getOrgUnit();
    if (orgUnit == null) {
      return List.of();
    }
    return switch (orgUnit.getKind()) {
      case SQUADRON -> SQUADRON_ROLE_BUCKETS;
      case BEREICH -> BEREICH_ROLE_BUCKETS;
      case SPECIAL_COMMAND, ORGANISATIONSLEITUNG -> List.of();
    };
  }

  /**
   * Validates a role-bucket code against the account and returns the grant kind it maps to ({@code
   * GLOBAL_ROLE} for a Sonderkonto, else {@code MEMBERSHIP_ROLE}).
   *
   * @param account the account
   * @param roleCode the role code to validate
   * @return the grant kind to use
   * @throws BadRequestException when the role code is not a valid bucket for this account
   */
  @NotNull
  private BankAccountViewGranteeKind requireValidRoleBucket(
      @NotNull BankAccount account, @NotNull String roleCode) {
    if (!availableRoleCodes(account).contains(roleCode)) {
      throw new BadRequestException("Unknown visibility role bucket for this account: " + roleCode);
    }
    return roleBucketsGlobal(account.getType())
        ? BankAccountViewGranteeKind.GLOBAL_ROLE
        : BankAccountViewGranteeKind.MEMBERSHIP_ROLE;
  }

  /**
   * {@code true} iff the account type supports configurable visibility at all (ORG_UNIT / AREA /
   * SPECIAL); {@code CARTEL} (fixed all-members) and {@code CARTEL_BANK} (internal) do not.
   *
   * @param type the account type
   * @return whether visibility is configurable
   */
  private static boolean visibilityConfigurable(@NotNull BankAccountType type) {
    return type == BankAccountType.ORG_UNIT
        || type == BankAccountType.AREA
        || type == BankAccountType.SPECIAL;
  }

  /**
   * {@code true} iff the account type has an all-members visibility bucket (same set as {@link
   * #visibilityConfigurable}).
   *
   * @param type the account type
   * @return whether the all-members bucket applies
   */
  private static boolean allMembersSupported(@NotNull BankAccountType type) {
    return visibilityConfigurable(type);
  }

  /**
   * {@code true} iff the account type's role buckets are global role codes (only {@code SPECIAL});
   * org-unit accounts use {@code MembershipRole} buckets.
   *
   * @param type the account type
   * @return whether the role buckets are global roles
   */
  private static boolean roleBucketsGlobal(@NotNull BankAccountType type) {
    return type == BankAccountType.SPECIAL;
  }

  /**
   * Loads an account and asserts the caller may view it.
   *
   * @param accountId the account id
   * @return the account entity
   * @throws NotFoundException when the account does not exist
   * @throws AccessDeniedException when the caller may not view it
   */
  @NotNull
  private BankAccount requireViewableAccount(@NotNull UUID accountId) {
    BankAccount account = requireAccount(accountId);
    if (!canView(account)) {
      throw new AccessDeniedException("The caller may not view this account");
    }
    return account;
  }

  /**
   * Loads an account or fails with 404.
   *
   * @param accountId the account id
   * @return the account entity
   * @throws NotFoundException when the account does not exist
   */
  @NotNull
  private BankAccount requireAccount(@NotNull UUID accountId) {
    return bankAccountRepository
        .findById(accountId)
        .orElseThrow(() -> new NotFoundException("Bank account not found"));
  }

  /**
   * Asserts the caller may set the account's balance target.
   *
   * @param account the account
   * @throws AccessDeniedException when the caller may not set the target
   */
  private void requireCanSetTarget(@NotNull BankAccount account) {
    if (!canSetTarget(account)) {
      throw new AccessDeniedException("The caller may not set this account's balance target");
    }
  }

  /**
   * Asserts the caller may configure the account's visibility.
   *
   * @param account the account
   * @throws AccessDeniedException when the caller may not configure visibility
   */
  private void requireCanConfigureVisibility(@NotNull BankAccount account) {
    if (!canConfigureVisibility(account)) {
      throw new AccessDeniedException("The caller may not configure this account's visibility");
    }
  }

  /**
   * Explicit optimistic-lock check before mutating the account (mirrors {@code
   * BankAccountService}).
   *
   * @param account the loaded account
   * @param version the client-echoed version
   * @throws ObjectOptimisticLockingFailureException on a version mismatch
   */
  private static void requireVersionMatch(@NotNull BankAccount account, long version) {
    if (account.getVersion() != null && account.getVersion() != version) {
      throw new ObjectOptimisticLockingFailureException(BankAccount.class, account.getId());
    }
  }

  /**
   * The owning org-unit id of an account, or {@code null} when it has none.
   *
   * @param account the account
   * @return the owning org-unit id, or {@code null}
   */
  @Nullable
  private static UUID owningOrgUnitId(@NotNull BankAccount account) {
    return account.getOrgUnit() == null ? null : account.getOrgUnit().getId();
  }

  /**
   * Parses a {@code MembershipRole} name, returning {@code null} for an unknown code.
   *
   * @param code the role code
   * @return the role, or {@code null}
   */
  @Nullable
  private static MembershipRole parseMembershipRole(@Nullable String code) {
    if (code == null) {
      return null;
    }
    try {
      return MembershipRole.valueOf(code);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  /**
   * Redacts the player-custody columns of a booking row for an org-unit viewer (REQ-BANK-038): the
   * holder and counter-holder handles are nulled, everything else is preserved.
   *
   * @param booking the bank-staff booking row
   * @return a copy with the holder handles removed
   */
  @NotNull
  private static BankBookingDto redact(@NotNull BankBookingDto booking) {
    return new BankBookingDto(
        booking.postingId(),
        booking.transactionId(),
        booking.type(),
        booking.amount(),
        null,
        booking.note(),
        booking.createdAt(),
        booking.reversedTransactionId(),
        booking.counterAccountNo(),
        booking.counterAccountName(),
        null,
        booking.intraAccount(),
        booking.transferFee());
  }

  /**
   * Batch-computes the balances of the given accounts in one grouped query (REQ-DATA-003).
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
   * Fetches the last 30 days of posting slices for the given accounts in one windowed query
   * (REQ-DATA-003) and groups them by account id, for the per-card 30-day trend (REQ-BANK-016).
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
   * Projects one visible account and its resolved figures into the balance-card wire shape, now
   * carrying the balance target (REQ-BANK-036) and the settings affordance.
   *
   * @param account the visible account
   * @param balance the resolved balance (zero when it has no postings)
   * @param canRequest {@code true} iff this is the caller's own-level account (F2 affordance)
   * @param delta30d the 30-day net change (signed)
   * @param sparkline the 30 end-of-day balances of the window, oldest first
   * @param canManageSettings whether the caller may open the account's settings
   * @return the balance-card DTO
   */
  @NotNull
  private OrgUnitBankBalanceDto toDto(
      @NotNull BankAccount account,
      @NotNull BigDecimal balance,
      boolean canRequest,
      @NotNull BigDecimal delta30d,
      @NotNull List<BigDecimal> sparkline,
      boolean canManageSettings) {
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
        sparkline,
        account.getBalanceTarget(),
        canManageSettings);
  }
}

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

import de.greluc.krt.iri.basetool.backend.model.BankAccountGrant;
import de.greluc.krt.iri.basetool.backend.model.BankAccountGrantId;
import de.greluc.krt.iri.basetool.backend.repository.BankAccountGrantRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single {@code @PreAuthorize} helper bean for the bank surface (epic #556, ADR-0011), shaped
 * after {@link SpecialCommandSecurityService}.
 *
 * <p>Bank authorization has exactly two inputs (REQ-BANK-008/-009/-010): the two bank Keycloak
 * roles (evaluated through the role hierarchy, so {@code ADMIN > BANK_MANAGEMENT > BANK_EMPLOYEE})
 * and the app-managed {@link BankAccountGrant} rows. It deliberately consults <strong>nothing
 * else</strong> — no {@link OwnerScopeService} scoping, no contextual {@code ROLE_X@orgUnitId}
 * authorities, no {@code X-Active-Org-Unit-Id} admin pin: bank membership is fully independent of
 * org-unit membership, in both directions. This independence is by construction (the class has no
 * org-unit dependency to consult) and pinned by tests.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BankSecurityService {

  private final AuthHelperService authHelperService;
  private final BankAccountGrantRepository grantRepository;

  /**
   * Whether the current caller is bank staff at all — holds {@code ROLE_BANK_EMPLOYEE} directly or
   * reaches it via the hierarchy (management, admin). The coarse gate of every bank surface.
   *
   * @return {@code true} iff the caller reaches the Bank Employee role
   */
  public boolean isBankStaff() {
    return authHelperService.hasReachableRole("ROLE_BANK_EMPLOYEE");
  }

  /**
   * Whether the current caller is Bankleitung or above — sees and manages all accounts, holders and
   * grants (REQ-BANK-010). Admins reach this via the hierarchy.
   *
   * @return {@code true} iff the caller reaches the Bank Management role
   */
  public boolean isManagement() {
    return authHelperService.hasReachableRole("ROLE_BANK_MANAGEMENT");
  }

  /**
   * Answers the SpEL-level question whether the caller may <em>see</em> the given account: bank
   * staff with either the management role or any grant row on the account (row existence = view
   * access, REQ-BANK-009). A non-existent account id is treated as denied — the controller layer
   * surfaces the 404 separately for callers that pass.
   *
   * @param accountId the account to check; never {@code null}
   * @param authentication current Spring Security authentication; may be {@code null} for anonymous
   *     calls (defensive — bank URLs already require authentication)
   * @return {@code true} iff the caller may read the account
   */
  public boolean canSee(@NotNull UUID accountId, Authentication authentication) {
    return hasCapability(accountId, authentication, g -> true);
  }

  /**
   * Whether the caller may book deposits onto the account: management/admin unrestricted, employees
   * need {@code can_deposit} on their grant row (REQ-BANK-009).
   *
   * @param accountId the receiving account; never {@code null}
   * @param authentication current Spring Security authentication
   * @return {@code true} iff the caller may deposit
   */
  public boolean canDeposit(@NotNull UUID accountId, Authentication authentication) {
    return hasCapability(accountId, authentication, BankAccountGrant::isCanDeposit);
  }

  /**
   * Whether the caller may book withdrawals from the account: management/admin unrestricted,
   * employees need {@code can_withdraw} on their grant row (REQ-BANK-009).
   *
   * @param accountId the paying account; never {@code null}
   * @param authentication current Spring Security authentication
   * @return {@code true} iff the caller may withdraw
   */
  public boolean canWithdraw(@NotNull UUID accountId, Authentication authentication) {
    return hasCapability(accountId, authentication, BankAccountGrant::isCanWithdraw);
  }

  /**
   * Whether the caller may transfer out of (or rebook within) the account: management/admin
   * unrestricted, employees need {@code can_transfer} on the <em>source</em> account (REQ-BANK-011;
   * the destination must merely be visible, checked in the booking service).
   *
   * @param accountId the source account; never {@code null}
   * @param authentication current Spring Security authentication
   * @return {@code true} iff the caller may transfer
   */
  public boolean canTransfer(@NotNull UUID accountId, Authentication authentication) {
    return hasCapability(accountId, authentication, BankAccountGrant::isCanTransfer);
  }

  /**
   * Shared evaluation core: authenticated bank staff pass when they are management (hierarchy
   * grants admins the same) or their own grant row satisfies the capability predicate. Reads only
   * roles and the grant table — never org-unit state (REQ-BANK-008).
   *
   * @param accountId the account under decision
   * @param authentication current authentication, possibly {@code null}
   * @param capability the per-grant capability check ({@code g -> true} for plain visibility)
   * @return {@code true} iff the caller passes
   */
  private boolean hasCapability(
      @NotNull UUID accountId,
      Authentication authentication,
      @NotNull Predicate<BankAccountGrant> capability) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    if (!isBankStaff()) {
      return false;
    }
    if (isManagement()) {
      return true;
    }
    Optional<UUID> userId = authHelperService.currentUserId();
    return userId
        .flatMap(uid -> grantRepository.findById(new BankAccountGrantId(uid, accountId)))
        .filter(capability)
        .isPresent();
  }
}

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

package de.greluc.krt.profit.basetool.backend.model.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * The per-tier approval limits of one bank account (REQ-BANK-041), shared by both account-detail
 * surfaces: the bank-staff detail page and the org-unit settings panel. Carries the read-only
 * figures everyone may see (which tiers have which ceiling) plus enough structure for the editor —
 * the available role buckets for this account and whether the calling surface may edit.
 *
 * <p>A tier missing from {@link #roleLimits} / a {@code null} {@link #allMembersLimit} / a user not
 * in {@link #userLimits} means "no limit" = unlimited (no approval needed). Tiers mirror the
 * account's configurable visibility buckets: squadron sub-ranks / Bereich ranks ({@link
 * #availableRoleCodes}), the all-members bucket ({@link #allMembersSupported}) and individual
 * users.
 *
 * @param canEdit whether the calling surface may set/clear limits (responsible holder / bank
 *     management / admin on the org-unit surface; management / admin on the bank surface)
 * @param configurable whether this account type carries approval limits at all ({@code ORG_UNIT} /
 *     {@code AREA} / {@code CARTEL})
 * @param allMembersSupported whether the all-members tier applies to this account
 * @param availableRoleCodes the role buckets that may carry a limit on this account ({@code
 *     MembershipRole} names), in display order; empty for SK / CARTEL accounts
 * @param roleLimits the currently configured role-bucket limits, keyed by role code
 * @param allMembersLimit the configured all-members limit, or {@code null} when none is set
 * @param userLimits the configured individual-user limits, with resolved display names
 */
public record BankApprovalLimitsDto(
    boolean canEdit,
    boolean configurable,
    boolean allMembersSupported,
    List<String> availableRoleCodes,
    Map<String, BigDecimal> roleLimits,
    @Nullable BigDecimal allMembersLimit,
    List<BankApprovalLimitUserDto> userLimits) {

  /**
   * Whether any approval limit is configured at all — gates the read-only display block (no block
   * is rendered when an account carries no limits).
   *
   * @return {@code true} iff at least one role, all-members or user limit is set
   */
  public boolean hasAny() {
    return !roleLimits.isEmpty() || allMembersLimit != null || !userLimits.isEmpty();
  }
}

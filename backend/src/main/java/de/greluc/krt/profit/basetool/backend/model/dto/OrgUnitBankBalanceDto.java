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

import de.greluc.krt.profit.basetool.backend.model.BankAccountStatus;
import de.greluc.krt.profit.basetool.backend.model.OrgUnitKind;
import java.math.BigDecimal;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * The current balance of one org-unit bank account, exposed to an officer or lead who oversees that
 * org unit (REQ-BANK-021, F1). Deliberately balance-only: it carries the account's identity (so the
 * frontend can label the card and target a booking request against the org unit) and its current
 * balance, but never the transaction history, the holder distribution or the audit trail — those
 * stay a bank-staff surface. One instance is produced per overseen org unit that actually owns an
 * account; org units without an account are simply absent from the list.
 *
 * @param accountId the org-unit account's id (the caller's own account; used to key the F2 request
 *     form, never to reach a staff endpoint)
 * @param accountNo the human-readable {@code KB-<n>} account number
 * @param accountName the account's display name
 * @param status the account lifecycle status (ACTIVE / CLOSED)
 * @param orgUnitId the owning org unit's id
 * @param orgUnitName the owning org unit's long-form name
 * @param orgUnitShorthand the owning org unit's 3–5 letter shorthand, or {@code null} for a legacy
 *     row without one
 * @param orgUnitKind the owning org unit's kind (SQUADRON / SPECIAL_COMMAND / BEREICH /
 *     ORGANISATIONSLEITUNG), for styling
 * @param balance the account's current balance in whole aUEC (compute-on-read, ADR-0010)
 * @param canRequest {@code true} iff this is the caller's <em>own-level</em> account, so the F2
 *     booking-request affordance applies (epic #692 Phase 6, owner decision Q4). {@code false} for
 *     a subordinate account reached through the cascading view — those are view-only, and the
 *     backend rejects a request against them.
 */
public record OrgUnitBankBalanceDto(
    UUID accountId,
    String accountNo,
    String accountName,
    BankAccountStatus status,
    UUID orgUnitId,
    String orgUnitName,
    @Nullable String orgUnitShorthand,
    OrgUnitKind orgUnitKind,
    BigDecimal balance,
    boolean canRequest) {}

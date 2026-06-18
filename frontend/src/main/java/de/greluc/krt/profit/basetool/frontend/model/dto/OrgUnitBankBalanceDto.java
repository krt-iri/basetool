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

package de.greluc.krt.profit.basetool.frontend.model.dto;

import java.math.BigDecimal;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Frontend mirror of the backend's org-unit bank balance payload (epic #666 F1, REQ-BANK-021).
 * Deliberately balance-only — it carries the account's identity (so the officer/lead page can label
 * the card and target a booking request at the org unit) and the compute-on-read balance, never the
 * transaction history, holders or audit. The status / org-unit kind arrive as enum names rendered
 * through i18n keys.
 *
 * @param accountId the org-unit account's id
 * @param accountNo the server-generated display number ({@code KB-0042})
 * @param accountName the account's display name
 * @param status lifecycle enum name ({@code ACTIVE} / {@code CLOSED})
 * @param orgUnitId the owning org unit's id
 * @param orgUnitName the owning org unit's long-form name
 * @param orgUnitShorthand the owning org unit's shorthand, or {@code null}
 * @param orgUnitKind kind enum name ({@code SQUADRON} / {@code SPECIAL_COMMAND})
 * @param balance the current balance (signed whole aUEC)
 */
public record OrgUnitBankBalanceDto(
    UUID accountId,
    String accountNo,
    String accountName,
    String status,
    UUID orgUnitId,
    String orgUnitName,
    @Nullable String orgUnitShorthand,
    String orgUnitKind,
    BigDecimal balance) {}

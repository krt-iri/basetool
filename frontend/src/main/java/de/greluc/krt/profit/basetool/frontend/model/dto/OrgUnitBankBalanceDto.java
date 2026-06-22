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
 * Frontend mirror of the backend's org-unit bank balance payload (epic #666 F1,
 * REQ-BANK-021/-027/-028). Deliberately balance-only — it carries the account's identity (so the
 * officer/lead page can label the card and target a booking request at the org unit) and the
 * compute-on-read balance, never the transaction history, holders or audit. The status / type /
 * org-unit kind arrive as enum names rendered through i18n keys.
 *
 * <p>An org-unit account populates the {@code orgUnit*} fields; a special account (Sonderkonto,
 * REQ-BANK-028, only visible to Bereich/OL overseers and admins) carries {@code null} org-unit
 * fields and {@code canRequest = false}, and the page labels its card by {@link #type}. Only active
 * accounts are ever delivered.
 *
 * @param accountId the account's id
 * @param accountNo the server-generated display number ({@code KB-0042})
 * @param accountName the account's display name
 * @param status lifecycle enum name (always {@code ACTIVE} here — closed accounts are filtered out)
 * @param type account-type enum name ({@code ORG_UNIT} / {@code AREA} / {@code CARTEL} / {@code
 *     SPECIAL}); used to label a special account that has no org-unit identity
 * @param orgUnitId the owning org unit's id, or {@code null} for a special account
 * @param orgUnitName the owning org unit's long-form name, or {@code null} for a special account
 * @param orgUnitShorthand the owning org unit's shorthand, or {@code null}
 * @param orgUnitKind kind enum name ({@code SQUADRON} / {@code SPECIAL_COMMAND} / {@code BEREICH} /
 *     {@code ORGANISATIONSLEITUNG}), or {@code null} for a special account
 * @param balance the current balance (signed whole aUEC)
 * @param canRequest {@code true} iff this is the caller's own-level org-unit account (the request
 *     button is shown); {@code false} for a view-only subordinate account reached by the cascade
 *     (epic #692 Phase 6, owner decision Q4) and for every special account
 */
public record OrgUnitBankBalanceDto(
    UUID accountId,
    String accountNo,
    String accountName,
    String status,
    String type,
    @Nullable UUID orgUnitId,
    @Nullable String orgUnitName,
    @Nullable String orgUnitShorthand,
    @Nullable String orgUnitKind,
    BigDecimal balance,
    boolean canRequest) {}

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

package de.greluc.krt.profit.basetool.backend.model.projection;

import de.greluc.krt.profit.basetool.backend.model.BankTransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPQL constructor projection of one booking-history row on the account detail page: the posting
 * joined with its transaction header and holder in a single statement (no N+1, REQ-BANK-018). For
 * {@code TRANSFER} rows the counter leg (other account / other holder) is resolved by a second
 * batched IN-query over the page's transaction ids — see {@code BankAccountService}.
 *
 * @param postingId the leg's id (stable row identity for the page)
 * @param transactionId the owning transaction's id (reversal target, counter-leg lookup key)
 * @param type the transaction type driving the row's chip rendering
 * @param amount the signed amount of THIS account's leg
 * @param holderHandle the handle snapshot of the holder whose stash this leg changed
 * @param note the transaction's free-text note, may be {@code null}
 * @param createdAt the booking instant (UTC)
 * @param reversedTransactionId for {@code REVERSAL} rows the corrected transaction's id, else
 *     {@code null}
 */
public record BankBookingRow(
    UUID postingId,
    UUID transactionId,
    BankTransactionType type,
    BigDecimal amount,
    String holderHandle,
    String note,
    Instant createdAt,
    UUID reversedTransactionId) {}

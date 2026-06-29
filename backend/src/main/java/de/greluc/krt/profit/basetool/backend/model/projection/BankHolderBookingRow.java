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
 * JPQL constructor projection of one row of a <strong>holder's</strong> custody history
 * (REQ-BANK-032, ADR-0039): the holder ledger leg joined with its transaction header in a single
 * statement (no N+1). The account reference (which account the money moved on) and — for a {@code
 * HOLDER_TRANSFER} — the counter holder are resolved by batched IN-queries over the page's
 * transaction ids (see {@code BankHolderService}), exactly mirroring how the account history
 * annotates its rows with the holder.
 *
 * @param postingId the holder leg's id (stable row identity for the page)
 * @param transactionId the owning transaction's id (counter-leg lookup key, reversal target)
 * @param type the transaction type driving the row's chip rendering
 * @param amount the signed amount of THIS holder's leg (positive: received into the stash;
 *     negative: paid out of the stash)
 * @param note the transaction's free-text note, may be {@code null}
 * @param createdAt the booking instant (UTC)
 * @param reversedTransactionId for {@code REVERSAL} rows the corrected transaction's id, else
 *     {@code null}
 * @param transferFee the in-game transfer fee added on top of this transaction's entered amount and
 *     borne by the debited source (ADR-0052, REQ-BANK-033); {@code 0} for non-fee transactions
 */
public record BankHolderBookingRow(
    UUID postingId,
    UUID transactionId,
    BankTransactionType type,
    BigDecimal amount,
    String note,
    Instant createdAt,
    UUID reversedTransactionId,
    BigDecimal transferFee) {}

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

package de.greluc.krt.iri.basetool.backend.model.projection;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * JPQL constructor projection of one ledger leg with its account and holder labels, fetched batched
 * by transaction ids. Used to resolve the counter side of {@code TRANSFER} rows for the booking
 * history (one IN-query per page instead of per-row lookups) and to mirror legs when building a
 * {@code REVERSAL} (ADR-0010).
 *
 * @param transactionId the owning transaction's id (the batch key)
 * @param postingId the leg's id, used to exclude the row's own leg when picking the counter leg
 * @param accountId the account the leg posts to
 * @param accountNo the account's display number (e.g. {@code KB-0003})
 * @param accountName the account's display name
 * @param holderId the holder whose stash the leg changes
 * @param holderHandle the holder's handle snapshot
 * @param amount the leg's signed amount
 */
public record BankCounterLeg(
    UUID transactionId,
    UUID postingId,
    UUID accountId,
    String accountNo,
    String accountName,
    UUID holderId,
    String holderHandle,
    BigDecimal amount) {}

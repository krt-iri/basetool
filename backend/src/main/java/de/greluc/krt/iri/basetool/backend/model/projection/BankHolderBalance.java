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
 * JPQL constructor projection for one slice of an account's holder distribution (REQ-BANK-003): the
 * per-(account, holder) sub-balance computed as a grouped {@code SUM} over the postings. The
 * sub-balances of an account always sum exactly to its balance.
 *
 * @param holderId the {@code bank_holder} id
 * @param handle the holder's deletion-proof handle snapshot, for display
 * @param holderActive whether the holder currently accepts new postings
 * @param amount the signed sub-balance the holder physically holds on the account
 */
public record BankHolderBalance(
    UUID holderId, String handle, boolean holderActive, BigDecimal amount) {}

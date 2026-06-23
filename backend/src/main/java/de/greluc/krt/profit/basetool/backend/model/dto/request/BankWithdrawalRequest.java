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

package de.greluc.krt.profit.basetool.backend.model.dto.request;

import de.greluc.krt.profit.basetool.backend.validation.WholeNumber;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Write payload for booking a withdrawal (REQ-BANK-004/-005): money left the bank, paid out by the
 * named holder. Guarded by the no-overdraft rule at <strong>account</strong> level only
 * (REQ-BANK-006, ADR-0039) — the holder may go negative; the amount is whole-aUEC and strictly
 * positive — the sign comes from the transaction type.
 *
 * @param accountId the paying account
 * @param holderId the player who physically paid the money out (REQ-BANK-003)
 * @param amount whole-aUEC amount, at least 1
 * @param note optional free-text note for the booking history and statements
 */
public record BankWithdrawalRequest(
    @NotNull UUID accountId,
    @NotNull UUID holderId,
    @NotNull @DecimalMin("1") @DecimalMax("1000000000000.0") @WholeNumber BigDecimal amount,
    @Nullable @Size(max = 500) String note) {}

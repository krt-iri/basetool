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
 * Write payload for booking an account-to-account transfer (REQ-BANK-011, ADR-0039): two account
 * legs summing to zero and two holder legs summing to zero — value moves between two
 * <strong>different</strong> accounts and physical custody moves with it. Identical source and
 * destination account is a rejected self-transfer ({@code BANK_SELF_TRANSFER}); moving custody
 * between holders without touching an account is a holder Umbuchung ({@code
 * BankHolderTransferRequest}), not a transfer.
 *
 * @param sourceAccountId the account the value leaves
 * @param sourceHolderId the player whose stash shrinks
 * @param destinationAccountId the account the value enters (must differ from the source)
 * @param destinationHolderId the player whose stash grows
 * @param amount whole-aUEC amount, at least 1
 * @param note optional free-text note for the booking history and statements
 */
public record BankTransferRequest(
    @NotNull UUID sourceAccountId,
    @NotNull UUID sourceHolderId,
    @NotNull UUID destinationAccountId,
    @NotNull UUID destinationHolderId,
    @NotNull @DecimalMin("1") @DecimalMax("1000000000000.0") @WholeNumber BigDecimal amount,
    @Nullable @Size(max = 500) String note) {}

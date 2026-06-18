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

import de.greluc.krt.profit.basetool.backend.model.BankBookingRequestType;
import de.greluc.krt.profit.basetool.backend.validation.WholeNumber;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Write payload for an org-unit officer/lead raising a confirm-before-post booking request
 * (REQ-BANK-022). The caller names the org unit they act for (the service verifies it is within
 * their oversight scope and resolves it to the org unit's bank account), the movement kind and a
 * whole-aUEC amount. There is deliberately <strong>no holder field</strong> — the holder is
 * recorded by the bank employee at confirmation, not by the requester.
 *
 * @param orgUnitId the org unit the requester acts for (must be in the caller's oversight scope)
 * @param type whether to request a deposit or a withdrawal
 * @param amount whole-aUEC amount, at least 1
 * @param note optional free-text note carried onto the booking on confirmation
 */
public record CreateBankBookingRequest(
    @NotNull UUID orgUnitId,
    @NotNull BankBookingRequestType type,
    @NotNull @DecimalMin("1") @DecimalMax("1000000000000.0") @WholeNumber BigDecimal amount,
    @Nullable @Size(max = 500) String note) {}

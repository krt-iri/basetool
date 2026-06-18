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

import de.greluc.krt.profit.basetool.backend.model.FinanceType;
import de.greluc.krt.profit.basetool.backend.validation.WholeNumber;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Data transfer record carrying Mission Finance Entry Create payload. The {@code @Size} /
 * {@code @DecimalMax} caps cap the anonymous attack surface — without them an unauthenticated
 * caller could push a 100 MB {@code note} or a {@code 1e100} amount through the public create-entry
 * endpoint (audit finding C-2). The {@link WholeNumber} constraint rejects fractional amounts —
 * mission finance is entered and stored as whole aUEC (REQ-MISSION-001); the system keeps
 * fractional precision only for derived totals (refinery profit), never for an operator-entered
 * entry.
 */
public record MissionFinanceEntryCreateDto(
    @NotNull UUID missionId,
    @NotNull UUID participantId,
    @Size(max = 2000) String note,
    @NotNull FinanceType type,
    @NotNull @DecimalMin("0.0") @DecimalMax("1000000000.0") @WholeNumber BigDecimal amount) {}

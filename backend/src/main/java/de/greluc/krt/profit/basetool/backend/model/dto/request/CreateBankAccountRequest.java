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

import de.greluc.krt.profit.basetool.backend.model.BankAccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Write payload for creating a bank account (REQ-BANK-001/-002). Server-managed fields (id, account
 * number, status, version) are deliberately absent; the type-specific owner-reference rules (org
 * unit for {@code ORG_UNIT}, area name for {@code AREA}, neither otherwise) are validated in {@code
 * BankAccountService} because they depend on {@link #type}.
 *
 * @param name display name of the new account
 * @param type the organizational layer; immutable after creation
 * @param orgUnitId owning org unit, required for {@code ORG_UNIT}, forbidden otherwise
 * @param areaName free-form Bereich name, required for {@code AREA}, forbidden otherwise
 */
public record CreateBankAccountRequest(
    @NotBlank @Size(max = 255) String name,
    @NotNull BankAccountType type,
    @Nullable UUID orgUnitId,
    @Nullable @Size(max = 255) String areaName) {}

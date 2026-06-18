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

import java.math.BigDecimal;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Response payload for one holder-registry row (epic #556, REQ-BANK-003), enriched with the
 * cross-account custody totals the management "Halter" tab shows (W1 mockup).
 *
 * @param id the holder row's id
 * @param userId the linked basetool user, or {@code null} after user deletion (handle snapshot
 *     remains)
 * @param handle the deletion-proof handle snapshot
 * @param active whether the holder accepts new postings
 * @param totalHeld signed sum the holder physically holds across all accounts
 * @param accountCount number of accounts with a non-zero sub-balance for this holder
 * @param version optimistic-locking version the client must echo on mutations
 */
public record BankHolderDto(
    UUID id,
    @Nullable UUID userId,
    String handle,
    boolean active,
    BigDecimal totalHeld,
    long accountCount,
    Long version) {}

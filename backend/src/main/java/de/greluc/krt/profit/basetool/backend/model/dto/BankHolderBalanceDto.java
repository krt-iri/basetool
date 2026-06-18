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

/**
 * One slice of an account's holder distribution (REQ-BANK-003): which player physically holds which
 * part of the account's balance. The slices of an account always sum exactly to its balance.
 *
 * @param holderId the holder row's id
 * @param handle the holder's handle snapshot, for display
 * @param active whether the holder accepts new postings (drives selectability in booking modals)
 * @param amount the signed sub-balance the holder holds on the account
 */
public record BankHolderBalanceDto(
    UUID holderId, String handle, boolean active, BigDecimal amount) {}

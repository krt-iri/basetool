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

/**
 * The calling user's evaluated booking capabilities on one account (REQ-BANK-009), as decided by
 * {@code BankSecurityService} — management and admins carry every flag via the role hierarchy. The
 * UI renders the K1 action buttons from these flags; the server re-checks every booking regardless
 * (the DTO is presentation input, not an authorization shortcut).
 *
 * @param canDeposit whether the caller may book deposits onto the account
 * @param canWithdraw whether the caller may book withdrawals from the account
 * @param canTransfer whether the caller may transfer out of / rebook within the account
 * @param management whether the caller has the management perspective (drives manage-only UI)
 */
public record BankCapabilitiesDto(
    boolean canDeposit, boolean canWithdraw, boolean canTransfer, boolean management) {}

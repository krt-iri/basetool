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
 * Result of an admin audit-log retention purge (REQ-AUDIT-004): how many audit rows older than the
 * chosen cutoff were deleted. Shared by the per-area purge ({@code DELETE /api/v1/audit/{domain}})
 * and the bank purge ({@code DELETE /api/v1/bank/admin/audit}); the UI renders the deleted count
 * back to the admin. The purge itself is audit-logged and is therefore <em>not</em> counted here.
 *
 * @param deletedCount the number of audit rows removed (non-negative; {@code 0} when nothing was
 *     older than the cutoff)
 */
public record AuditPurgeResultDto(int deletedCount) {}

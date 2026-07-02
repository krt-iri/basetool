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
 * Outcome summary of a bulk personal-blueprint clear (REQ-INV-023 / REQ-INV-024). Both the "delete
 * all my blueprints" (owner-scoped) and the admin "delete all users' blueprints" (global purge)
 * skip the auto-granted, non-removable default blueprints (REQ-INV-016), so {@link #deleted} counts
 * only the removable rows that were actually removed.
 *
 * @param deleted number of removable owned-blueprint rows removed by the operation (never counts a
 *     preserved default)
 */
public record PersonalBlueprintBulkDeleteResult(int deleted) {}

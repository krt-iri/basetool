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

package de.greluc.krt.profit.basetool.backend.model.projection;

import java.util.UUID;

/**
 * One pooled stock slice for the blueprint craftability calculation (#781): the caller's available
 * SCU of a single {@link de.greluc.krt.profit.basetool.backend.model.Material} at a single quality
 * tier, summed across all storage locations.
 *
 * <p>Two sources produce slices, both normalized to SCU so they can be merged: the caller's "My
 * Inventory" stock (summed from {@link de.greluc.krt.profit.basetool.backend.model.InventoryItem}
 * via a grouped JPQL query) and — when the refinery toggle is on — the yield of the caller's {@code
 * OPEN}/{@code IN_PROGRESS} refinery orders (computed from {@link
 * de.greluc.krt.profit.basetool.backend.model.RefineryGood} after the units→SCU conversion). The
 * craftability calculator consumes the best-quality slices first, so the {@code quality} field is
 * load-bearing, not decorative.
 *
 * @param materialId the commodity this slice holds; never {@code null}
 * @param quality the quality tier (0..1000) shared by every unit in this slice
 * @param totalScu the available amount in SCU, summed across locations for the material/quality
 *     pair
 */
public record OwnedStockSlice(UUID materialId, Integer quality, Double totalScu) {}

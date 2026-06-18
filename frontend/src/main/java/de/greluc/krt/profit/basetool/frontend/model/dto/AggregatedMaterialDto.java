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

package de.greluc.krt.profit.basetool.frontend.model.dto;

import java.util.List;

/**
 * Frontend mirror of the backend {@code AggregatedMaterialDto}: one aggregation row of an item
 * order's internal material view (a material at one quality, summed across the order). {@code
 * qualityRequirement} is the {@code GOOD}/{@code NONE} name as a string. {@code claims}/{@code
 * openAmount} are populated only for public SK orders (Phase 5, #345); {@code openAmount} is {@code
 * null} for private orders, which is how the detail template decides whether to render the claim
 * columns.
 *
 * @param material the aggregated material (carries {@code quantityType} for unit-aware display)
 * @param qualityRequirement the quality bucket name ({@code GOOD} or {@code NONE})
 * @param totalQuantity the summed required quantity for this material+quality
 * @param currentStock the total stock linked to the order for this material at or above the
 *     bucket's quality floor; the order-overview list renders {@code currentStock / totalQuantity}
 *     as the material-collection progress for item orders, mirroring the MATERIAL rows
 * @param claims the per-squadron claims on this bucket (empty for non-SK orders)
 * @param openAmount {@code totalQuantity − Σ claims}; {@code null} for non-SK orders
 */
public record AggregatedMaterialDto(
    MaterialDto material,
    String qualityRequirement,
    Double totalQuantity,
    Double currentStock,
    List<ClaimDto> claims,
    Double openAmount) {}

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

import java.util.List;
import java.util.UUID;

/**
 * Data transfer record carrying Job Order Material payload.
 *
 * @param id material-line primary key
 * @param material the required material (with {@code quantityType} for unit-aware display)
 * @param minQuality the minimum acceptable quality (700) or {@code null} for "Keine"
 * @param amount the required amount in the material's own unit
 * @param currentStock the summed linked-inventory stock for this line
 * @param claims the per-squadron claims on this material's bucket; populated only for public SK
 *     orders (Phase 5, #345), empty otherwise
 * @param openAmount {@code required − Σ claims} for the bucket; {@code null} for non-SK orders (no
 *     claim columns are rendered then), a non-null value (possibly 0) for SK orders
 * @param version optimistic-lock version
 */
public record JobOrderMaterialDto(
    UUID id,
    MaterialDto material,
    Integer minQuality,
    Double amount,
    Double currentStock,
    List<ClaimDto> claims,
    Double openAmount,
    Long version) {}

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

package de.greluc.krt.profit.basetool.ingest.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One material row of a {@link RefineryExtractOrderDto}. Verbatim screen reads; the backend
 * resolves the material and applies the skip/quote rules. Mirror of the backend record.
 *
 * @param rowIndex the stitched on-screen row order (preserved by the backend)
 * @param rawMaterialName verbatim material read, resolved backend-side
 * @param quality the on-screen quality value, if read
 * @param inputQuantity the raw input quantity
 * @param outputQuantity the quoted output quantity; {@code null} marks an un-quoted row
 * @param refine whether the REFINE toggle was on (an off row is skipped backend-side)
 * @param confidence the derived per-row read confidence in [0,1]
 * @param sourceImage the source image this row was read from (provenance)
 */
public record RefineryExtractGoodDto(
    @PositiveOrZero Integer rowIndex,
    @NotNull @Size(max = 255) String rawMaterialName,
    Integer quality,
    @NotNull @PositiveOrZero Integer inputQuantity,
    @PositiveOrZero Integer outputQuantity,
    @NotNull Boolean refine,
    @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
    @Size(max = 255) String sourceImage) {}

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

package de.greluc.krt.iri.basetool.backend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Data transfer record carrying Create Job Order Material payload. */
public record CreateJobOrderMaterialDto(
    @NotNull UUID materialId,
    @org.jetbrains.annotations.Nullable
        @Min(700)
        @Max(700)
        @Schema(
            description =
                "Minimale Qualität: 700 (vorgegeben) oder null für \"Keine\" (keine"
                    + " Mindestqualität).",
            example = "700")
        Integer minQuality,
    // @Max caps the per-material amount at 100 000 units so an anonymous caller cannot push a
    // 1e308 value through the public create-order endpoint (audit finding H-2: ledger pollution
    // + downstream BigDecimal aggregation overflow). Tightening from "no upper bound" to
    // 100 000 covers any realistic legitimate Star Citizen cargo manifest by an order of
    // magnitude.
    @NotNull @Min(0) @Max(100_000) Double amount) {}

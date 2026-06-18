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

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One material row of an extracted refinement order ({@code goods[]} in the frozen contract, plan
 * §5). Duplicate material names across rows are <b>normal</b> (the same ore appears at up to four
 * different qualities in real orders) — rows are never merged here.
 *
 * <p>{@code quality} deliberately carries no range constraint: an out-of-range value (likely VLM
 * misread) must still produce a draft with an {@code OUT_OF_RANGE_QUALITY} warning instead of a
 * 400, so the user can correct it in the review form.
 *
 * @param rowIndex stitched on-screen position, top row = 0; drives the draft's goods order
 * @param rawMaterialName material name verbatim including any {@code "(ORE)"} / {@code "(RAW)"}
 *     suffix; may be game-UI-truncated (e.g. {@code "UCTION SALVAGE"})
 * @param quality SC QUALITY column; expected 0..1000 but not enforced (see above); {@code null}
 *     defaults to {@code 0} in the draft (existing create-form convention)
 * @param inputQuantity SC QTY column (raw units); {@code 0} marks an empty/misread row that is
 *     skipped with {@code SKIPPED_ZERO_QTY}
 * @param outputQuantity SC YIELD column (projected refined output); {@code null} when the row was
 *     captured before GET QUOTE (rendered {@code "--"}) — reported as {@code UNQUOTED_ROW}
 * @param refine SC REFINE toggle; {@code false} rows (inert slag) are skipped with {@code
 *     SKIPPED_REFINE_OFF}
 * @param confidence derived per-row read confidence in {@code [0,1]} (two-pass agreement +
 *     checksum, per the Phase 0 policy) — never the model's verbalized self-estimate
 * @param sourceImage file name of the screenshot the row was read from (provenance only)
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

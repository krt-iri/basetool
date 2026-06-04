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

package de.greluc.krt.iri.basetool.frontend.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;

/**
 * Data transfer record carrying Refinery Good payload.
 *
 * <p>{@code yieldBonusPercent} mirrors the backend's read-only UEX-derived enrichment (positive =
 * bonus, negative = malus, {@code null} = no yield row known for the (location, material) pair).
 * The frontend renders it next to the input-quantity field and ignores it on form submit (backend
 * recomputes it on every response).
 */
public record RefineryGoodDto(
    UUID id,
    MaterialDto inputMaterial,
    @Min(1) Integer inputQuantity,
    MaterialDto outputMaterial,
    @Min(1) Integer outputQuantity,
    @Min(0) @Max(1000) Integer quality,
    Integer yieldBonusPercent) {}

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

package de.greluc.krt.profit.basetool.backend.dto.scwiki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The input quality band a {@link ScWikiBlueprintModifierDto} interpolates across (SC Wiki {@code
 * blueprint_modifier_quality_range}). In observed payloads this is {@code 0..1000}; the crafted
 * item's stat multiplier moves from {@code modifier_range.at_min_quality} to {@code at_max_quality}
 * as the consumed ingredient's quality moves from {@link #min} to {@link #max}.
 *
 * @param min lowest ingredient-quality value of the band, or {@code null}
 * @param max highest ingredient-quality value of the band, or {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScWikiBlueprintQualityRangeDto(
    @JsonProperty("min") Double min, @JsonProperty("max") Double max) {}

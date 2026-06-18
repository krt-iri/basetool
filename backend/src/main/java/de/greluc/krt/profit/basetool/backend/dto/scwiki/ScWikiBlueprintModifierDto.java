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
import java.util.List;
import java.util.UUID;

/**
 * One stat contribution a requirement group makes to the crafted item (SC Wiki {@code
 * blueprint_modifier}), nested under {@link ScWikiBlueprintRequirementGroupDto#modifiers()}. This
 * is the "stat the ingredient delivers": {@link #propertyKey} / {@link #label} name the affected
 * output stat (e.g. {@code weapon_damage} / "Impact Force"), and {@link #modifierRange} gives the
 * multiplier band the stat moves through as the consumed ingredient's quality sweeps {@link
 * #qualityRange}.
 *
 * <p>When {@link #valueSegments} is present the stat does NOT move linearly between the two {@code
 * modifier_range} endpoints — it follows the chained per-segment ranges instead (a stepped /
 * piecewise-linear curve). Consumers must prefer {@link #valueSegments} over {@link #qualityRange}
 * / {@link #modifierRange} whenever it is non-empty.
 *
 * @param propertyKey internal stat key (e.g. {@code "weapon_damage"}), or {@code null}
 * @param propertyUuid UUID of the property definition, or {@code null}
 * @param label human-readable stat name (e.g. {@code "Impact Force"}), or {@code null}
 * @param betterWhen whether a higher or lower value is desirable ({@code higher}/{@code
 *     lower}/{@code neutral}), or {@code null}
 * @param qualityRange the ingredient-quality band the modifier interpolates across, or {@code null}
 * @param modifierRange the stat-multiplier endpoints for that band, or {@code null}
 * @param valueRangeType interpolation type (currently {@code "linear"}), or {@code null}
 * @param valueSegments the per-segment ranges of a stepped / piecewise-linear modifier, or {@code
 *     null} / empty for the simple linear form
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScWikiBlueprintModifierDto(
    @JsonProperty("property_key") String propertyKey,
    @JsonProperty("property_uuid") UUID propertyUuid,
    @JsonProperty("label") String label,
    @JsonProperty("better_when") String betterWhen,
    @JsonProperty("quality_range") ScWikiBlueprintQualityRangeDto qualityRange,
    @JsonProperty("modifier_range") ScWikiBlueprintModifierRangeDto modifierRange,
    @JsonProperty("value_range_type") String valueRangeType,
    @JsonProperty("value_segments") List<ScWikiBlueprintModifierSegmentDto> valueSegments) {}

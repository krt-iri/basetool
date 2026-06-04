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

import java.util.List;

/**
 * Boundary DTO for one stat contribution a requirement group makes to the crafted item. Surfaces
 * the persisted {@code blueprint_requirement_modifier} on the admin blueprint page: {@link #label}
 * names the affected output stat and {@link #modifierAtMinQuality}..{@link #modifierAtMaxQuality}
 * gives the multiplier band swept across {@link #qualityMin}..{@link #qualityMax}.
 *
 * <p>When {@link #segments} is non-empty the stat changes in steps rather than along the single
 * endpoint band — the slider on the page interpolates within the segment that contains the chosen
 * quality and ignores the {@code modifierAtMin/MaxQuality} pair.
 *
 * <p><b>Effective vs. raw quality band.</b> For a stepped modifier the SC Wiki populates the raw
 * {@link #qualityMin}..{@link #qualityMax} pair with only the <em>first</em> segment's bounds (e.g.
 * {@code 0..500}), while the {@link #segments} together cover the full {@code 0..1000} band. The UI
 * must span the whole covered range, so {@link #effectiveQualityMin}..{@link #effectiveQualityMax}
 * give the union of the segment bounds when segments are present and fall back to the raw pair for
 * the simple linear form. Slider extents must use the effective pair; the raw pair is kept for
 * reference / linear interpolation only.
 *
 * @param propertyKey internal stat key (e.g. {@code "weapon_damage"})
 * @param label human-readable stat name (e.g. {@code "Impact Force"})
 * @param betterWhen whether a higher / lower / neutral value is desirable
 * @param qualityMin lowest ingredient-quality value of the raw endpoint band (first segment only
 *     for stepped modifiers)
 * @param qualityMax highest ingredient-quality value of the raw endpoint band (first segment only
 *     for stepped modifiers)
 * @param modifierAtMinQuality stat multiplier at the minimum quality
 * @param modifierAtMaxQuality stat multiplier at the maximum quality
 * @param valueRangeType interpolation type between the endpoints (e.g. {@code "linear"})
 * @param segments per-segment ranges of a stepped / piecewise-linear modifier; empty for the simple
 *     linear form
 * @param effectiveQualityMin lowest quality the modifier actually covers — the smallest segment
 *     {@code qualityMin} when stepped, else {@link #qualityMin}
 * @param effectiveQualityMax highest quality the modifier actually covers — the largest segment
 *     {@code qualityMax} when stepped, else {@link #qualityMax}
 */
public record BlueprintRequirementModifierDto(
    String propertyKey,
    String label,
    String betterWhen,
    Double qualityMin,
    Double qualityMax,
    Double modifierAtMinQuality,
    Double modifierAtMaxQuality,
    String valueRangeType,
    List<BlueprintRequirementModifierSegmentDto> segments,
    Double effectiveQualityMin,
    Double effectiveQualityMax) {}

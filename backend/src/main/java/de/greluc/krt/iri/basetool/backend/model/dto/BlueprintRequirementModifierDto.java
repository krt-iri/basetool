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
 * @param propertyKey internal stat key (e.g. {@code "weapon_damage"})
 * @param label human-readable stat name (e.g. {@code "Impact Force"})
 * @param betterWhen whether a higher / lower / neutral value is desirable
 * @param qualityMin lowest ingredient-quality value of the interpolation band
 * @param qualityMax highest ingredient-quality value of the interpolation band
 * @param modifierAtMinQuality stat multiplier at the minimum quality
 * @param modifierAtMaxQuality stat multiplier at the maximum quality
 * @param valueRangeType interpolation type between the endpoints (e.g. {@code "linear"})
 * @param segments per-segment ranges of a stepped / piecewise-linear modifier; empty for the simple
 *     linear form
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
    List<BlueprintRequirementModifierSegmentDto> segments) {}

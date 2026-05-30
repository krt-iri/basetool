package de.greluc.krt.iri.basetool.backend.model.dto;

/**
 * Boundary DTO for one segment of a stepped / piecewise-linear modifier curve. Surfaces the
 * persisted {@code blueprint_modifier_segment} so the admin blueprint slider can compute the real
 * stat value at any quality: within {@link #qualityMin}..{@link #qualityMax} the value interpolates
 * linearly from {@link #modifierAtStart} to {@link #modifierAtEnd}.
 *
 * @param qualityMin the segment's start ingredient quality
 * @param qualityMax the segment's end ingredient quality
 * @param modifierAtStart the stat multiplier at {@link #qualityMin}
 * @param modifierAtEnd the stat multiplier at {@link #qualityMax}
 */
public record BlueprintRequirementModifierSegmentDto(
    Double qualityMin, Double qualityMax, Double modifierAtStart, Double modifierAtEnd) {}

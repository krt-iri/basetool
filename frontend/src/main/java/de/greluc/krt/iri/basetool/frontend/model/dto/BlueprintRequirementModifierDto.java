package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/**
 * Frontend mirror of the backend blueprint requirement-modifier DTO (a per-slot stat contribution).
 * Carries the endpoint band ({@code qualityMin..qualityMax} &rarr; {@code
 * modifierAtMin/MaxQuality}) plus, for stepped stats, the ordered {@code segments} the slider
 * interpolates within.
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

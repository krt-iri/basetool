package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/**
 * Frontend mirror of the backend blueprint requirement-modifier DTO (a per-slot stat contribution).
 * Carries the raw endpoint band ({@code qualityMin..qualityMax} &rarr; {@code
 * modifierAtMin/MaxQuality}) plus, for stepped stats, the ordered {@code segments} the slider
 * interpolates within. The slider extents use {@code effectiveQualityMin..effectiveQualityMax} (the
 * union of the segment bounds when stepped, else the raw pair) so the track spans the full covered
 * range — the raw pair only reflects the first segment for stepped modifiers.
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

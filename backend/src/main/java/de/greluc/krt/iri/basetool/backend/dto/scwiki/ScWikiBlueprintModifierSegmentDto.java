package de.greluc.krt.iri.basetool.backend.dto.scwiki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One linear segment of a multi-step (piecewise-linear) {@link ScWikiBlueprintModifierDto} (SC Wiki
 * {@code blueprint_modifier.value_segments[]}). Within {@link #qualityMin}..{@link #qualityMax} the
 * stat multiplier interpolates linearly from {@link #modifierAtStart} (at {@code qualityMin}) to
 * {@link #modifierAtEnd} (at {@code qualityMax}); chaining the contiguous segments reproduces a
 * stepped / non-linear quality&rarr;stat curve that the single {@code modifier_range} pair cannot
 * express. The SC Wiki schema types the quality bounds as integers; they are bound as {@code
 * Double} to share the linear-interpolation maths with {@link ScWikiBlueprintQualityRangeDto}.
 *
 * @param qualityMin the segment's start ingredient quality, or {@code null}
 * @param qualityMax the segment's end ingredient quality, or {@code null}
 * @param modifierAtStart the stat multiplier at {@link #qualityMin}, or {@code null}
 * @param modifierAtEnd the stat multiplier at {@link #qualityMax}, or {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScWikiBlueprintModifierSegmentDto(
    @JsonProperty("quality_min") Double qualityMin,
    @JsonProperty("quality_max") Double qualityMax,
    @JsonProperty("modifier_at_start") Double modifierAtStart,
    @JsonProperty("modifier_at_end") Double modifierAtEnd) {}

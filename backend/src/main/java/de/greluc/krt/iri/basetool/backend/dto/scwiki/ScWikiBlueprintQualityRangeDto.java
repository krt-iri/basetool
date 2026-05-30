package de.greluc.krt.iri.basetool.backend.dto.scwiki;

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

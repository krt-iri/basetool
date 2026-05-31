package de.greluc.krt.iri.basetool.backend.dto.scwiki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The crafted-item stat multiplier endpoints of a {@link ScWikiBlueprintModifierDto} (SC Wiki
 * {@code blueprint_modifier_range}). The value applied to the output stat interpolates from {@link
 * #atMinQuality} (at the bottom of the modifier's quality band) to {@link #atMaxQuality} (at the
 * top); e.g. {@code 0.95 .. 1.05} means the stat ranges from −5% to +5% depending on the consumed
 * ingredient's quality.
 *
 * @param atMinQuality multiplier applied at the minimum ingredient quality, or {@code null}
 * @param atMaxQuality multiplier applied at the maximum ingredient quality, or {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScWikiBlueprintModifierRangeDto(
    @JsonProperty("at_min_quality") Double atMinQuality,
    @JsonProperty("at_max_quality") Double atMaxQuality) {}

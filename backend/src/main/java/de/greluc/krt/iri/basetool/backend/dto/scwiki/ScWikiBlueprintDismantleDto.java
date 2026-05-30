package de.greluc.krt.iri.basetool.backend.dto.scwiki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Dismantle metadata of a blueprint (SC Wiki {@code blueprint_dismantle}): how long dismantling the
 * crafted item takes and what fraction of the inputs is recovered. The recovered commodities
 * themselves are carried separately in {@code dismantle_returns[]}. Present only on the blueprint
 * detail response.
 *
 * @param timeSeconds dismantle duration in seconds, or {@code null}
 * @param efficiency fraction of inputs recovered on dismantle (e.g. {@code 0.5} = 50%), or {@code
 *     null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScWikiBlueprintDismantleDto(
    @JsonProperty("time_seconds") Integer timeSeconds,
    @JsonProperty("efficiency") Double efficiency) {}

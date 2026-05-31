package de.greluc.krt.iri.basetool.backend.dto.scwiki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * One aggregated stat a blueprint affects across all its requirement groups (SC Wiki {@code
 * blueprint_summary_property}). A compact, de-duplicated roll-up of the {@link
 * ScWikiBlueprintModifierDto} property keys, used to badge a blueprint with the stats it influences
 * without expanding every group. Present only on the blueprint detail response.
 *
 * @param propertyKey internal stat key (e.g. {@code "weapon_damage"}), or {@code null}
 * @param propertyUuid UUID of the property definition, or {@code null}
 * @param label human-readable stat name (e.g. {@code "Impact Force"}), or {@code null}
 * @param betterWhen whether a higher or lower value is desirable, or {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScWikiBlueprintSummaryPropertyDto(
    @JsonProperty("property_key") String propertyKey,
    @JsonProperty("property_uuid") UUID propertyUuid,
    @JsonProperty("label") String label,
    @JsonProperty("better_when") String betterWhen) {}

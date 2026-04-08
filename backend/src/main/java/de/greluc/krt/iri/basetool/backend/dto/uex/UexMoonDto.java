package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record UexMoonDto(
    @JsonProperty("is_available") Integer isAvailable,
    @JsonProperty("is_visible") Integer isVisible,
    @JsonProperty("is_default") Integer isDefault,
    @JsonProperty("id") Integer id,
    @JsonProperty("name") String name,
    @JsonProperty("code") String code,
    @JsonProperty("is_available_live") Integer isAvailableLive,
    @JsonProperty("star_system_name") String starSystemName,
    @JsonProperty("planet_name") String planetName,
    @JsonProperty("orbit_name") String orbitName,
    @JsonProperty("faction_name") String factionName,
    @JsonProperty("jurisdiction_name") String jurisdictionName
) {
    public Boolean checkIsAvailableLive() {
        return isAvailableLive != null && isAvailableLive == 1;
    }
}

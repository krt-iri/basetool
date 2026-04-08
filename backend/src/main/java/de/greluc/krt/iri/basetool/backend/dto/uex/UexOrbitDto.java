package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record UexOrbitDto(
    @JsonProperty("id") Integer id,
    @JsonProperty("name") String name,
    @JsonProperty("code") String code,
    @JsonProperty("is_available_live") Integer isAvailableLive,
    @JsonProperty("star_system_name") String starSystemName,
    @JsonProperty("faction_name") String factionName,
    @JsonProperty("jurisdiction_name") String jurisdictionName
) {
    public Boolean checkIsAvailableLive() {
        return isAvailableLive != null && isAvailableLive == 1;
    }
}

package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record UexJurisdictionDto(
    @JsonProperty("id") Integer id,
    @JsonProperty("name") String name,
    @JsonProperty("code") String code,
    @JsonProperty("is_available_live") Integer isAvailableLive,
    @JsonProperty("nickname") String nickname,
    @JsonProperty("wiki") String wiki,
    @JsonProperty("faction_name") String factionName) {
  public Boolean checkIsAvailableLive() {
    return isAvailableLive != null && isAvailableLive == 1;
  }
}

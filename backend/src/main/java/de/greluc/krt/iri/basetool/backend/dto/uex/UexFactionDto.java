package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record UexFactionDto(
    @JsonProperty("id") Integer id,
    @JsonProperty("name") String name,
    @JsonProperty("code") String code,
    @JsonProperty("is_available_live") Integer isAvailableLive,
    @JsonProperty("wiki") String wiki,
    @JsonProperty("is_piracy") Integer isPiracy,
    @JsonProperty("is_bounty_hunting") Integer isBountyHunting) {
  public Boolean checkIsAvailableLive() {
    return isAvailableLive != null && isAvailableLive == 1;
  }

  public Boolean checkIsPiracy() {
    return isPiracy != null && isPiracy == 1;
  }

  public Boolean checkIsBountyHunting() {
    return isBountyHunting != null && isBountyHunting == 1;
  }
}

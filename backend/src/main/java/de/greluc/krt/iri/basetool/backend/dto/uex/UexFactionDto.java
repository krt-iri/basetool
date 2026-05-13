package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Inbound JSON record for UEX Corp's <code>/factions</code> endpoint. Mapped to the project's own
 * {@code Faction} entity by {@code UexUniverseSyncService}; downstream code consumes the entity,
 * not this DTO.
 */
@Builder
public record UexFactionDto(
    @JsonProperty("id") Integer id,
    @JsonProperty("name") String name,
    @JsonProperty("code") String code,
    @JsonProperty("is_available_live") Integer isAvailableLive,
    @JsonProperty("wiki") String wiki,
    @JsonProperty("is_piracy") Integer isPiracy,
    @JsonProperty("is_bounty_hunting") Integer isBountyHunting) {
  /** Returns {@code true} iff UEX reports the faction as currently active in-game. */
  public Boolean checkIsAvailableLive() {
    return isAvailableLive != null && isAvailableLive == 1;
  }

  /** Returns {@code true} iff the faction is flagged as piracy-aligned by UEX. */
  public Boolean checkIsPiracy() {
    return isPiracy != null && isPiracy == 1;
  }

  /** Returns {@code true} iff the faction is flagged as bounty-hunting by UEX. */
  public Boolean checkIsBountyHunting() {
    return isBountyHunting != null && isBountyHunting == 1;
  }
}

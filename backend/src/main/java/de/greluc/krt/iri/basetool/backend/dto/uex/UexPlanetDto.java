package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Inbound JSON record for UEX Corp's <code>/planets</code> endpoint. Mapped to the project's own
 * {@code Planet} entity by {@code UexUniverseSyncService}; downstream code consumes the entity, not
 * this DTO.
 */
@Builder
public record UexPlanetDto(
    @JsonProperty("is_available") Integer isAvailable,
    @JsonProperty("is_visible") Integer isVisible,
    @JsonProperty("is_default") Integer isDefault,
    @JsonProperty("id") Integer id,
    @JsonProperty("name") String name,
    @JsonProperty("code") String code,
    @JsonProperty("is_available_live") Integer isAvailableLive,
    @JsonProperty("star_system_name") String starSystemName,
    @JsonProperty("faction_name") String factionName,
    @JsonProperty("jurisdiction_name") String jurisdictionName) {
  /** Returns {@code true} iff UEX reports the planet as currently reachable in-game. */
  public Boolean checkIsAvailableLive() {
    return isAvailableLive != null && isAvailableLive == 1;
  }
}

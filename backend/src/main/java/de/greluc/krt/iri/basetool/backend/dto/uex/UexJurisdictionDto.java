package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Inbound JSON record for UEX Corp's <code>/jurisdictions</code> endpoint. Mapped to the project's
 * own {@code Jurisdiction} entity by {@code UexUniverseSyncService}; downstream code consumes the
 * entity, not this DTO.
 */
@Builder
public record UexJurisdictionDto(
    @JsonProperty("id") Integer id,
    @JsonProperty("name") String name,
    @JsonProperty("code") String code,
    @JsonProperty("is_available_live") Integer isAvailableLive,
    @JsonProperty("nickname") String nickname,
    @JsonProperty("wiki") String wiki,
    @JsonProperty("faction_name") String factionName) {
  /** Returns {@code true} iff UEX reports the jurisdiction as currently active in-game. */
  public Boolean checkIsAvailableLive() {
    return isAvailableLive != null && isAvailableLive == 1;
  }
}

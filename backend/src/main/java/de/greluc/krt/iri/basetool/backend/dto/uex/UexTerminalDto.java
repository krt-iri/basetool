package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Inbound JSON record for UEX Corp's <code>/terminals</code> endpoint. Mapped to the project's own
 * {@code Terminal} entity by {@code UexUniverseSyncService}; downstream code consumes the entity,
 * not this DTO.
 */
@Builder
public record UexTerminalDto(
    @JsonProperty("is_available") Integer isAvailable,
    @JsonProperty("is_visible") Integer isVisible,
    @JsonProperty("is_jump_point") Integer isJumpPoint,
    @JsonProperty("has_loading_dock") Integer hasLoadingDock,
    @JsonProperty("has_docking_port") Integer hasDockingPort,
    @JsonProperty("has_freight_elevator") Integer hasFreightElevator,
    @JsonProperty("is_auto_load") Integer isAutoLoad,
    @JsonProperty("id") Integer id,
    @JsonProperty("name") String name,
    @JsonProperty("code") String code,
    @JsonProperty("is_available_live") Integer isAvailableLive,
    @JsonProperty("nickname") String nickname,
    @JsonProperty("star_system_name") String starSystemName,
    @JsonProperty("planet_name") String planetName,
    @JsonProperty("orbit_name") String orbitName,
    @JsonProperty("moon_name") String moonName,
    @JsonProperty("space_station_name") String spaceStationName,
    @JsonProperty("outpost_name") String outpostName,
    @JsonProperty("city_name") String cityName,
    @JsonProperty("faction_name") String factionName,
    @JsonProperty("company_name") String companyName) {
  /** Returns {@code true} iff UEX reports the terminal as currently reachable in-game. */
  public Boolean checkIsAvailableLive() {
    return isAvailableLive != null && isAvailableLive == 1;
  }
}

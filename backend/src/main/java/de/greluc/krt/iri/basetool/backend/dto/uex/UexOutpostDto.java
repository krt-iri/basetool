package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record UexOutpostDto(
    @JsonProperty("is_available") Integer isAvailable,
    @JsonProperty("is_visible") Integer isVisible,
    @JsonProperty("is_default") Integer isDefault,
    @JsonProperty("is_monitored") Integer isMonitored,
    @JsonProperty("is_armistice") Integer isArmistice,
    @JsonProperty("is_landable") Integer isLandable,
    @JsonProperty("is_decommissioned") Integer isDecommissioned,
    @JsonProperty("has_quantum_marker") Integer hasQuantumMarker,
    @JsonProperty("has_trade_terminal") Integer hasTradeTerminal,
    @JsonProperty("has_habitation") Integer hasHabitation,
    @JsonProperty("has_refinery") Integer hasRefinery,
    @JsonProperty("has_cargo_center") Integer hasCargoCenter,
    @JsonProperty("has_clinic") Integer hasClinic,
    @JsonProperty("has_food") Integer hasFood,
    @JsonProperty("has_shops") Integer hasShops,
    @JsonProperty("has_refuel") Integer hasRefuel,
    @JsonProperty("has_repair") Integer hasRepair,
    @JsonProperty("has_gravity") Integer hasGravity,
    @JsonProperty("has_loading_dock") Integer hasLoadingDock,
    @JsonProperty("has_docking_port") Integer hasDockingPort,
    @JsonProperty("has_freight_elevator") Integer hasFreightElevator,
    @JsonProperty("pad_types") String padTypes,
    @JsonProperty("id") Integer id,
    @JsonProperty("name") String name,
    @JsonProperty("code") String code,
    @JsonProperty("is_available_live") Integer isAvailableLive,
    @JsonProperty("nickname") String nickname,
    @JsonProperty("star_system_name") String starSystemName,
    @JsonProperty("planet_name") String planetName,
    @JsonProperty("orbit_name") String orbitName,
    @JsonProperty("moon_name") String moonName,
    @JsonProperty("faction_name") String factionName,
    @JsonProperty("jurisdiction_name") String jurisdictionName
) {
    public Boolean checkIsAvailableLive() {
        return isAvailableLive != null && isAvailableLive == 1;
    }
}

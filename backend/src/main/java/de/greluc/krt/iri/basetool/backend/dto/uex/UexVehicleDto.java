package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound JSON record for UEX Corp's <code>/vehicles</code> endpoint. Mapped to the project's own
 * {@code ShipType} entity by {@code UexVehicleService}; downstream code consumes the entity, not
 * this DTO.
 */
public record UexVehicleDto(
    @JsonProperty("name") String name,
    @JsonProperty("name_full") String nameFull,
    @JsonProperty("company_name") String companyName,
    @JsonProperty("scu") Integer scu,
    @JsonProperty("crew") String crew,
    @JsonProperty("url_store") String urlStore,
    @JsonProperty("url_wiki") String urlWiki,
    @JsonProperty("is_spaceship") Integer isSpaceship,
    @JsonProperty("is_ground_vehicle") Integer isGroundVehicle) {}

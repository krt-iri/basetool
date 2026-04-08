package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UexVehicleDto(
        @JsonProperty("name") String name,
        @JsonProperty("name_full") String nameFull,
        @JsonProperty("company_name") String companyName,
        @JsonProperty("scu") Integer scu,
        @JsonProperty("crew") String crew,
        @JsonProperty("url_store") String urlStore,
        @JsonProperty("url_wiki") String urlWiki,
        @JsonProperty("is_spaceship") Integer isSpaceship,
        @JsonProperty("is_ground_vehicle") Integer isGroundVehicle
) {
}

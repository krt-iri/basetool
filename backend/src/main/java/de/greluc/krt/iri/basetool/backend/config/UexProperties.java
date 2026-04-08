package de.greluc.krt.iri.basetool.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@EnableScheduling
@EnableAsync
@ConfigurationProperties(prefix = "krt.uex")
public class UexProperties {

    @NotBlank
    private String apiUrl = "https://api.uexcorp.space/2.0";

    @NotBlank
    private String commoditiesEndpoint = "/commodities";

    @NotBlank
    private String commoditiesPricesEndpoint = "/commodities_prices_all";

    @NotBlank
    private String starSystemsEndpoint = "/star_systems";

    @NotBlank
    private String companiesEndpoint = "/companies";

    @NotBlank
    private String vehiclesEndpoint = "/vehicles";

    @NotBlank
    private String citiesEndpoint = "/cities";

    @NotBlank
    private String factionsEndpoint = "/factions";

    @NotBlank
    private String jurisdictionsEndpoint = "/jurisdictions";

    @NotBlank
    private String moonsEndpoint = "/moons";

    @NotBlank
    private String orbitsEndpoint = "/orbits";

    @NotBlank
    private String outpostsEndpoint = "/outposts";

    @NotBlank
    private String planetsEndpoint = "/planets";

    @NotBlank
    private String poiEndpoint = "/poi";

    @NotBlank
    private String spaceStationsEndpoint = "/space_stations";

    @NotBlank
    private String terminalsEndpoint = "/terminals";

    @NotBlank
    private String refineriesMethodsEndpoint = "/refineries_methods";

    @NotBlank
    private String refineriesYieldsEndpoint = "/refineries_yields";

    @NotNull
    private Boolean schedulerEnabled = true;

    @NotBlank
    private String schedulerDelay = "3600000";

}

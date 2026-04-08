package de.greluc.krt.iri.basetool.backend.integration;

import de.greluc.krt.iri.basetool.backend.config.UexProperties;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexCommodityDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexCommodityPriceDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexCompanyDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexResponseDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexStarSystemDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexVehicleDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexCityDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexFactionDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexJurisdictionDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexMoonDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexOrbitDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexOutpostDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexPlanetDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexPoiDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexRefiningMethodDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexRefineryYieldDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexSpaceStationDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexTerminalDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UexClient {

    private final WebClient.Builder webClientBuilder;
    private final UexProperties uexProperties;

    public List<UexCommodityDto> getCommodities() {
        log.info("Fetching all commodities from UEX API");
        
        WebClient client = webClientBuilder
                .baseUrl(uexProperties.getApiUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return client.get()
                .uri(uexProperties.getCommoditiesEndpoint())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexCommodityDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("Failed to fetch commodities from UEX API", e);
                    return Mono.just(UexResponseDto.<UexCommodityDto>builder().data(Collections.emptyList()).build());
                })
                .blockOptional()
                .map(UexResponseDto::data)
                .orElse(Collections.emptyList());
    }

    public List<UexCommodityPriceDto> getCommoditiesPricesAll() {
        log.info("Fetching all commodities prices from UEX API");
        
        WebClient client = webClientBuilder
                .baseUrl(uexProperties.getApiUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return client.get()
                .uri(uexProperties.getCommoditiesPricesEndpoint())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexCommodityPriceDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("Failed to fetch commodities prices from UEX API", e);
                    return Mono.just(UexResponseDto.<UexCommodityPriceDto>builder().data(Collections.emptyList()).build());
                })
                .blockOptional()
                .map(UexResponseDto::data)
                .orElse(Collections.emptyList());
    }

    public List<UexStarSystemDto> getStarSystems() {
        log.info("Fetching all star systems from UEX API");
        
        WebClient client = webClientBuilder
                .baseUrl(uexProperties.getApiUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return client.get()
                .uri(uexProperties.getStarSystemsEndpoint())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexStarSystemDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("Failed to fetch star systems from UEX API", e);
                    return Mono.just(UexResponseDto.<UexStarSystemDto>builder().data(Collections.emptyList()).build());
                })
                .blockOptional()
                .map(UexResponseDto::data)
                .orElse(Collections.emptyList());
    }
    public List<UexCompanyDto> getCompanies() {
        log.info("Fetching all companies from UEX API");

        WebClient client = webClientBuilder
                .baseUrl(uexProperties.getApiUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return client.get()
                .uri(uexProperties.getCompaniesEndpoint())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexCompanyDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("Failed to fetch companies from UEX API", e);
                    return Mono.just(UexResponseDto.<UexCompanyDto>builder().data(Collections.emptyList()).build());
                })
                .blockOptional()
                .map(UexResponseDto::data)
                .orElse(Collections.emptyList());
    }

    public List<UexVehicleDto> getVehicles() {
        log.info("Fetching all vehicles from UEX API");

        WebClient client = webClientBuilder
                .baseUrl(uexProperties.getApiUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return client.get()
                .uri(uexProperties.getVehiclesEndpoint())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexVehicleDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("Failed to fetch vehicles from UEX API", e);
                    return Mono.just(UexResponseDto.<UexVehicleDto>builder().data(Collections.emptyList()).build());
                })
                .blockOptional()
                .map(UexResponseDto::data)
                .orElse(Collections.emptyList());
    }

    public List<UexCityDto> getCities() {
        log.info("Fetching all citys from UEX API");

        WebClient client = webClientBuilder
                .baseUrl(uexProperties.getApiUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return client.get()
                .uri(uexProperties.getCitiesEndpoint())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexCityDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("Failed to fetch citys from UEX API", e);
                    return Mono.just(UexResponseDto.<UexCityDto>builder().data(Collections.emptyList()).build());
                })
                .blockOptional()
                .map(UexResponseDto::data)
                .orElse(Collections.emptyList());
    }

    public List<UexFactionDto> getFactions() {
        log.info("Fetching all factions from UEX API");

        WebClient client = webClientBuilder
                .baseUrl(uexProperties.getApiUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return client.get()
                .uri(uexProperties.getFactionsEndpoint())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexFactionDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("Failed to fetch factions from UEX API", e);
                    return Mono.just(UexResponseDto.<UexFactionDto>builder().data(Collections.emptyList()).build());
                })
                .blockOptional()
                .map(UexResponseDto::data)
                .orElse(Collections.emptyList());
    }

    public List<UexJurisdictionDto> getJurisdictions() {
        log.info("Fetching all jurisdictions from UEX API");

        WebClient client = webClientBuilder
                .baseUrl(uexProperties.getApiUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return client.get()
                .uri(uexProperties.getJurisdictionsEndpoint())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexJurisdictionDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("Failed to fetch jurisdictions from UEX API", e);
                    return Mono.just(UexResponseDto.<UexJurisdictionDto>builder().data(Collections.emptyList()).build());
                })
                .blockOptional()
                .map(UexResponseDto::data)
                .orElse(Collections.emptyList());
    }

    public List<UexMoonDto> getMoons() {
        log.info("Fetching all moons from UEX API");

        WebClient client = webClientBuilder
                .baseUrl(uexProperties.getApiUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return client.get()
                .uri(uexProperties.getMoonsEndpoint())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexMoonDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("Failed to fetch moons from UEX API", e);
                    return Mono.just(UexResponseDto.<UexMoonDto>builder().data(Collections.emptyList()).build());
                })
                .blockOptional()
                .map(UexResponseDto::data)
                .orElse(Collections.emptyList());
    }

    public List<UexOrbitDto> getOrbits() {
        log.info("Fetching all orbits from UEX API");

        WebClient client = webClientBuilder
                .baseUrl(uexProperties.getApiUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return client.get()
                .uri(uexProperties.getOrbitsEndpoint())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexOrbitDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("Failed to fetch orbits from UEX API", e);
                    return Mono.just(UexResponseDto.<UexOrbitDto>builder().data(Collections.emptyList()).build());
                })
                .blockOptional()
                .map(UexResponseDto::data)
                .orElse(Collections.emptyList());
    }

    public List<UexOutpostDto> getOutposts() {
        log.info("Fetching all outposts from UEX API");

        WebClient client = webClientBuilder
                .baseUrl(uexProperties.getApiUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return client.get()
                .uri(uexProperties.getOutpostsEndpoint())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexOutpostDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("Failed to fetch outposts from UEX API", e);
                    return Mono.just(UexResponseDto.<UexOutpostDto>builder().data(Collections.emptyList()).build());
                })
                .blockOptional()
                .map(UexResponseDto::data)
                .orElse(Collections.emptyList());
    }

    public List<UexPlanetDto> getPlanets() {
        log.info("Fetching all planets from UEX API");

        WebClient client = webClientBuilder
                .baseUrl(uexProperties.getApiUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return client.get()
                .uri(uexProperties.getPlanetsEndpoint())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexPlanetDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("Failed to fetch planets from UEX API", e);
                    return Mono.just(UexResponseDto.<UexPlanetDto>builder().data(Collections.emptyList()).build());
                })
                .blockOptional()
                .map(UexResponseDto::data)
                .orElse(Collections.emptyList());
    }

    public List<UexPoiDto> getPoi() {
        log.info("Fetching all pois from UEX API");

        WebClient client = webClientBuilder
                .baseUrl(uexProperties.getApiUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return client.get()
                .uri(uexProperties.getPoiEndpoint())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexPoiDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("Failed to fetch pois from UEX API", e);
                    return Mono.just(UexResponseDto.<UexPoiDto>builder().data(Collections.emptyList()).build());
                })
                .blockOptional()
                .map(UexResponseDto::data)
                .orElse(Collections.emptyList());
    }

    public List<UexSpaceStationDto> getSpaceStations() {
        log.info("Fetching all spacestations from UEX API");

        WebClient client = webClientBuilder
                .baseUrl(uexProperties.getApiUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return client.get()
                .uri(uexProperties.getSpaceStationsEndpoint())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexSpaceStationDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("Failed to fetch spacestations from UEX API", e);
                    return Mono.just(UexResponseDto.<UexSpaceStationDto>builder().data(Collections.emptyList()).build());
                })
                .blockOptional()
                .map(UexResponseDto::data)
                .orElse(Collections.emptyList());
    }

    public List<UexTerminalDto> getTerminals() {
        log.info("Fetching all terminals from UEX API");

        WebClient client = webClientBuilder
                .baseUrl(uexProperties.getApiUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return client.get()
                .uri(uexProperties.getTerminalsEndpoint())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexTerminalDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("Failed to fetch terminals from UEX API", e);
                    return Mono.just(UexResponseDto.<UexTerminalDto>builder().data(Collections.emptyList()).build());
                })
                .blockOptional()
                .map(UexResponseDto::data)
                .orElse(Collections.emptyList());
    }

    public List<UexRefiningMethodDto> getRefineriesMethods() {
        log.info("Fetching all refineries methods from UEX API");

        WebClient client = webClientBuilder
                .baseUrl(uexProperties.getApiUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return client.get()
                .uri(uexProperties.getRefineriesMethodsEndpoint())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexRefiningMethodDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("Failed to fetch refineries methods from UEX API", e);
                    return Mono.just(UexResponseDto.<UexRefiningMethodDto>builder().data(Collections.emptyList()).build());
                })
                .blockOptional()
                .map(UexResponseDto::data)
                .orElse(Collections.emptyList());
    }

    public List<UexRefineryYieldDto> getRefineriesYields() {
        log.info("Fetching all refineries yields from UEX API");

        WebClient client = webClientBuilder
                .baseUrl(uexProperties.getApiUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return client.get()
                .uri(uexProperties.getRefineriesYieldsEndpoint())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexRefineryYieldDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("Failed to fetch refineries yields from UEX API", e);
                    return Mono.just(UexResponseDto.<UexRefineryYieldDto>builder().data(Collections.emptyList()).build());
                })
                .blockOptional()
                .map(UexResponseDto::data)
                .orElse(Collections.emptyList());
    }
}

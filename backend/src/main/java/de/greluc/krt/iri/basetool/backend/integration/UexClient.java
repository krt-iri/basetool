package de.greluc.krt.iri.basetool.backend.integration;

import de.greluc.krt.iri.basetool.backend.config.UexProperties;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexCityDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexCommodityDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexCommodityPriceDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexCompanyDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexFactionDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexJurisdictionDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexMoonDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexOrbitDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexOutpostDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexPlanetDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexPoiDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexRefineryYieldDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexRefiningMethodDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexResponseDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexSpaceStationDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexStarSystemDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexTerminalDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexVehicleDto;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Read-only HTTP client for the UEX (uexcorp.space) catalog API.
 *
 * <p>Every public {@code get…()} method follows the same pattern: a {@code GET} to the path
 * declared in {@link UexProperties}, a 30-second per-call timeout, JSON-decode into the matching
 * {@code UexResponseDto<UexXxxDto>}, and on ANY error return an empty list — the calling sync
 * services treat an empty payload as "skip this run" and explicitly never wipe local tables based
 * on it (see the UEX sync services for the rationale). This keeps a transient upstream outage from
 * truncating the local catalog.
 *
 * <p>The reactive {@code Mono} chain is collapsed with {@code blockOptional()} because all callers
 * are scheduled background tasks that have nothing else to do while waiting — synchronous code here
 * is simpler than threading the reactive type through every service method.
 *
 * <p>The WebClient is built once in {@link #initClient()} after dependency injection so the
 * underlying Reactor-Netty connection pool is actually shared across requests. {@code
 * maxInMemorySize(16 MB)} raises Spring's default 256 KB ceiling because the {@code
 * commodities_prices_all} response routinely exceeds 1 MB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UexClient {

  private final WebClient.Builder webClientBuilder;
  private final UexProperties uexProperties;

  /**
   * Reusable WebClient bound to the UEX base URL. Built once after dependency injection completes
   * (instead of per call) so the underlying connection pool is actually shared across requests. The
   * Reactor-Netty HttpClient already carries the connect / read / write timeouts configured in
   * WebClientConfig.
   */
  private WebClient client;

  /**
   * Builds the {@link WebClient} after dependency injection. Done once in {@code @PostConstruct}
   * instead of lazily per call so the Reactor-Netty connection pool from {@link
   * de.greluc.krt.iri.basetool.backend.config.WebClientConfig} is reused across all UEX requests
   * for the lifetime of the application.
   */
  @PostConstruct
  void initClient() {
    this.client =
        webClientBuilder
            .baseUrl(uexProperties.getApiUrl())
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();
  }

  /**
   * Fetches the full UEX commodity catalog. See class Javadoc for the shared pattern.
   *
   * @return all commodities, or an empty list if the upstream call fails or times out
   */
  public List<UexCommodityDto> getCommodities() {
    log.info("Fetching all commodities from UEX API");

    return client
        .get()
        .uri(uexProperties.getCommoditiesEndpoint())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexCommodityDto>>() {})
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(
            e -> {
              log.error("Failed to fetch commodities from UEX API", e);
              return Mono.just(
                  UexResponseDto.<UexCommodityDto>builder().data(Collections.emptyList()).build());
            })
        .blockOptional()
        .map(UexResponseDto::data)
        .orElse(Collections.emptyList());
  }

  /**
   * Fetches the full UEX commodity-price matrix (every commodity × every terminal). This is the
   * largest UEX payload by far ({@literal >}1 MB) — see the {@code maxInMemorySize} in {@link
   * #initClient()}.
   *
   * @return all commodity prices, or an empty list on error
   */
  public List<UexCommodityPriceDto> getCommoditiesPricesAll() {
    log.info("Fetching all commodities prices from UEX API");

    return client
        .get()
        .uri(uexProperties.getCommoditiesPricesEndpoint())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexCommodityPriceDto>>() {})
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(
            e -> {
              log.error("Failed to fetch commodities prices from UEX API", e);
              return Mono.just(
                  UexResponseDto.<UexCommodityPriceDto>builder()
                      .data(Collections.emptyList())
                      .build());
            })
        .blockOptional()
        .map(UexResponseDto::data)
        .orElse(Collections.emptyList());
  }

  /**
   * @return all star systems, or an empty list on error
   */
  public List<UexStarSystemDto> getStarSystems() {
    log.info("Fetching all star systems from UEX API");

    return client
        .get()
        .uri(uexProperties.getStarSystemsEndpoint())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexStarSystemDto>>() {})
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(
            e -> {
              log.error("Failed to fetch star systems from UEX API", e);
              return Mono.just(
                  UexResponseDto.<UexStarSystemDto>builder().data(Collections.emptyList()).build());
            })
        .blockOptional()
        .map(UexResponseDto::data)
        .orElse(Collections.emptyList());
  }

  /**
   * @return all companies (in-universe manufacturers), or an empty list on error
   */
  public List<UexCompanyDto> getCompanies() {
    log.info("Fetching all companies from UEX API");

    return client
        .get()
        .uri(uexProperties.getCompaniesEndpoint())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexCompanyDto>>() {})
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(
            e -> {
              log.error("Failed to fetch companies from UEX API", e);
              return Mono.just(
                  UexResponseDto.<UexCompanyDto>builder().data(Collections.emptyList()).build());
            })
        .blockOptional()
        .map(UexResponseDto::data)
        .orElse(Collections.emptyList());
  }

  /**
   * @return all vehicles (ships and ground vehicles), or an empty list on error
   */
  public List<UexVehicleDto> getVehicles() {
    log.info("Fetching all vehicles from UEX API");

    return client
        .get()
        .uri(uexProperties.getVehiclesEndpoint())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexVehicleDto>>() {})
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(
            e -> {
              log.error("Failed to fetch vehicles from UEX API", e);
              return Mono.just(
                  UexResponseDto.<UexVehicleDto>builder().data(Collections.emptyList()).build());
            })
        .blockOptional()
        .map(UexResponseDto::data)
        .orElse(Collections.emptyList());
  }

  /**
   * @return all cities, or an empty list on error
   */
  public List<UexCityDto> getCities() {
    log.info("Fetching all citys from UEX API");

    return client
        .get()
        .uri(uexProperties.getCitiesEndpoint())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexCityDto>>() {})
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(
            e -> {
              log.error("Failed to fetch citys from UEX API", e);
              return Mono.just(
                  UexResponseDto.<UexCityDto>builder().data(Collections.emptyList()).build());
            })
        .blockOptional()
        .map(UexResponseDto::data)
        .orElse(Collections.emptyList());
  }

  /**
   * @return all in-universe factions, or an empty list on error
   */
  public List<UexFactionDto> getFactions() {
    log.info("Fetching all factions from UEX API");

    return client
        .get()
        .uri(uexProperties.getFactionsEndpoint())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexFactionDto>>() {})
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(
            e -> {
              log.error("Failed to fetch factions from UEX API", e);
              return Mono.just(
                  UexResponseDto.<UexFactionDto>builder().data(Collections.emptyList()).build());
            })
        .blockOptional()
        .map(UexResponseDto::data)
        .orElse(Collections.emptyList());
  }

  /**
   * @return all jurisdictions (legal authorities covering a system or region), or an empty list on
   *     error
   */
  public List<UexJurisdictionDto> getJurisdictions() {
    log.info("Fetching all jurisdictions from UEX API");

    return client
        .get()
        .uri(uexProperties.getJurisdictionsEndpoint())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexJurisdictionDto>>() {})
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(
            e -> {
              log.error("Failed to fetch jurisdictions from UEX API", e);
              return Mono.just(
                  UexResponseDto.<UexJurisdictionDto>builder()
                      .data(Collections.emptyList())
                      .build());
            })
        .blockOptional()
        .map(UexResponseDto::data)
        .orElse(Collections.emptyList());
  }

  /**
   * @return all moons, or an empty list on error
   */
  public List<UexMoonDto> getMoons() {
    log.info("Fetching all moons from UEX API");

    return client
        .get()
        .uri(uexProperties.getMoonsEndpoint())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexMoonDto>>() {})
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(
            e -> {
              log.error("Failed to fetch moons from UEX API", e);
              return Mono.just(
                  UexResponseDto.<UexMoonDto>builder().data(Collections.emptyList()).build());
            })
        .blockOptional()
        .map(UexResponseDto::data)
        .orElse(Collections.emptyList());
  }

  /**
   * @return all orbital locations, or an empty list on error
   */
  public List<UexOrbitDto> getOrbits() {
    log.info("Fetching all orbits from UEX API");

    return client
        .get()
        .uri(uexProperties.getOrbitsEndpoint())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexOrbitDto>>() {})
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(
            e -> {
              log.error("Failed to fetch orbits from UEX API", e);
              return Mono.just(
                  UexResponseDto.<UexOrbitDto>builder().data(Collections.emptyList()).build());
            })
        .blockOptional()
        .map(UexResponseDto::data)
        .orElse(Collections.emptyList());
  }

  /**
   * @return all outposts, or an empty list on error
   */
  public List<UexOutpostDto> getOutposts() {
    log.info("Fetching all outposts from UEX API");

    return client
        .get()
        .uri(uexProperties.getOutpostsEndpoint())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexOutpostDto>>() {})
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(
            e -> {
              log.error("Failed to fetch outposts from UEX API", e);
              return Mono.just(
                  UexResponseDto.<UexOutpostDto>builder().data(Collections.emptyList()).build());
            })
        .blockOptional()
        .map(UexResponseDto::data)
        .orElse(Collections.emptyList());
  }

  /**
   * @return all planets, or an empty list on error
   */
  public List<UexPlanetDto> getPlanets() {
    log.info("Fetching all planets from UEX API");

    return client
        .get()
        .uri(uexProperties.getPlanetsEndpoint())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexPlanetDto>>() {})
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(
            e -> {
              log.error("Failed to fetch planets from UEX API", e);
              return Mono.just(
                  UexResponseDto.<UexPlanetDto>builder().data(Collections.emptyList()).build());
            })
        .blockOptional()
        .map(UexResponseDto::data)
        .orElse(Collections.emptyList());
  }

  /**
   * @return all points of interest (Lagrange points, derelicts, anomalies), or an empty list on
   *     error
   */
  public List<UexPoiDto> getPoi() {
    log.info("Fetching all pois from UEX API");

    return client
        .get()
        .uri(uexProperties.getPoiEndpoint())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexPoiDto>>() {})
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(
            e -> {
              log.error("Failed to fetch pois from UEX API", e);
              return Mono.just(
                  UexResponseDto.<UexPoiDto>builder().data(Collections.emptyList()).build());
            })
        .blockOptional()
        .map(UexResponseDto::data)
        .orElse(Collections.emptyList());
  }

  /**
   * @return all space stations, or an empty list on error
   */
  public List<UexSpaceStationDto> getSpaceStations() {
    log.info("Fetching all spacestations from UEX API");

    return client
        .get()
        .uri(uexProperties.getSpaceStationsEndpoint())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexSpaceStationDto>>() {})
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(
            e -> {
              log.error("Failed to fetch spacestations from UEX API", e);
              return Mono.just(
                  UexResponseDto.<UexSpaceStationDto>builder()
                      .data(Collections.emptyList())
                      .build());
            })
        .blockOptional()
        .map(UexResponseDto::data)
        .orElse(Collections.emptyList());
  }

  /**
   * @return all terminals (trade kiosks at any location type), or an empty list on error
   */
  public List<UexTerminalDto> getTerminals() {
    log.info("Fetching all terminals from UEX API");

    return client
        .get()
        .uri(uexProperties.getTerminalsEndpoint())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexTerminalDto>>() {})
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(
            e -> {
              log.error("Failed to fetch terminals from UEX API", e);
              return Mono.just(
                  UexResponseDto.<UexTerminalDto>builder().data(Collections.emptyList()).build());
            })
        .blockOptional()
        .map(UexResponseDto::data)
        .orElse(Collections.emptyList());
  }

  /**
   * Fetches all refining methods (e.g. {@code Cormack}, {@code Pyrometric}, …). Drives the local
   * refining-method catalog used by the refinery-order pricing.
   *
   * @return all refining methods, or an empty list on error
   */
  public List<UexRefiningMethodDto> getRefineriesMethods() {
    log.info("Fetching all refineries methods from UEX API");

    return client
        .get()
        .uri(uexProperties.getRefineriesMethodsEndpoint())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexRefiningMethodDto>>() {})
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(
            e -> {
              log.error("Failed to fetch refineries methods from UEX API", e);
              return Mono.just(
                  UexResponseDto.<UexRefiningMethodDto>builder()
                      .data(Collections.emptyList())
                      .build());
            })
        .blockOptional()
        .map(UexResponseDto::data)
        .orElse(Collections.emptyList());
  }

  /**
   * Fetches refinery yield ratios per (terminal, method, commodity). Used by the refinery sync to
   * compute expected output quantities for a given input.
   *
   * @return all refinery yields, or an empty list on error
   */
  public List<UexRefineryYieldDto> getRefineriesYields() {
    log.info("Fetching all refineries yields from UEX API");

    return client
        .get()
        .uri(uexProperties.getRefineriesYieldsEndpoint())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<UexResponseDto<UexRefineryYieldDto>>() {})
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(
            e -> {
              log.error("Failed to fetch refineries yields from UEX API", e);
              return Mono.just(
                  UexResponseDto.<UexRefineryYieldDto>builder()
                      .data(Collections.emptyList())
                      .build());
            })
        .blockOptional()
        .map(UexResponseDto::data)
        .orElse(Collections.emptyList());
  }
}

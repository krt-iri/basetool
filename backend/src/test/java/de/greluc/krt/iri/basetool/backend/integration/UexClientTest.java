package de.greluc.krt.iri.basetool.backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.config.UexProperties;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexCommodityDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexCommodityPriceDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexStarSystemDto;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Unit tests for the {@link UexClient}. Uses {@link MockWebServer} to drive the WebClient against
 * an in-process HTTP endpoint instead of hitting the real UEX API.
 *
 * <p>Three behaviour patterns to cover for every endpoint method:
 *
 * <ol>
 *   <li>Happy path — JSON wrapped in {@code {"status":"ok","data":[...]}} → list contents are
 *       parsed and returned.
 *   <li>Server error (5xx, network blip) → the {@code onErrorResume} fallback returns an empty list
 *       so the caller never sees an exception.
 *   <li>Empty body / no {@code data} field → empty list.
 * </ol>
 *
 * Each test pins the endpoint URI so a future refactor that accidentally swaps two endpoint
 * constants in {@link UexProperties} would fail loud.
 */
class UexClientTest {

  private MockWebServer server;
  private UexProperties properties;
  private UexClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();

    properties = new UexProperties();
    properties.setApiUrl(server.url("/").toString());
    // All endpoints stay at their default paths — the production defaults
    // already match the public UEX 2.0 API surface and are validated as
    // part of property-binding tests.

    client = new UexClient(WebClient.builder(), properties);
    client.initClient();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  // ─── getCommodities ─────────────────────────────────────────────────────

  @Test
  void getCommodities_happyPath_returnsParsedList() throws Exception {
    // Given
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "status": "ok",
                  "data": [
                    {"id": 1, "name": "Gold", "is_illegal": 0},
                    {"id": 2, "name": "Quantanium", "is_illegal": 1}
                  ]
                }
                """));

    // When
    List<UexCommodityDto> commodities = client.getCommodities();

    // Then
    assertEquals(2, commodities.size());
    assertEquals("Gold", commodities.get(0).name());
    assertEquals("Quantanium", commodities.get(1).name());
    assertEquals(1, commodities.get(1).isIllegal());

    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertNotNull(req);
    assertEquals("/commodities", req.getPath());
    assertEquals("GET", req.getMethod());
  }

  @Test
  void getCommodities_serverError_returnsEmptyListInsteadOfThrowing() {
    // Given — a 5xx that the fallback must swallow
    server.enqueue(new MockResponse().setResponseCode(500).setBody("upstream exploded"));

    // When
    List<UexCommodityDto> commodities = client.getCommodities();

    // Then
    assertNotNull(commodities, "fallback must return empty list, not null");
    assertTrue(commodities.isEmpty());
  }

  @Test
  void getCommodities_connectionDropped_returnsEmptyList() {
    // Given — simulate a network blip
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_DURING_REQUEST_BODY));

    // When
    List<UexCommodityDto> commodities = client.getCommodities();

    // Then
    assertNotNull(commodities);
    assertTrue(commodities.isEmpty());
  }

  @Test
  void getCommodities_emptyDataArray_returnsEmptyList() {
    // Given — API returned a 200 but no items
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"status\":\"ok\",\"data\":[]}"));

    // When
    List<UexCommodityDto> commodities = client.getCommodities();

    // Then
    assertNotNull(commodities);
    assertTrue(commodities.isEmpty());
  }

  // ─── getCommoditiesPricesAll ────────────────────────────────────────────

  @Test
  void getCommoditiesPricesAll_happyPath_returnsParsedList() throws Exception {
    server.enqueue(
        jsonOk(
            """
            {"status":"ok","data":[
              {"id_commodity": 1, "price_buy": 12.5, "price_sell": 15.0}
            ]}
            """));

    List<UexCommodityPriceDto> prices = client.getCommoditiesPricesAll();

    assertEquals(1, prices.size());
    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertEquals("/commodities_prices_all", req.getPath());
  }

  @Test
  void getCommoditiesPricesAll_serverError_returnsEmptyList() {
    server.enqueue(new MockResponse().setResponseCode(503));
    assertTrue(client.getCommoditiesPricesAll().isEmpty());
  }

  // ─── getStarSystems ─────────────────────────────────────────────────────

  @Test
  void getStarSystems_happyPath_returnsParsedList() throws Exception {
    server.enqueue(
        jsonOk(
            """
            {"status":"ok","data":[
              {"id": 1, "name": "Stanton"},
              {"id": 2, "name": "Pyro"}
            ]}
            """));

    List<UexStarSystemDto> systems = client.getStarSystems();

    assertEquals(2, systems.size());
    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertEquals("/star_systems", req.getPath());
  }

  @Test
  void getStarSystems_clientError_returnsEmptyList() {
    server.enqueue(new MockResponse().setResponseCode(404));
    assertTrue(client.getStarSystems().isEmpty());
  }

  // ─── Endpoint sanity (URIs and empty-fallback) ─────────────────────────
  // These tests make sure all the smaller "list everything" endpoints hit
  // the right URI on the wire. We don't need separate happy-path schema
  // assertions for every Dto type — UexResponseDto<T> is generic and
  // Jackson's record-binding is already exercised by the three big ones
  // above. Each call gets an empty-data response so the fallback path
  // doesn't fire.

  @Test
  void getCompanies_hitsCorrectEndpoint() throws Exception {
    assertHitsEndpoint(client::getCompanies, "/companies");
  }

  @Test
  void getVehicles_hitsCorrectEndpoint() throws Exception {
    assertHitsEndpoint(client::getVehicles, "/vehicles");
  }

  @Test
  void getCities_hitsCorrectEndpoint() throws Exception {
    assertHitsEndpoint(client::getCities, "/cities");
  }

  @Test
  void getFactions_hitsCorrectEndpoint() throws Exception {
    assertHitsEndpoint(client::getFactions, "/factions");
  }

  @Test
  void getJurisdictions_hitsCorrectEndpoint() throws Exception {
    assertHitsEndpoint(client::getJurisdictions, "/jurisdictions");
  }

  @Test
  void getMoons_hitsCorrectEndpoint() throws Exception {
    assertHitsEndpoint(client::getMoons, "/moons");
  }

  @Test
  void getOrbits_hitsCorrectEndpoint() throws Exception {
    assertHitsEndpoint(client::getOrbits, "/orbits");
  }

  @Test
  void getOutposts_hitsCorrectEndpoint() throws Exception {
    assertHitsEndpoint(client::getOutposts, "/outposts");
  }

  @Test
  void getPlanets_hitsCorrectEndpoint() throws Exception {
    assertHitsEndpoint(client::getPlanets, "/planets");
  }

  @Test
  void getPoi_hitsCorrectEndpoint() throws Exception {
    assertHitsEndpoint(client::getPoi, "/poi");
  }

  @Test
  void getSpaceStations_hitsCorrectEndpoint() throws Exception {
    assertHitsEndpoint(client::getSpaceStations, "/space_stations");
  }

  @Test
  void getTerminals_hitsCorrectEndpoint() throws Exception {
    assertHitsEndpoint(client::getTerminals, "/terminals");
  }

  @Test
  void getRefineriesMethods_hitsCorrectEndpoint() throws Exception {
    assertHitsEndpoint(client::getRefineriesMethods, "/refineries_methods");
  }

  @Test
  void getRefineriesYields_hitsCorrectEndpoint() throws Exception {
    assertHitsEndpoint(client::getRefineriesYields, "/refineries_yields");
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private MockResponse jsonOk(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body);
  }

  private void assertHitsEndpoint(Runnable call, String expectedPath) throws InterruptedException {
    server.enqueue(jsonOk("{\"status\":\"ok\",\"data\":[]}"));
    call.run();
    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertNotNull(req, "client did not issue an HTTP request");
    assertEquals(expectedPath, req.getPath());
    assertEquals("GET", req.getMethod());
  }
}

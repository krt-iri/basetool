package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for {@link InventoryDeleteAllProxyController}. Drives the real {@link WebClient}
 * against {@link MockWebServer} so every exception-handling branch is exercised end-to-end without
 * a Spring context.
 *
 * <p>Coverage points:
 *
 * <ul>
 *   <li>Happy path: backend returns 204 → controller returns 204 and targets the canonical backend
 *       path {@code /api/v1/inventory/all}.
 *   <li>Backend client error (4xx) → controller re-throws as {@link ResponseStatusException} with
 *       the same status (e.g. 403 from a non-admin caller).
 *   <li>Backend server error (5xx) → same passthrough behaviour.
 *   <li>Network failure (server shutdown mid-request) → controller wraps in a 500 {@code
 *       ResponseStatusException} (no upstream stack trace leaked).
 * </ul>
 */
class InventoryDeleteAllProxyControllerTest {

  private MockWebServer server;
  private InventoryDeleteAllProxyController controller;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
    controller = new InventoryDeleteAllProxyController(webClient);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void deleteAllGlobalInventory_onBackend204_returns204() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(204));

    ResponseEntity<Void> result = controller.deleteAllGlobalInventory();

    assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertNotNull(req);
    assertEquals("DELETE", req.getMethod());
    assertEquals("/api/v1/inventory/all", req.getPath());
  }

  @Test
  void deleteAllGlobalInventory_onBackend403_propagatesAsForbidden() {
    server.enqueue(new MockResponse().setResponseCode(403).setBody("Forbidden"));

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> controller.deleteAllGlobalInventory());

    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
  }

  @Test
  void deleteAllGlobalInventory_onBackend500_propagatesAsInternalServerError() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> controller.deleteAllGlobalInventory());

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
  }

  @Test
  void deleteAllGlobalInventory_onConnectionFailure_wrapsAs500() throws Exception {
    // Shutdown the server before the call so the WebClient gets a
    // connect/read failure (NOT a WebClientResponseException).
    server.shutdown();

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> controller.deleteAllGlobalInventory());

    assertEquals(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ex.getStatusCode(),
        "Non-HTTP failures must be re-thrown as a 500 (sanitised — no upstream stack trace)");
    assertTrue(ex.getReason() != null && ex.getReason().toLowerCase().contains("unexpected"));

    // Tear down was a no-op; bring the field back to a clean state so AfterEach's
    // shutdown is a no-op too.
    server = new MockWebServer();
    server.start();
  }
}

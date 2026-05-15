package de.greluc.krt.iri.basetool.frontend.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

/**
 * Unit tests for the frontend's {@link BackendHealthIndicator}. The indicator is driven against an
 * in-process {@link MockWebServer} so the backend's {@code /actuator/health/readiness} response —
 * including failure modes such as 4xx/5xx upstream codes and connection-refused — can be staged
 * deterministically without a real backend.
 *
 * <p>Three behaviour classes are covered:
 *
 * <ol>
 *   <li>Happy path — a 2xx response from {@code /actuator/health/readiness} yields {@link
 *       Status#UP}.
 *   <li>Upstream error — a 4xx/5xx response yields {@link Status#DOWN} with the upstream status
 *       code attached as a detail for log correlation; this models the case where the backend is
 *       reachable but its OWN readiness is degraded (e.g. backend cannot reach Keycloak, Postgres,
 *       or its disk).
 *   <li>Transport failure — the upstream port is closed (server shut down before the probe), so the
 *       {@code JdkClientHttpRequestFactory} surfaces an I/O failure that maps to {@link
 *       Status#DOWN} with the exception class as a detail.
 * </ol>
 *
 * <p>Each test pins the request path to {@code /actuator/health/readiness} so a future refactor
 * that accidentally pointed the indicator at the bare {@code /actuator/health} (different
 * semantics) or some other actuator endpoint would fail loud. The trailing-slash trimming in the
 * constructor is also exercised so a {@code BACKEND_URL=https://backend:11261/} setup cannot
 * produce a double-slash URL.
 *
 * <p>TLS verification is NOT exercised here — the indicator installs a trust-all SSL context to
 * mirror the application's main {@code WebClient}, but {@link MockWebServer} defaults to plain
 * HTTP, which is sufficient for behavioural coverage. The trust-all path is a one-method
 * constructor side-effect with no branches.
 */
class BackendHealthIndicatorTest {

  /**
   * Short timeouts keep transport-failure tests millisecond-fast — the production indicator uses
   * 2&nbsp;s / 3&nbsp;s but we do not need those margins against an in-process server.
   */
  private static final Duration TEST_CONNECT_TIMEOUT = Duration.ofMillis(500);

  private static final Duration TEST_READ_TIMEOUT = Duration.ofMillis(500);

  private MockWebServer server;
  private String backendUrl;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    // No trailing slash, like the prod `BACKEND_URL=https://backend:11261`.
    backendUrl = server.url("").toString().replaceAll("/+$", "");
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void health_returns_up_when_backend_readiness_responds_200() throws Exception {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"status\":\"UP\"}"));
    BackendHealthIndicator indicator =
        new BackendHealthIndicator(backendUrl, TEST_CONNECT_TIMEOUT, TEST_READ_TIMEOUT);

    Health health = indicator.health();

    assertEquals(Status.UP, health.getStatus());
    assertEquals("backend-readiness", health.getDetails().get("endpoint"));

    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertNotNull(req, "indicator did not issue an HTTP request");
    assertEquals("GET", req.getMethod());
    assertEquals("/actuator/health/readiness", req.getPath());
  }

  @Test
  void health_returns_down_when_backend_readiness_responds_503() throws Exception {
    // Models the realistic chain: backend is reachable but its OWN readiness probe says DOWN
    // because (e.g.) Keycloak is unreachable from the backend's network position. Spring Boot
    // serves 503 with body `{"status":"DOWN"}` in that case.
    server.enqueue(
        new MockResponse()
            .setResponseCode(503)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"status\":\"DOWN\"}"));
    BackendHealthIndicator indicator =
        new BackendHealthIndicator(backendUrl, TEST_CONNECT_TIMEOUT, TEST_READ_TIMEOUT);

    Health health = indicator.health();

    assertEquals(Status.DOWN, health.getStatus());
    assertEquals(503, health.getDetails().get("status"));
    assertEquals("backend-readiness", health.getDetails().get("endpoint"));

    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertNotNull(req);
    assertEquals("/actuator/health/readiness", req.getPath());
  }

  @Test
  void health_returns_down_when_backend_readiness_responds_404() {
    // Models a misconfiguration where BACKEND_URL points at a service that does not expose
    // /actuator/health/readiness (e.g. wrong port, wrong host, actuator path disabled).
    server.enqueue(new MockResponse().setResponseCode(404));
    BackendHealthIndicator indicator =
        new BackendHealthIndicator(backendUrl, TEST_CONNECT_TIMEOUT, TEST_READ_TIMEOUT);

    Health health = indicator.health();

    assertEquals(Status.DOWN, health.getStatus());
    assertEquals(404, health.getDetails().get("status"));
  }

  @Test
  void health_returns_down_when_server_is_unreachable() {
    String deadBackendUrl = server.url("").toString().replaceAll("/+$", "");
    try {
      server.shutdown();
    } catch (Exception ex) {
      throw new IllegalStateException("server shutdown failed during arrange", ex);
    }
    BackendHealthIndicator indicator =
        new BackendHealthIndicator(deadBackendUrl, TEST_CONNECT_TIMEOUT, TEST_READ_TIMEOUT);

    Health health = indicator.health();

    assertEquals(Status.DOWN, health.getStatus());
    assertEquals("backend-readiness", health.getDetails().get("endpoint"));
    Object error = health.getDetails().get("error");
    assertNotNull(error, "transport failure must record an `error` detail for log correlation");
    assertNotEquals("", error.toString(), "the recorded error class name must not be empty");
  }

  @Test
  void constructor_trims_trailing_slash_so_the_probe_url_has_no_double_slash() throws Exception {
    // Same MockWebServer, but feed it WITH a trailing slash to verify the constructor's
    // canonicalisation. Without the trim, the probe would hit `//actuator/health/readiness`
    // which most servers would respond to with 404.
    String backendUrlWithTrailingSlash = server.url("/").toString();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"UP\"}"));
    BackendHealthIndicator indicator =
        new BackendHealthIndicator(
            backendUrlWithTrailingSlash, TEST_CONNECT_TIMEOUT, TEST_READ_TIMEOUT);

    Health health = indicator.health();

    assertEquals(Status.UP, health.getStatus());
    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertNotNull(req);
    assertEquals(
        "/actuator/health/readiness",
        req.getPath(),
        "trailing slash on BACKEND_URL must not propagate to the probe path");
  }
}

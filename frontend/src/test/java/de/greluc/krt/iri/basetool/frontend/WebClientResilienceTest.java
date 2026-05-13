package de.greluc.krt.iri.basetool.frontend;

import static org.junit.jupiter.api.Assertions.*;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.io.IOException;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      // Tighten client timeouts to speed up tests
      "app.http.connect-timeout=200ms",
      "app.http.response-timeout=500ms",
      "app.http.read-timeout=500ms",
      "app.http.write-timeout=500ms",
      // Resilience4j instances for our WebClient filter (instance name: backendApi)
      "resilience4j.retry.instances.backendApi.max-attempts=3",
      "resilience4j.retry.instances.backendApi.wait-duration=50ms",
      "resilience4j.circuitbreaker.instances.backendApi.sliding-window-size=2",
      "resilience4j.circuitbreaker.instances.backendApi.minimum-number-of-calls=2",
      "resilience4j.circuitbreaker.instances.backendApi.failure-rate-threshold=50",
      "resilience4j.circuitbreaker.instances.backendApi.permitted-number-of-calls-in-half-open-state=1",
      "resilience4j.circuitbreaker.instances.backendApi.wait-duration-in-open-state=200ms",
      "resilience4j.timelimiter.instances.backendApi.timeout-duration=400ms",
      "resilience4j.bulkhead.instances.backendApi.max-concurrent-calls=10"
    })
class WebClientResilienceTest {

  private static MockWebServer server;

  @Autowired private WebClient publicWebClient;

  @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

  @MockitoBean private OAuth2AuthorizedClientRepository authorizedClientRepository;

  @BeforeAll
  static void startServer() throws IOException {
    server = new MockWebServer();
    server.start(0);

    Dispatcher dispatcher =
        new Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) {
            String path = request.getPath();
            if ("/api/v1/ping".equals(path)) {
              return new MockResponse().setResponseCode(500).setBody("boom");
            }
            if ("/api/v1/slow".equals(path)) {
              return new MockResponse()
                  .setBody("slow")
                  .setBodyDelay(1, java.util.concurrent.TimeUnit.SECONDS)
                  .setResponseCode(200);
            }
            return new MockResponse().setResponseCode(404);
          }
        };
    server.setDispatcher(dispatcher);
  }

  @DynamicPropertySource
  static void registerProps(DynamicPropertyRegistry registry) {
    registry.add("app.backend-url", () -> "http://localhost:" + server.getPort());
  }

  @AfterAll
  static void stopServer() throws IOException {
    if (server != null) {
      server.shutdown();
    }
  }

  @Test
  void retry_ShouldPerformMultipleAttempts_On5xx() {
    int before = server.getRequestCount();
    try {
      publicWebClient.get().uri("/api/v1/ping").retrieve().toBodilessEntity().block();
      fail("Expected exception due to 5xx");
    } catch (Exception ignored) {
    }
    int after = server.getRequestCount();
    // 1 initial + 2 retries = 3 total attempts
    assertEquals(before + 3, after, "WebClient should have retried the request");
  }

  @Test
  void circuitBreaker_ShouldOpenAndShortCircuit_SubsequentCalls() {
    // First two calls fail and should count towards the circuit breaker window
    for (int i = 0; i < 2; i++) {
      try {
        publicWebClient.get().uri("/api/v1/ping").retrieve().toBodilessEntity().block();
        fail("Expected exception");
      } catch (Exception ignored) {
      }
    }
    int before = server.getRequestCount();
    // Third call should be short-circuited by the open breaker → no new backend hit
    try {
      publicWebClient.get().uri("/api/v1/ping").retrieve().toBodilessEntity().block();
      fail("Expected CallNotPermittedException");
    } catch (Exception e) {
      assertTrue(
          e.getCause() instanceof CallNotPermittedException
              || e instanceof CallNotPermittedException,
          "Expected circuit breaker to short-circuit the call");
    }
    int after = server.getRequestCount();
    assertEquals(before, after, "Request should have been short-circuited (no new backend hit)");
  }

  @Test
  void timeLimiter_ShouldTimeoutSlowResponses() {
    int before = server.getRequestCount();
    long start = System.currentTimeMillis();
    try {
      publicWebClient.get().uri("/api/v1/slow").retrieve().bodyToMono(String.class).block();
      fail("Expected timeout due to slow response");
    } catch (Exception ignored) {
    }
    long duration = System.currentTimeMillis() - start;
    int after = server.getRequestCount();
    assertTrue(duration < 2000, "Call should time out quickly");
    assertTrue(after >= before + 1, "A request should have been attempted");
  }
}

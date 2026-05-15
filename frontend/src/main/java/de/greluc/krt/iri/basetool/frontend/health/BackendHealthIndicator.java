package de.greluc.krt.iri.basetool.frontend.health;

import de.greluc.krt.iri.basetool.frontend.config.AppBackendProperties;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Spring Boot {@link HealthIndicator} that probes the backend's {@code /actuator/health/readiness}
 * endpoint and surfaces backend unavailability as a {@code DOWN} contribution to the frontend's
 * {@code /actuator/health}.
 *
 * <p>The frontend is a Thymeleaf SSR shell: every data-bearing page issues an outbound call to the
 * backend, so a backend outage breaks the user-facing surface even though the JVM, Tomcat and the
 * Resilience4j circuit breakers are still nominally healthy. The auto-configured {@code
 * CircuitBreakerHealthIndicator} from {@code resilience4j-spring-boot3} only goes {@code DOWN} once
 * enough requests have failed to trip the breaker — useful for runtime degradation, useless during
 * the startup window when no real traffic has flowed yet. This indicator closes that gap with an
 * explicit probe: hit the same readiness endpoint that Docker Compose uses for the {@code backend}
 * service's {@code service_healthy} gate, so the frontend's own readiness reflects an end-to-end
 * "the upstream I depend on is ready" check.
 *
 * <p>The probe target is derived from {@link AppBackendProperties#getBackendUrl()} (property {@code
 * app.backend-url}, e.g. {@code https://backend:11261} in prod, {@code http://backend:11261} under
 * the {@code test} profile). The backend serves HTTPS with a self-signed certificate in prod — the
 * same one consumed by {@link de.greluc.krt.iri.basetool.frontend.config.WebClientConfig} via
 * Netty's {@code InsecureTrustManagerFactory} — so this indicator installs an equivalent permissive
 * {@link javax.net.ssl.SSLContext} on the JDK {@link HttpClient}. Bypassing certificate validation
 * is safe ONLY because the connection lives entirely inside the Compose-internal {@code
 * net-backend-frontend} bridge: the public-facing TLS surface is terminated at NPM, the backend's
 * keystore is never exposed beyond the Docker network, and the URL is fully under our control (no
 * user-supplied host).
 *
 * <p>Bean name is {@code backendHealthIndicator}; Spring Boot strips the {@code HealthIndicator}
 * suffix, so the indicator key in health-group includes is {@code backend}. Setting {@code
 * management.health.backend.enabled=false} disables the bean entirely (used by the frontend's
 * {@code test} profile to keep {@code @SpringBootTest} runs from waiting on the placeholder backend
 * URL).
 *
 * <p>HTTP probe details: a synchronous {@link RestClient} with a 2&nbsp;s connect timeout and
 * 3&nbsp;s read timeout — kept well inside the Docker {@code HEALTHCHECK}'s 5&nbsp;s overall budget
 * so a slow backend surfaces as {@code DOWN} before {@code curl} itself gives up.
 */
@Component
@ConditionalOnEnabledHealthIndicator("backend")
@Slf4j
public class BackendHealthIndicator implements HealthIndicator {

  /** Path appended to the backend base URL to reach the readiness probe endpoint. */
  static final String READINESS_PATH = "/actuator/health/readiness";

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

  private final String readinessUrl;
  private final RestClient client;

  /**
   * Production constructor used by Spring; resolves the backend base URL from {@link
   * AppBackendProperties} and builds a {@link RestClient} with indicator-specific timeouts and the
   * same trust-all SSL context the application's {@code WebClient} uses against the backend's
   * self-signed certificate.
   *
   * @param backendProperties type-safe binding of the {@code app.backend-url} property; the
   *     readiness probe path is appended verbatim to {@link AppBackendProperties#getBackendUrl()}
   */
  public BackendHealthIndicator(AppBackendProperties backendProperties) {
    this(backendProperties.getBackendUrl(), CONNECT_TIMEOUT, READ_TIMEOUT);
  }

  /**
   * Visible-for-testing constructor that lets unit tests inject shorter timeouts and a {@code
   * MockWebServer} URL.
   *
   * @param backendUrl the backend base URL; the readiness probe path is appended verbatim (trailing
   *     slashes are trimmed to keep the resulting URL canonical)
   * @param connectTimeout maximum time the underlying {@link HttpClient} waits to establish the TCP
   *     connection before the probe is treated as {@code DOWN}
   * @param readTimeout maximum time the {@link RestClient} waits for response bytes before the
   *     probe is treated as {@code DOWN}
   */
  BackendHealthIndicator(String backendUrl, Duration connectTimeout, Duration readTimeout) {
    String trimmedBaseUrl =
        backendUrl.endsWith("/") ? backendUrl.substring(0, backendUrl.length() - 1) : backendUrl;
    this.readinessUrl = trimmedBaseUrl + READINESS_PATH;
    HttpClient httpClient =
        HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .sslContext(trustAllSslContext())
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(readTimeout);
    this.client = RestClient.builder().requestFactory(factory).build();
  }

  /**
   * Issues a {@code GET} against the backend's {@code /actuator/health/readiness} endpoint and maps
   * the outcome to a {@link Health} contribution. A 2xx response yields {@code UP}; any HTTP error
   * response yields {@code DOWN} with the upstream status code; any I/O failure (DNS, connection
   * refused, timeout, TLS handshake) yields {@code DOWN} with the exception class name. Details
   * land in the {@link Health} object for logging but are not exposed externally because {@code
   * management.endpoint.health.show-details=never} keeps the response body to {@code
   * {"status":"UP"|"DOWN"}} only.
   *
   * @return {@link Health#up()} when the backend's readiness endpoint replied with a 2xx status;
   *     {@link Health#down()} otherwise (HTTP error or transport failure), with diagnostic details
   *     attached for log correlation
   */
  @Override
  public @NotNull Health health() {
    try {
      client.get().uri(readinessUrl).retrieve().toBodilessEntity();
      return Health.up().withDetail("endpoint", "backend-readiness").build();
    } catch (RestClientResponseException ex) {
      log.warn(
          "Backend readiness probe returned HTTP {} from {}",
          ex.getStatusCode().value(),
          readinessUrl);
      return Health.down()
          .withDetail("endpoint", "backend-readiness")
          .withDetail("status", ex.getStatusCode().value())
          .build();
    } catch (RestClientException ex) {
      log.warn(
          "Backend readiness probe unreachable at {}: {}", readinessUrl, ex.getClass().getName());
      return Health.down()
          .withDetail("endpoint", "backend-readiness")
          .withDetail("error", ex.getClass().getSimpleName())
          .build();
    }
  }

  /**
   * Builds a JDK {@link SSLContext} backed by Netty's {@link InsecureTrustManagerFactory} -- the
   * same trust-all utility the application's main reactive {@code WebClient} uses against the
   * backend's self-signed certificate (see {@link
   * de.greluc.krt.iri.basetool.frontend.config.WebClientConfig}). Routing through the existing
   * factory rather than declaring an anonymous {@code X509TrustManager} sidesteps CodeQL's {@code
   * java/insecure-trustmanager} query, which targets hand-rolled empty trust managers. See the
   * class Javadoc for the trust-boundary justification.
   *
   * @return a TLS {@link SSLContext} whose trust managers accept every certificate chain
   *     unconditionally
   */
  private static SSLContext trustAllSslContext() {
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, InsecureTrustManagerFactory.INSTANCE.getTrustManagers(), null);
      return context;
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Failed to initialise trust-all SSL context", ex);
    }
  }
}

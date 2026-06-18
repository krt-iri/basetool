/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.frontend.health;

import java.net.http.HttpClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Spring Boot {@link HealthIndicator} that probes the configured Keycloak realm's OIDC discovery
 * endpoint ({@code {issuer}/.well-known/openid-configuration}) from the frontend module and
 * surfaces unreachability as a {@code DOWN} contribution to {@code /actuator/health}.
 *
 * <p>The frontend is an OAuth2 client (browser SSO): a Keycloak outage breaks the login redirect
 * and token-refresh flow even though the Thymeleaf renderer itself stays up. Routing this indicator
 * into the {@code readiness} health group (see {@code
 * management.endpoint.health.group.readiness.include} in {@code application.yml}) makes Docker
 * Compose's {@code depends_on: condition: service_healthy} reflect end-to-end SSO viability rather
 * than just "Tomcat is bound".
 *
 * <p>The probe target is the same OIDC metadata document Spring's OAuth2 client fetches at startup,
 * bound from {@code spring.security.oauth2.client.provider.keycloak.issuer-uri} — deliberately the
 * client-side property, not the backend's resource-server property: the frontend and backend reach
 * Keycloak via different network paths in the prod compose topology (the frontend goes through NPM
 * in the public-DNS direction, the backend via the internal {@code net-backend-keycloak} bridge),
 * so each module owns its own reachability check.
 *
 * <p>Bean name is {@code keycloakHealthIndicator}; Spring Boot strips the {@code HealthIndicator}
 * suffix, so the indicator key in health-group includes is {@code keycloak}. The property {@code
 * management.health.keycloak.enabled=false} disables the bean (used by the {@code test} profile to
 * keep {@code @SpringBootTest} runs from waiting on the dummy issuer URI baked into {@code
 * application-test.yml}).
 *
 * <p>HTTP probe details: a synchronous {@link RestClient} over Java's {@link HttpClient} with a
 * 2&nbsp;s connect timeout and 3&nbsp;s read timeout — kept well inside the Docker {@code
 * HEALTHCHECK}'s 5&nbsp;s overall budget so a slow Keycloak surfaces as {@code DOWN} before {@code
 * curl} itself gives up.
 */
@Component
@ConditionalOnEnabledHealthIndicator("keycloak")
@Slf4j
public class KeycloakHealthIndicator implements HealthIndicator {

  /** Path appended to the issuer URI to reach the standard OIDC discovery document. */
  static final String DISCOVERY_PATH = "/.well-known/openid-configuration";

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

  private final String discoveryUrl;
  private final RestClient client;

  /**
   * Production constructor used by Spring; resolves the issuer URI from {@code
   * spring.security.oauth2.client.provider.keycloak.issuer-uri} and builds a {@link RestClient}
   * with the indicator-specific timeouts. {@link Autowired} is required because the class declares
   * a second (package-private, test-only) constructor; without it Spring 4+'s constructor-selection
   * logic falls back to a non-existent default constructor and fails at startup with {@code
   * NoSuchMethodException: <init>()}.
   *
   * @param issuerUri the Keycloak realm issuer URI (e.g. {@code
   *     https://keycloak.example/realms/iri}) resolved from {@code
   *     spring.security.oauth2.client.provider.keycloak.issuer-uri}; the discovery document path is
   *     appended verbatim
   */
  @Autowired
  public KeycloakHealthIndicator(
      @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}") String issuerUri) {
    this(issuerUri, CONNECT_TIMEOUT, READ_TIMEOUT);
  }

  /**
   * Visible-for-testing constructor that lets unit tests inject shorter timeouts when driving the
   * indicator against an in-process {@code MockWebServer}.
   *
   * @param issuerUri the Keycloak realm issuer URI; the OIDC discovery path is appended verbatim
   * @param connectTimeout maximum time the underlying {@link HttpClient} waits to establish the TCP
   *     connection before the probe is treated as {@code DOWN}
   * @param readTimeout maximum time the {@link RestClient} waits for response bytes before the
   *     probe is treated as {@code DOWN}
   */
  KeycloakHealthIndicator(String issuerUri, Duration connectTimeout, Duration readTimeout) {
    this.discoveryUrl = issuerUri + DISCOVERY_PATH;
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(readTimeout);
    this.client = RestClient.builder().requestFactory(factory).build();
  }

  /**
   * Issues a {@code GET} against the OIDC discovery endpoint and maps the outcome to a {@link
   * Health} contribution. A 2xx response yields {@code UP}; any HTTP error response yields {@code
   * DOWN} with the upstream status code; any I/O failure (DNS, connection refused, timeout, TLS
   * handshake) yields {@code DOWN} with the exception class name. Details land in the {@link
   * Health} object for logging but are not exposed externally because {@code
   * management.endpoint.health.show-details=never} keeps the response body to {@code
   * {"status":"UP"|"DOWN"}} only.
   *
   * @return {@link Health#up()} when the discovery endpoint replied with a 2xx status; {@link
   *     Health#down()} otherwise (HTTP error or transport failure), with diagnostic details
   *     attached for log correlation
   */
  @Override
  public @NotNull Health health() {
    try {
      client.get().uri(discoveryUrl).retrieve().toBodilessEntity();
      return Health.up().withDetail("endpoint", "openid-configuration").build();
    } catch (RestClientResponseException ex) {
      log.warn(
          "Keycloak OIDC discovery returned HTTP {} from {}",
          ex.getStatusCode().value(),
          discoveryUrl);
      return Health.down()
          .withDetail("endpoint", "openid-configuration")
          .withDetail("status", ex.getStatusCode().value())
          .build();
    } catch (RestClientException ex) {
      log.warn(
          "Keycloak OIDC discovery unreachable at {}: {}", discoveryUrl, ex.getClass().getName());
      return Health.down()
          .withDetail("endpoint", "openid-configuration")
          .withDetail("error", ex.getClass().getSimpleName())
          .build();
    }
  }
}

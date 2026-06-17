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

package de.greluc.krt.iri.basetool.ingest.config;

import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security configuration for the ingest gateway: a pure JWT-bearer resource server. There is no
 * session, no cookie and no HTML, so the posture is deliberately minimal — stateless sessions, CSRF
 * disabled (nothing cookie-bound to forge), empty CORS, and a {@code default-src 'none'} CSP
 * (REQ-INGEST-001/-002).
 *
 * <p>Authorization is intentionally coarse: every ingest endpoint requires only an authenticated
 * caller ({@code isAuthenticated()}, enforced both here and by method-level {@code @PreAuthorize}),
 * mirroring the backend's import endpoints (REQ-REFINERY-011). The optional {@code aud} check below
 * is the resource-server defence-in-depth knob.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  /**
   * Expected JWT {@code aud} values for the opt-in audience check. Empty by default → no audience
   * enforcement (signature/issuer/expiry still apply). Set {@code
   * app.security.jwt.expected-audiences=basetool-backend} to require it — but only once the realm's
   * clients actually emit that audience (see {@code docs/INGEST_KEYCLOAK_SETUP.md}); the same value
   * the backend uses, because the gateway forwards the same bearer to the backend.
   */
  @Value("${app.security.jwt.expected-audiences:}")
  private List<String> expectedAudiences;

  /**
   * Opt-in audience-validating {@link JwtDecoder}, created ONLY when {@code
   * app.security.jwt.expected-audiences} is set. Otherwise Spring Boot's auto-configured decoder is
   * used unchanged. When active it layers an {@code aud} check on top of the default signature /
   * issuer / timestamp validators.
   *
   * @param issuerUri the configured Keycloak issuer location
   * @return a Nimbus decoder whose validator chain additionally requires a matching {@code aud}
   */
  @Bean
  @ConditionalOnProperty(prefix = "app.security.jwt", name = "expected-audiences")
  JwtDecoder audienceValidatingJwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuerUri),
            audienceValidator(expectedAudiences)));
    return decoder;
  }

  /**
   * Builds the {@code aud}-claim validator: a token passes only when its {@code aud} list
   * intersects {@code expectedAudiences}. Package-private + static so it is unit-testable without a
   * Spring context.
   *
   * @param expectedAudiences the accepted audience values; an empty set matches no token
   * @return a validator that errors unless the JWT's {@code aud} intersects the expected set
   */
  static OAuth2TokenValidator<Jwt> audienceValidator(List<String> expectedAudiences) {
    return new JwtClaimValidator<List<String>>(
        JwtClaimNames.AUD, aud -> aud != null && !Collections.disjoint(aud, expectedAudiences));
  }

  /**
   * The single {@link SecurityFilterChain}: CSRF disabled (bearer-only, stateless), empty CORS,
   * locked-down response headers, the authorization matrix, JWT resource-server activation and a
   * stateless session policy.
   *
   * @param http the Spring Security builder
   * @return the configured filter chain
   * @throws Exception propagated from {@link HttpSecurity#build()}
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .headers(
            headers -> {
              // The gateway serves only JSON — no document context exists, so every fetch
              // directive inherits 'none'. frame-ancestors/base-uri/form-action are
              // defence-in-depth against an injected document.
              headers.contentSecurityPolicy(
                  csp ->
                      csp.policyDirectives(
                          "default-src 'none'; frame-ancestors 'none'; base-uri 'none';"
                              + " form-action 'none'"));
              headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny);
              headers.httpStrictTransportSecurity(
                  hsts -> hsts.includeSubDomains(true).preload(true).maxAgeInSeconds(31_536_000L));
            })
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    // springdoc serves /v3/api-docs in non-prod only (prod sets api-docs.enabled
                    // = false → 404); harmless to permit here.
                    .requestMatchers("/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    return http.build();
  }

  /**
   * CORS source: empty allowlist, {@code allowCredentials=false}. The gateway is called by a native
   * desktop app (no browser origin) and by no browser directly, so cross-origin browser traffic is
   * rejected outright — combined with the bearer-only model this closes the open-CORS-with-creds
   * failure mode.
   *
   * @return a CORS source applied to all paths
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(List.of());
    configuration.setAllowedMethods(List.of("POST", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Accept", "Accept-Language", "X-Correlation-Id"));
    configuration.setAllowCredentials(false);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}

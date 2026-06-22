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

package de.greluc.krt.profit.basetool.backend.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Backend security configuration: JWT resource-server, role hierarchy, CSRF policy and the request
 * authorization matrix.
 *
 * <p>The backend is a pure resource server — incoming JWTs are validated against the Keycloak
 * issuer, the {@link CustomJwtGrantedAuthoritiesConverter} maps both Keycloak realm roles AND the
 * project-specific {@code is_logistician} / {@code is_mission_manager} flags on {@code app_user}
 * into Spring authorities. The role hierarchy mirrors the CLAUDE.md matrix (admin/officer imply
 * logistician/mission-manager).
 *
 * <p>The {@code authorizeHttpRequests} matrix in {@link #filterChain} is the single, exhaustive
 * source for which endpoints are public, which require authentication, and which require a specific
 * role/authority. The order matters — Spring evaluates the matchers top-down. Method-level
 * {@code @PreAuthorize} on services adds fine-grained checks but never weakens the chain matcher.
 *
 * <p>CSRF is enabled with the cookie token repository except in the {@code test} profile, where it
 * is disabled so MockMvc tests do not need to plumb the token through every call. API endpoints
 * that are exclusively JSON and bearer-token authenticated ({@code /api/v1/missions/**}, {@code
 * /api/v1/operations/**}, {@code /api/v1/orders}, {@code /api/v1/finance-entries}) are explicitly
 * ignored because they can never be triggered from a CSRF-vulnerable browser flow.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  /**
   * Cross-origin allowlist for the backend API. Empty by default: the backend is only addressed
   * server-side from the Spring-Boot frontend (Thymeleaf SSR), so no direct browser-to-backend
   * cross-origin traffic is expected, and any such call is rejected with HTTP 403. Override in
   * environment-specific YAML when a real browser client on a different origin is introduced (e.g.
   * a future mobile web app on https://mobile.profit-base.online).
   */
  @Value("${app.cors.allowed-origin-patterns:}")
  private List<String> allowedOriginPatterns;

  /**
   * Expected JWT {@code aud} (audience) values for the opt-in audience check (audit L-1). Empty by
   * default → no audience enforcement: the resource server already validates signature, issuer and
   * expiry, and the effective authority comes from realm roles, so requiring {@code aud} is a
   * defense-in-depth knob an operator enables once they know the value their realm issues (a wrong
   * value would reject every token). Set {@code app.security.jwt.expected-audiences} (comma-list)
   * to the backend client / resource id to turn it on.
   */
  @Value("${app.security.jwt.expected-audiences:}")
  private List<String> expectedAudiences;

  /**
   * Opt-in audience-validating {@link JwtDecoder}. Created ONLY when {@code
   * app.security.jwt.expected-audiences} is set (audit L-1); otherwise Spring Boot's
   * auto-configured decoder is used unchanged, so the default behavior — including the {@code test}
   * profile's unreachable placeholder issuer, which the auto-config fetches lazily — is untouched.
   * When active it layers an {@code aud} check on top of the default signature / issuer / timestamp
   * validators.
   *
   * @param issuerUri the configured Keycloak issuer location.
   * @return a Nimbus decoder whose validator chain additionally requires a matching {@code aud}.
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
   * Builds the {@code aud}-claim validator (audit L-1): a token passes only when its {@code aud}
   * list intersects {@code expectedAudiences}. Package-private + static so it is unit-testable
   * without a Spring context.
   *
   * @param expectedAudiences the accepted audience values; never {@code null} (an empty set matches
   *     no token).
   * @return an {@link OAuth2TokenValidator} that errors unless the JWT's {@code aud} intersects the
   *     expected set.
   */
  static OAuth2TokenValidator<Jwt> audienceValidator(List<String> expectedAudiences) {
    return new JwtClaimValidator<List<String>>(
        JwtClaimNames.AUD,
        aud -> aud != null && !java.util.Collections.disjoint(aud, expectedAudiences));
  }

  /**
   * Declares the role hierarchy that {@code @PreAuthorize("hasRole('LOGISTICIAN')")} and friends
   * use. Mirrors the matrix in {@code ROLES_AND_PERMISSIONS.md}: admin and officer both imply
   * logistician and mission-manager, so an admin satisfies a {@code @PreAuthorize} for {@code
   * LOGISTICIAN} without being explicitly granted that role.
   *
   * @return the {@link RoleHierarchy} bean consumed by Spring Security's expression handlers
   */
  @Bean
  public static RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.fromHierarchy(
        """
        ROLE_ADMIN > ROLE_LOGISTICIAN
        ROLE_OFFICER > ROLE_LOGISTICIAN
        ROLE_ADMIN > ROLE_MISSION_MANAGER
        ROLE_OFFICER > ROLE_MISSION_MANAGER
        ROLE_ADMIN > ROLE_BANK_MANAGEMENT
        ROLE_BANK_MANAGEMENT > ROLE_BANK_EMPLOYEE
        """);
  }

  /**
   * Wires the project's {@link CustomJwtGrantedAuthoritiesConverter} into Spring Security's
   * standard {@link JwtAuthenticationConverter}, so every authenticated request sees the merged
   * authority set (Keycloak realm roles + DB-flag-derived roles).
   *
   * @param customConverter project-specific authorities converter
   * @return wired {@code JwtAuthenticationConverter}
   */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter(
      CustomJwtGrantedAuthoritiesConverter customConverter) {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(customConverter);
    return converter;
  }

  /**
   * Builds the main {@link SecurityFilterChain}: CSRF policy (profile-dependent), CORS source,
   * security response headers (CSP, X-Frame-Options, Referrer-Policy, Permissions-Policy,
   * X-Content-Type-Options), the request-authorization matrix and JWT resource-server activation.
   *
   * <p>The matrix is profile-independent — same rules for {@code dev} and {@code prod}. Public
   * endpoints (master data, mission-search, guest mission editing) are listed explicitly; every
   * unlisted request falls through to {@code anyRequest().authenticated()}.
   *
   * @param http Spring Security builder
   * @param jwtAuthenticationConverter wired by {@link #jwtAuthenticationConverter}
   * @param env active environment, used to detect the {@code test} profile so CSRF can be disabled
   *     for MockMvc tests
   * @return the configured security filter chain
   * @throws Exception propagated from {@link HttpSecurity#build()}
   */
  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      JwtAuthenticationConverter jwtAuthenticationConverter,
      org.springframework.core.env.Environment env)
      throws Exception {

    boolean isTest = java.util.Arrays.asList(env.getActiveProfiles()).contains("test");

    if (isTest) {
      // CSRF is intentionally disabled in the `test` Spring profile so MockMvc
      // tests can POST without first acquiring a CSRF cookie. The production
      // path (the `else` branch below) keeps cookie-based CSRF enabled with
      // explicit `ignoringRequestMatchers(...)` only for the JWT-bearer-token
      // API endpoints under `/api/v1/**`, which are protected by JWT auth
      // and have no session cookie to attack. The test profile is gated by
      // Spring profile activation and is never enabled in deployed environments.
      // lgtm[java/spring-disabled-csrf-protection]
      http.csrf(
          org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
              ::disable);
    } else {
      // L-2: pin the CSRF cookie's Secure + SameSite attributes explicitly. Spring's default
      // CookieCsrfTokenRepository does not set SameSite, leaving the browser to fall back to
      // {@code Lax}; pinning {@code Strict} aligns the XSRF cookie with the session cookie
      // (see frontend application*.yml `server.servlet.session.cookie.same-site: strict`).
      CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
      csrfRepo.setCookieCustomizer(c -> c.sameSite("Strict").secure(true));
      http.csrf(
          csrf ->
              csrf.csrfTokenRepository(csrfRepo)
                  .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                  // L-10: keep the ignore list co-located with one comment so a future
                  // reviewer sees the contract. Every entry here MUST be JSON-only +
                  // bearer-token-authenticated (no session cookie). Adding {@code
                  // /api/v1/announcement} would technically be consistent because the
                  // controller is also JSON+bearer — left out for now because no client
                  // hits it from a session-cookie context, so the missing entry costs
                  // nothing in practice.
                  .ignoringRequestMatchers(
                      "/api/v1/missions/**",
                      "/api/v1/operations/**",
                      "/api/v1/orders",
                      "/api/v1/orders/items",
                      "/api/v1/finance-entries"));
    }

    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .headers(
            headers -> {
              headers.contentSecurityPolicy(
                  // The backend is a pure JSON resource server — it serves no HTML, scripts,
                  // styles, images or fonts of its own (enforced by the ArchUnit "no HTML" rules
                  // and the removal of Swagger UI). The earlier policy relaxed {@code style-src}
                  // with {@code 'unsafe-inline'} and allowed {@code data:} img/font sources purely
                  // so the bundled Swagger UI rendered; with that gone the policy locks down to
                  // {@code default-src 'none'}, which makes every fetch directive
                  // (script/style/img/font/connect/object) inherit {@code 'none'} — nothing can be
                  // loaded into a (would-be) document context. {@code frame-ancestors 'none'}
                  // mirrors X-Frame-Options DENY; {@code base-uri 'none'} and {@code form-action
                  // 'none'} are defence-in-depth against an injected {@code <base>}/{@code <form>}.
                  // {@code upgrade-insecure-requests} is dropped: there are no sub-resources left
                  // to upgrade.
                  csp ->
                      csp.policyDirectives(
                          "default-src 'none'; frame-ancestors 'none'; base-uri 'none';"
                              + " form-action 'none'"));
              headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny);
              headers.referrerPolicy(
                  ref -> ref.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
              // M-12: Cross-Origin-Opener-Policy + Cross-Origin-Resource-Policy. COOP isolates
              // the browsing-context group so a popup cannot reach back via {@code window.opener}
              // (OAuth-redirect timing attacks etc.); CORP prevents cross-origin embedding via
              // {@code <img src=…>} / {@code <script src=…>}.
              headers.crossOriginOpenerPolicy(
                  coop ->
                      coop.policy(
                          org.springframework.security.web.header.writers
                              .CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy
                              .SAME_ORIGIN));
              headers.crossOriginResourcePolicy(
                  corp ->
                      corp.policy(
                          org.springframework.security.web.header.writers
                              .CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy
                              .SAME_ORIGIN));
              // Audit finding H-9: explicit HSTS. Spring Security's default writer only emits the
              // header when {@code request.isSecure()} is true; behind a reverse proxy without
              // forward-headers configuration that check silently disables HSTS. Setting it here
              // makes the policy explicit and independent of the request-scoped scheme detection.
              headers.httpStrictTransportSecurity(
                  hsts -> hsts.includeSubDomains(true).preload(true).maxAgeInSeconds(31_536_000L));
              headers.addHeaderWriter(
                  new org.springframework.security.web.header.writers.StaticHeadersWriter(
                      "Permissions-Policy",
                      // L-3: explicit deny for every browser feature the app does not use, so
                      // an injected iframe / shared context cannot opt-in.
                      "geolocation=(), camera=(), microphone=(), fullscreen=(),"
                          + " payment=(), usb=(), serial=(), bluetooth=(), accelerometer=(),"
                          + " gyroscope=(), magnetometer=(), display-capture=(),"
                          + " clipboard-read=(), clipboard-write=(), interest-cohort=()"));
              headers.contentTypeOptions(Customizer.withDefaults());
            })
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/error", "/v3/api-docs/**")
                    // Swagger UI has been removed from the project (springdoc -api starter, no
                    // -ui). Only the raw OpenAPI document is served at /v3/api-docs, and only in
                    // non-prod profiles: the prod profile sets springdoc.api-docs.enabled=false so
                    // this matcher resolves to a 404 there. The committed openapi.json remains the
                    // single source of API documentation.
                    .permitAll()
                    // Spring Boot Actuator health endpoint, used by Docker HEALTHCHECK and by
                    // docker-compose `depends_on: condition: service_healthy`. Other actuator
                    // endpoints stay behind authentication (default `anyRequest().authenticated()`
                    // catch-all below). `management.endpoint.health.show-details=never` keeps the
                    // response to `{"status":"UP"}` so no internal details leak.
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/frequency-types", "/api/v1/frequency-types/**",
                        "/api/v1/locations", "/api/v1/locations/**",
                        "/api/v1/job-types", "/api/v1/job-types/**",
                        "/api/v1/manufacturers", "/api/v1/manufacturers/**",
                        "/api/v1/materials", "/api/v1/materials/**",
                        "/api/v1/refining-methods", "/api/v1/refining-methods/**",
                        "/api/v1/settings", "/api/v1/settings/**",
                        "/api/v1/ship-types", "/api/v1/ship-types/**",
                        "/api/v1/squadrons", "/api/v1/squadrons/**",
                        "/api/v1/star-systems", "/api/v1/star-systems/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/missions/next")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/missions/search")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/missions/{id}")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/missions", "/api/v1/missions/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/missions")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/refinery-orders/mission/**",
                        "/api/v1/system/**",
                        "/api/v2/system/**")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/missions/*/participants/add",
                        "/api/v1/missions/*/participants/slim")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/missions/*/participants/*/check-in",
                        "/api/v1/missions/*/participants/*/check-out")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/missions/*/participants/*/check-in/slim",
                        "/api/v1/missions/*/participants/*/check-out/slim")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/orders")
                    .permitAll()
                    // Item-order create + its catalog reads mirror the material create's anonymous
                    // access (the public request form). The item-handover / report endpoints are
                    // NOT listed here — they stay behind the authenticated catch-all +
                    // @PreAuthorize.
                    .requestMatchers(HttpMethod.POST, "/api/v1/orders/items")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/orders/item-catalog",
                        "/api/v1/orders/item-catalog/**")
                    .permitAll()
                    // The Job Order create form is reachable anonymously (the public request form),
                    // so the org-unit catalog that fills its requesting/responsible pickers must be
                    // too. The payload carries no PII — only name + shorthand + kind + profit flag
                    // —
                    // mirroring the already-permitAll /api/v1/squadrons catalog.
                    .requestMatchers(HttpMethod.GET, "/api/v1/org-units/active")
                    .permitAll()
                    // Finance-entry creation is no longer anonymous: the method-level
                    // @PreAuthorize on MissionFinanceEntryController#createFinanceEntry requires an
                    // authenticated member (isMemberOrAbove), blocking anonymous AND role-less
                    // GUEST
                    // callers. The URL gate only has to stop being permitAll; the method gate is
                    // the
                    // source of truth for the member requirement.
                    .requestMatchers(HttpMethod.POST, "/api/v1/finance-entries")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.PUT,
                        "/api/v1/missions/*/participants/*",
                        "/api/v1/missions/*/participants/*/payout-preference")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.PUT,
                        "/api/v1/missions/*/participants/*/slim",
                        "/api/v1/missions/*/participants/*/payout-preference/slim")
                    .permitAll()
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/missions/*/participants/*")
                    .permitAll()
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/missions/*/participants/*/slim")
                    .permitAll()
                    .requestMatchers("/api/v1/users/search")
                    .hasAnyRole("ADMIN", "OFFICER", "SQUADRON_MEMBER", "MEMBER")
                    // BANK_MANAGEMENT widening (REQ-BANK-009): the grants UI resolves grantees
                    // via the user lookup, and bank staff need not hold any org-role
                    // (REQ-BANK-008) — without this, a pure bank manager would receive 403 here.
                    .requestMatchers("/api/v1/users/lookup")
                    .hasAnyRole("ADMIN", "OFFICER", "SQUADRON_MEMBER", "MEMBER", "BANK_MANAGEMENT")
                    .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/users")
                    .hasAnyRole("ADMIN", "OFFICER", "SQUADRON_MEMBER", "MEMBER")
                    .requestMatchers(HttpMethod.GET, "/api/v1/users/*")
                    .hasAnyRole("ADMIN", "OFFICER", "SQUADRON_MEMBER", "MEMBER")
                    // Post Phase-4-Lockdown (MULTI_SQUADRON_PLAN.md section 2): flag-vergabe
                    // (Logistician/Mission-Manager) und attribute-patches sind admin-only. Die
                    // method-level @PreAuthorize auf UserController#patchLogistician /
                    // #patchMissionManager / #updateAttributes verlangt bereits hasRole('ADMIN');
                    // der Path-Matcher hier war ein Relikt der Pre-Phase-4-Konfiguration und wird
                    // jetzt mit der Method-Level-Annotation in Deckung gebracht, damit
                    // SecurityConfig nicht mehr suggeriert OFFICER duerfe diese Endpunkte
                    // erreichen.
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/users/*/logistician")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/users/*/mission-manager")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/users/*/attributes")
                    .hasRole("ADMIN")
                    // GET .../memberships ist die Picker-Read-Variante (SPEZIALKOMMANDO_PLAN.md
                    // §7.4) — gibt nur OrgUnit-Names + Shorthands zurueck, keine PII. Wird vom
                    // Frontend SquadronContextAdvice (Sidebar-Switcher + Bereichskontext-Chip)
                    // sowie von den R5.d Owner-Picker-Fragments fuer jeden authenticated Caller
                    // gelesen. Ohne diese explizite Regel faellt die URL in die catch-all
                    // `/api/v1/users/**` darunter — die ist `hasRole("ADMIN")` und verursachte
                    // einen 403 fuer Non-Admins beim eigenen Memberships-Lookup (Sidebar-Chip
                    // zeigte dann "Kein Bereichskontext"). Der Method-Level @PreAuthorize auf
                    // UserController#getUserMemberships ist die zweite Verteidigungslinie
                    // (defense in depth) und bleibt das Source-of-truth fuer die zulaessigen
                    // Rollen — die URL-Regel oeffnet nur das Tor.
                    .requestMatchers(HttpMethod.GET, "/api/v1/users/*/memberships")
                    .hasAnyRole("ADMIN", "OFFICER", "SQUADRON_MEMBER", "MEMBER")
                    .requestMatchers("/api/v1/users/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/hangar/my-ships")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/hangar/ships")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/v1/hangar/ships/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/hangar/ships/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/hangar/import/ships")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/hangar/import/fleetview")
                    .authenticated()
                    .requestMatchers("/api/v1/hangar/**")
                    .hasAnyAuthority("HANGAR_READ", "HANGAR_WRITE", "ROLE_ADMIN")
                    .requestMatchers(
                        "/api/v1/inventory/my-inventory", "/api/v1/inventory/my-inventory/**")
                    .authenticated()
                    .requestMatchers("/api/v1/inventory", "/api/v1/inventory/**")
                    .hasAnyRole("ADMIN", "OFFICER", "LOGISTICIAN", "SQUADRON_MEMBER", "MEMBER")
                    .requestMatchers("/api/v1/personal-inventory", "/api/v1/personal-inventory/**")
                    .authenticated()
                    .requestMatchers("/api/v1/uex/locations/**")
                    .authenticated()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    // Bank admin carve-out (REQ-BANK-010/-012): wipe reset and the audit log are
                    // URL-gated to ADMIN on top of the method-level @PreAuthorize — bank
                    // management explicitly does NOT pass. The rest of /api/v1/bank/** rides the
                    // authenticated() catch-all plus the BankSecurityService method gates.
                    .requestMatchers("/api/v1/bank/admin/**")
                    .hasRole("ADMIN")
                    // Activity audit logs (REQ-AUDIT-001, ADR-0037): the per-area viewer and PDF
                    // export are URL-gated to ADMIN on top of the method-level @PreAuthorize.
                    .requestMatchers("/api/v1/audit/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        // REQ-SEC-017 (PR review #1): a PENDING/REJECTED registration is authenticated but carries
        // only ROLE_PENDING_APPROVAL; this filter 403s it on every /api/** endpoint (except the
        // registration-status read) so the "no access until approved" guarantee holds at the
        // backend
        // boundary, not just via the frontend redirect. Placed after the bearer-token filter so the
        // authorities are already assembled.
        .addFilterAfter(
            new PendingApprovalAccessFilter(),
            org.springframework.security.oauth2.server.resource.web.authentication
                .BearerTokenAuthenticationFilter.class)
        // L-11: backend is a pure JWT-bearer resource server — no HTTP session needed for any
        // endpoint. Pinning STATELESS makes the contract explicit: a future bug that introduces
        // {@code @SessionAttributes} or {@code request.getSession(true)} on a permitAll POST is
        // caught at startup rather than silently creating a session per anonymous caller.
        .sessionManagement(
            sm ->
                sm.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS));

    return http.build();
  }

  /**
   * Per-environment CORS configuration.
   *
   * <p>The allowed origin patterns come from {@code app.cors.allowed-origin-patterns} — empty by
   * default because the only legitimate caller is the Spring-Boot frontend running server-side, NOT
   * a browser. {@code allowCredentials=false} is intentional and load-bearing: combined with a
   * future misconfigured wildcard origin list it would be the difference between a 403 and a CSRF
   * exposure across the entire API.
   *
   * @return CORS source applied to all paths
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(allowedOriginPatterns);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "Accept-Language",
            "X-Correlation-Id",
            "X-Requested-With",
            "X-XSRF-TOKEN"));
    // Credentials are intentionally disabled. The backend does not authenticate
    // browsers directly; the frontend exchanges tokens server-side and proxies
    // every request. Allowing credentials here would magnify the impact of any
    // future origin-list misconfiguration (open-CORS-with-credentials).
    configuration.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}

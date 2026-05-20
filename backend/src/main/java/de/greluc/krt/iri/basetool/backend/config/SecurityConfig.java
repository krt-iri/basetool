package de.greluc.krt.iri.basetool.backend.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
                      "/api/v1/finance-entries"));
    }

    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .headers(
            headers -> {
              headers.contentSecurityPolicy(
                  // Audit finding M-9: added {@code form-action 'self'} and {@code
                  // upgrade-insecure-requests}. The backend serves Swagger UI in addition to the
                  // JSON API, so form-action restricts where a (theoretical) injected form on the
                  // Swagger HTML could POST, and upgrade-insecure-requests prevents the rare
                  // mixed-content download link from falling back to HTTP.
                  csp ->
                      csp.policyDirectives(
                          "default-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors"
                              + " 'none'; form-action 'self'; upgrade-insecure-requests;"
                              + " connect-src 'self'; img-src 'self' data:; font-src 'self' data:;"
                              + " style-src 'self' 'unsafe-inline'; script-src 'self'"));
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
                auth.requestMatchers(
                        "/error", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
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
                    .requestMatchers(HttpMethod.POST, "/api/v1/finance-entries")
                    .permitAll()
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
                    .requestMatchers("/api/v1/users/lookup")
                    .hasAnyRole("ADMIN", "OFFICER", "SQUADRON_MEMBER", "MEMBER")
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
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
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

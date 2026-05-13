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
   * a future mobile web app on https://mobile.iri-base.org).
   */
  @Value("${app.cors.allowed-origin-patterns:}")
  private List<String> allowedOriginPatterns;

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

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter(
      CustomJwtGrantedAuthoritiesConverter customConverter) {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(customConverter);
    return converter;
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      JwtAuthenticationConverter jwtAuthenticationConverter,
      org.springframework.core.env.Environment env)
      throws Exception {

    boolean isTest = java.util.Arrays.asList(env.getActiveProfiles()).contains("test");

    if (isTest) {
      http.csrf(
          org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
              ::disable);
    } else {
      http.csrf(
          csrf ->
              csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                  .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
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
                  csp ->
                      csp.policyDirectives(
                          "default-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; connect-src 'self'; img-src 'self' data:; font-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'"));
              headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny);
              headers.referrerPolicy(
                  ref -> ref.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
              headers.addHeaderWriter(
                  new org.springframework.security.web.header.writers.StaticHeadersWriter(
                      "Permissions-Policy",
                      "geolocation=(), camera=(), microphone=(), fullscreen=()"));
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
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/users/*/logistician")
                    .hasAnyRole("ADMIN", "OFFICER")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/users/*/mission-manager")
                    .hasAnyRole("ADMIN", "OFFICER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/users/*/attributes")
                    .hasAnyRole("ADMIN", "OFFICER")
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
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

    return http.build();
  }

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

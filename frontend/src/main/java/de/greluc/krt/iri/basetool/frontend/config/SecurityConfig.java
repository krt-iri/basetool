package de.greluc.krt.iri.basetool.frontend.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/** Spring configuration for Security. */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {
  private final RequestLoggingFilter requestLoggingFilter;
  private final BackendRoleSyncFilter backendRoleSyncFilter;
  private final BotProtectionFilter botProtectionFilter;

  /**
   * Optional — only wired in {@code dev} / {@code test} profiles (see {@link SessionDebugFilter}'s
   * {@code @Profile} annotation). In prod the bean does not exist and the filter is skipped (audit
   * finding M-15: the filter logs raw session ids + principal names which must never reach prod
   * logs even if an operator flips the per-class log level).
   */
  private final org.springframework.beans.factory.ObjectProvider<SessionDebugFilter>
      sessionDebugFilter;

  private final SsoReAuthenticationEntryPoint ssoReAuthenticationEntryPoint;
  private final CspNonceFilter cspNonceFilter;

  // CSP migration milestone: the ~200 inline event-handler attributes (onclick="…",
  // onchange="…", onsubmit="…", oninput="…", onkeyup="…") that historically pinned us to a
  // {@code script-src-attr 'unsafe-inline'} allowance have been moved to delegated handlers
  // via the {@code data-trigger}-based dispatcher in {@code event-delegation.js} +
  // {@code common-handlers.js} and per-page {@code krtEvents.on(...)} bindings in template
  // {@code <script th:attr="nonce=${cspNonce}">} blocks. With zero inline {@code on*=}
  // attributes remaining in the templates ({@code grep} verified), the policy drops
  // {@code script-src-attr} entirely — the directive defaults to {@code 'none'} when omitted
  // (CSP3 spec, MDN <a href=
  // "https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Security-Policy/script-src-attr"
  // >script-src-attr</a>), which slams the door on stored-XSS via a future template that
  // accidentally re-introduces an inline event handler. {@code <script>} elements stay
  // nonce-gated through {@code script-src} below — unchanged.
  // Audit findings M-9 + M-10:
  //   * M-10: dropped the broad {@code https:} fallback from {@code script-src}. Browsers that
  //     understand {@code 'strict-dynamic'} (Chrome 52+, Firefox 52+, Safari 15.4+) ignore the
  //     fallback anyway, but older browsers fell back to "any HTTPS origin loads scripts" — that
  //     widened the policy unnecessarily for &lt;1% of installed-base browsers. With the fallback
  //     removed the policy is uniformly strict across browsers.
  //   * M-9: added {@code form-action 'self'} (an injected {@code &lt;form action=evil&gt;} cannot
  //     POST any visible field — including the CSRF token — to a third-party origin) and {@code
  //     upgrade-insecure-requests} (auto-rewrites HTTP subresources to HTTPS so a mixed-content
  //     bug never falls back to plain HTTP).
  // Audit finding L-3 (2026-05-20): {@code style-src} no longer carries {@code 'unsafe-inline'}.
  // Every {@code <style>} block in the templates ({@code grep} verified: 38 blocks across the
  // page set) was patched to {@code <style th:attr="nonce=${cspNonce}">}, so {@code <style>}
  // elements now load only with the per-request nonce — an injected {@code <style>} tag from a
  // stored-XSS vector cannot be evaluated by the browser anymore. The {@code style=""}
  // attributes on individual elements (859 across the templates) are a separate CSP3 directive
  // and stay allowed via the explicit {@code style-src-attr 'unsafe-inline'} fallback: rewriting
  // them out is a much larger refactor (many are dynamically computed via {@code th:style} —
  // progress bars, color swatches, conditional visibility) and they cannot run JavaScript, so
  // the residual attack surface is restricted to visual / CSS-injection harm. The directive is
  // pinned explicitly so a future tightening can move from {@code 'unsafe-inline'} to a
  // {@code 'unsafe-hashes' 'sha256-…'} allow-list incrementally without touching {@code
  // style-src}.
  private static final String CSP_TEMPLATE =
      "default-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; "
          + "form-action 'self'; upgrade-insecure-requests; "
          + "img-src 'self' data:; font-src 'self' data:; "
          + "style-src 'self' 'nonce-%1$s'; "
          + "style-src-attr 'unsafe-inline'; "
          + "script-src 'nonce-%1$s' 'strict-dynamic'";

  private org.springframework.security.web.header.HeaderWriter cspNonceHeaderWriter() {
    return (request, response) -> {
      Object nonceAttr = request.getAttribute(CspNonceFilter.REQUEST_ATTRIBUTE);
      String nonce = nonceAttr != null ? nonceAttr.toString() : "";
      response.setHeader("Content-Security-Policy", String.format(CSP_TEMPLATE, nonce));
    };
  }

  /**
   * Declares the Keycloak-role inheritance: {@code ADMIN} inherits {@code LOGISTICIAN} and {@code
   * MISSION_MANAGER}; {@code OFFICER} inherits {@code LOGISTICIAN} and {@code MISSION_MANAGER}.
   * Mirrored from {@code ROLES_AND_PERMISSIONS.md} - keep both in sync.
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
   * Main security filter chain. Wires CSP-nonce, bot-protection, session-debug, request-logging and
   * backend-role-sync filters; configures OAuth2 login against Keycloak with smart OIDC logout; and
   * declares the path-by-path permitAll / authenticated matrix.
   */
  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      ClientRegistrationRepository clientRegistrationRepository,
      RoleHierarchy roleHierarchy)
      throws Exception {
    SmartOidcLogoutSuccessHandler oidcLogoutSuccessHandler =
        new SmartOidcLogoutSuccessHandler(clientRegistrationRepository, "{baseUrl}");

    http.addFilterBefore(
            cspNonceFilter, org.springframework.security.web.header.HeaderWriterFilter.class)
        .addFilterBefore(
            botProtectionFilter,
            org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter
                .class);
    // M-15: SessionDebugFilter is only registered in dev / test (see its {@code @Profile}). In
    // prod the bean does not exist; we skip the addFilterBefore call entirely to keep the chain
    // clean rather than wiring a no-op.
    sessionDebugFilter.ifAvailable(
        f ->
            http.addFilterBefore(
                f,
                org.springframework.security.web.context.request.async
                    .WebAsyncManagerIntegrationFilter.class));
    http.addFilterBefore(
            requestLoggingFilter,
            org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter
                .class)
        .addFilterBefore(backendRoleSyncFilter, AuthorizationFilter.class)
        // Audit finding H-6: previously `/missions/**`, `/operations/**`, `/hangar/import/**`,
        // `/hangar/ships/all` and `/inventory/**` were carved out of CSRF protection entirely.
        // Those routes are session-cookie-authenticated frontend handlers — exactly the surface
        // CSRF protects. `SameSite=Strict` on the session cookie mitigates classical cross-site
        // form-POSTs, but defence-in-depth wants the token check enabled too. AJAX call sites
        // already attach `X-XSRF-TOKEN` (see `mission-subresource.js`); Thymeleaf forms with
        // `th:action` auto-include the `_csrf` hidden field through Spring Security's view
        // integration. The default Spring Security CSRF setup is therefore left intact, no
        // ignoringRequestMatchers needed.
        .csrf(org.springframework.security.config.Customizer.withDefaults())
        .headers(
            headers -> {
              headers.addHeaderWriter(cspNonceHeaderWriter());
              headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny);
              headers.referrerPolicy(
                  ref -> ref.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
              // M-12: Cross-Origin-Opener-Policy + Cross-Origin-Resource-Policy. Same rationale
              // as the backend — COOP isolates the browsing context group, CORP prevents
              // cross-origin embedding of frontend resources via {@code <img>} / {@code <script>}.
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
              // Audit finding H-9: explicit HSTS. Frontend is reached over HTTPS in production;
              // behind nginx-proxy-manager with X-Forwarded-Proto the default writer works,
              // but pinning the policy here makes the contract explicit and prod-safe even if
              // the proxy headers are misconfigured.
              headers.httpStrictTransportSecurity(
                  hsts -> hsts.includeSubDomains(true).preload(true).maxAgeInSeconds(31_536_000L));
              headers.addHeaderWriter(
                  new org.springframework.security.web.header.writers.StaticHeadersWriter(
                      "Permissions-Policy",
                      // L-3: explicit deny for every browser feature the app does not use.
                      "geolocation=(), camera=(), microphone=(), fullscreen=(),"
                          + " payment=(), usb=(), serial=(), bluetooth=(), accelerometer=(),"
                          + " gyroscope=(), magnetometer=(), display-capture=(),"
                          + " clipboard-read=(), clipboard-write=(), interest-cohort=()"));
              headers.contentTypeOptions(Customizer.withDefaults());
            })
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        org.springframework.http.HttpMethod.POST,
                        "/missions/*/managers",
                        "/missions/*/managers/*")
                    .authenticated()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.DELETE, "/missions/*/managers/*")
                    .authenticated()
                    // Actuator health endpoint reached by the Docker HEALTHCHECK / compose
                    // service_healthy gating. Other actuator endpoints fall through to the
                    // anyRequest().authenticated() catch-all below.
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(
                        "/",
                        "/error",
                        "/error/**",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/logos/**",
                        "/fonts/**",
                        "/impressum",
                        "/privacy",
                        "/terms",
                        // M-17: static robots.txt with "Disallow: /" so legitimate crawlers
                        // (Google, Bing, archive.org) get an explicit no-index preference
                        // instead of a custom-app-signal 404.
                        "/robots.txt",
                        // Browser-side asset paths that must never trigger an OAuth2 entry-point
                        // (and therefore must never land in HttpSessionRequestCache as a saved
                        // request). Background sourcemap lookups fired by DevTools and browser
                        // extensions (Sentry Replay's /sm/<hash>.map, plus generic /**/*.map
                        // probes against vendor bundles we ship without sourcemaps) used to fall
                        // into the anyRequest().authenticated() catch-all — the saved URL was
                        // then replayed by SavedRequestAwareAuthenticationSuccessHandler after a
                        // user clicked Login, landing them on the custom 404 page instead of /.
                        // Resource handler returns 404 for the missing files, which is the
                        // correct contract for the browser. Defense-in-depth for any asset path
                        // missed here lives in AssetAwareAuthenticationSuccessHandler.
                        "/favicon.ico",
                        "/sm/**",
                        "/**/*.map")
                    .permitAll()
                    .requestMatchers("/missions", "/missions/")
                    .permitAll()
                    .requestMatchers("/missions/**")
                    .permitAll() // Still permitAll for general access, @PreAuthorize or logic
                    // inside handles details
                    // Mission-detail presence WebSocket: only authenticated users can join the
                    // awareness channel (anonymous guests browsing a mission detail page must
                    // not appear as "editors" and must not see who is editing). The OIDC
                    // session is reused by the WebSocket handshake.
                    .requestMatchers("/ws/missions/**")
                    .authenticated()
                    .requestMatchers("/operations", "/operations/")
                    .permitAll()
                    .requestMatchers("/operations/**")
                    .permitAll()
                    .requestMatchers("/orders", "/orders/")
                    .permitAll()
                    .requestMatchers("/orders/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2Login(
            oauth2 ->
                oauth2
                    .loginPage("/oauth2/authorization/keycloak")
                    .failureUrl("/?error")
                    .successHandler(oauth2LoginSuccessHandler())
                    .authorizationEndpoint(
                        auth ->
                            auth.authorizationRequestResolver(
                                authorizationRequestResolver(clientRegistrationRepository)))
                    .userInfoEndpoint(
                        userInfo -> userInfo.userAuthoritiesMapper(userAuthoritiesMapper())))
        .logout(
            logout ->
                logout
                    .logoutRequestMatcher(
                        request ->
                            request.getRequestURI().equals(request.getContextPath() + "/logout"))
                    .logoutSuccessHandler(oidcLogoutSuccessHandler))
        .exceptionHandling(ex -> ex.authenticationEntryPoint(ssoReAuthenticationEntryPoint))
        // M-14: explicit session-management policy.
        //   * sessionFixation(changeSessionId) is Spring Security's default since 4.x; pinning it
        //     here makes the contract explicit so a future regression that switches to {@code
        //     none} or {@code migrateSession} is visible in code review.
        //   * maximumSessions(2) caps a single user to two concurrent sessions (e.g. desktop +
        //     phone); a stolen cookie used in parallel will eventually push the legitimate
        //     session out. maxSessionsPreventsLogin(false) keeps the UX as "most recent login
        //     wins" rather than "new login refused" — combined with the cookie SameSite=Strict
        //     this is the right trade-off for a member-facing app.
        .sessionManagement(
            sm ->
                sm.sessionFixation(
                        org.springframework.security.config.annotation.web.configurers
                                .SessionManagementConfigurer.SessionFixationConfigurer
                            ::changeSessionId)
                    .maximumSessions(2)
                    .maxSessionsPreventsLogin(false));
    return http.build();
  }

  /**
   * Publishes Spring's session-lifecycle events so {@link
   * org.springframework.security.core.session.SessionRegistry} (used by {@code
   * sessionManagement().maximumSessions(...)}) can track session creation / destruction. Without
   * this bean the concurrent-session control silently no-ops. Audit finding M-14.
   *
   * @return the publisher bean
   */
  @Bean
  public org.springframework.security.web.session.HttpSessionEventPublisher
      httpSessionEventPublisher() {
    return new org.springframework.security.web.session.HttpSessionEventPublisher();
  }

  /**
   * Builds the post-OAuth2-login success handler. Wraps the default {@link
   * org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler}
   * so that saved requests pointing to static-asset URLs (background sourcemap lookups,
   * favicon/font/CSS probes from DevTools and browser extensions) are dropped and the user is sent
   * to the context root instead of a 404 asset path. See {@link
   * AssetAwareAuthenticationSuccessHandler} for the full rationale and the matched-path list.
   *
   * @return the success handler wired into {@code .oauth2Login().successHandler(...)}
   */
  @Bean
  public AuthenticationSuccessHandler oauth2LoginSuccessHandler() {
    return new AssetAwareAuthenticationSuccessHandler();
  }

  private OAuth2AuthorizationRequestResolver authorizationRequestResolver(
      ClientRegistrationRepository clientRegistrationRepository) {
    DefaultOAuth2AuthorizationRequestResolver defaultResolver =
        new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository, "/oauth2/authorization");

    return new OAuth2AuthorizationRequestResolver() {
      @Override
      public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest req = defaultResolver.resolve(request);
        return customizeAuthorizationRequest(req, request);
      }

      @Override
      public OAuth2AuthorizationRequest resolve(
          HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest req = defaultResolver.resolve(request, clientRegistrationId);
        return customizeAuthorizationRequest(req, request);
      }

      private OAuth2AuthorizationRequest customizeAuthorizationRequest(
          OAuth2AuthorizationRequest req, HttpServletRequest request) {
        if (req == null) {
          return null;
        }
        java.util.Locale locale =
            org.springframework.web.servlet.support.RequestContextUtils.getLocale(request);
        Map<String, Object> additionalParameters =
            new java.util.HashMap<>(req.getAdditionalParameters());
        additionalParameters.put("ui_locales", locale.getLanguage());
        // Use Keycloak SSO session for silent re-authentication after service restarts.
        // If the Keycloak SSO session is still active, the user is re-authenticated
        // transparently without interaction. If not, Keycloak shows the login page.
        String prompt = request.getParameter("prompt");
        if ("none".equals(prompt)) {
          additionalParameters.put("prompt", "none");
        }
        return OAuth2AuthorizationRequest.from(req)
            .additionalParameters(additionalParameters)
            .build();
      }
    };
  }

  /**
   * Maps Keycloak realm roles from the OIDC token's {@code realm_access.roles} claim onto Spring
   * {@code ROLE_…} authorities (upper-cased, spaces replaced with underscores).
   */
  @Bean
  @SuppressWarnings("unchecked")
  public GrantedAuthoritiesMapper userAuthoritiesMapper() {
    return (authorities) -> {
      Set<GrantedAuthority> mappedAuthorities = new HashSet<>();

      authorities.forEach(
          authority -> {
            mappedAuthorities.add(authority);
            if (authority instanceof OidcUserAuthority oidcUserAuthority) {
              // Audit finding H-11: only log the attribute keys, never the values. The full
              // attribute map carries `email`, `preferred_username`, `given_name`, `family_name`
              // — all of which the PiiMasker only partially scrubs and which this package's
              // TRACE-by-default Logback config would emit on every login in production.
              log.debug(
                  "OidcUserAuthority attribute keys: {}",
                  oidcUserAuthority.getAttributes().keySet());
              Map<String, Object> realmAccess =
                  (Map<String, Object>) oidcUserAuthority.getAttributes().get("realm_access");
              if (realmAccess != null && realmAccess.containsKey("roles")) {
                Collection<String> roles = (Collection<String>) realmAccess.get("roles");
                log.info("Mapping realm roles from Keycloak: {}", roles);
                roles.forEach(
                    role -> {
                      String mappedRole = "ROLE_" + role.toUpperCase().replace(" ", "_");
                      log.debug("Mapped role: {}", mappedRole);
                      mappedAuthorities.add(new SimpleGrantedAuthority(mappedRole));
                    });
              }
            }
          });

      return mappedAuthorities;
    };
  }
}

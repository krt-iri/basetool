package de.greluc.krt.iri.basetool.frontend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
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
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.DefaultHttpSecurityExpressionHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {
    private final RequestLoggingFilter requestLoggingFilter;
    private final BackendRoleSyncFilter backendRoleSyncFilter;
    private final BotProtectionFilter botProtectionFilter;
    private final SessionDebugFilter sessionDebugFilter;
    private final SsoReAuthenticationEntryPoint ssoReAuthenticationEntryPoint;
    private final CspNonceFilter cspNonceFilter;

    private static final String CSP_TEMPLATE =
            "default-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; "
            + "img-src 'self' data:; font-src 'self' data:; "
            + "style-src 'self' 'unsafe-inline'; "
            + "script-src 'nonce-%s' 'strict-dynamic' https:; "
            // The templates carry ~200 inline event-handler attributes (onclick="…",
            // onsubmit="…", …). Nonces and 'strict-dynamic' do NOT cover those (they
            // only cover <script> elements). Until those are migrated to addEventListener
            // we keep them runnable via a separate, narrowly-scoped script-src-attr
            // directive. Inline <script> elements remain strictly nonce-gated by the
            // line above - the XSS surface that matters most is unaffected.
            + "script-src-attr 'unsafe-inline'";

    private org.springframework.security.web.header.HeaderWriter cspNonceHeaderWriter() {
        return (request, response) -> {
            Object nonceAttr = request.getAttribute(CspNonceFilter.REQUEST_ATTRIBUTE);
            String nonce = nonceAttr != null ? nonceAttr.toString() : "";
            response.setHeader("Content-Security-Policy", String.format(CSP_TEMPLATE, nonce));
        };
    }

    @Bean
    public static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_ADMIN > ROLE_LOGISTICIAN
                ROLE_OFFICER > ROLE_LOGISTICIAN
                ROLE_ADMIN > ROLE_MISSION_MANAGER
                ROLE_OFFICER > ROLE_MISSION_MANAGER
                """);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ClientRegistrationRepository clientRegistrationRepository, RoleHierarchy roleHierarchy) throws Exception {
        SmartOidcLogoutSuccessHandler oidcLogoutSuccessHandler =
                new SmartOidcLogoutSuccessHandler(clientRegistrationRepository, "{baseUrl}");

        http
            .addFilterBefore(cspNonceFilter, org.springframework.security.web.header.HeaderWriterFilter.class)
            .addFilterBefore(botProtectionFilter, org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter.class)
            .addFilterBefore(sessionDebugFilter, org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter.class)
            .addFilterBefore(requestLoggingFilter, org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter.class)
            .addFilterBefore(backendRoleSyncFilter, AuthorizationFilter.class)
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(
                    "/missions/**",
                    "/operations/**",
                    "/hangar/import/fleetview",
                    "/hangar/ships/all",
                    "/inventory/**"
                )
            )
            .headers(headers -> {
                headers.addHeaderWriter(cspNonceHeaderWriter());
                headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny);
                headers.referrerPolicy(ref -> ref.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                headers.addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter("Permissions-Policy", "geolocation=(), camera=(), microphone=(), fullscreen=()"));
                headers.contentTypeOptions(Customizer.withDefaults());
            })
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/missions/*/managers", "/missions/*/managers/*").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/missions/*/managers/*").authenticated()
                // Actuator health endpoint reached by the Docker HEALTHCHECK / compose
                // service_healthy gating. Other actuator endpoints fall through to the
                // anyRequest().authenticated() catch-all below.
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/", "/error", "/css/**", "/js/**", "/images/**", "/logos/**", "/fonts/**", "/impressum", "/privacy", "/terms").permitAll()
                .requestMatchers("/missions", "/missions/").permitAll()
                .requestMatchers("/missions/**").permitAll() // Still permitAll for general access, @PreAuthorize or logic inside handles details
                .requestMatchers("/operations", "/operations/").permitAll()
                .requestMatchers("/operations/**").permitAll()
                .requestMatchers("/orders", "/orders/").permitAll()
                .requestMatchers("/orders/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/oauth2/authorization/keycloak")
                .failureUrl("/?error")
                .authorizationEndpoint(auth -> auth.authorizationRequestResolver(authorizationRequestResolver(clientRegistrationRepository)))
                .userInfoEndpoint(userInfo -> userInfo.userAuthoritiesMapper(userAuthoritiesMapper()))
            )
            .logout(logout -> logout
                .logoutRequestMatcher(request -> request.getRequestURI().equals(request.getContextPath() + "/logout"))
                .logoutSuccessHandler(oidcLogoutSuccessHandler)
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(ssoReAuthenticationEntryPoint)
            );
        return http.build();
    }

    private OAuth2AuthorizationRequestResolver authorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        DefaultOAuth2AuthorizationRequestResolver defaultResolver = 
                new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization");

        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                OAuth2AuthorizationRequest req = defaultResolver.resolve(request);
                return customizeAuthorizationRequest(req, request);
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                OAuth2AuthorizationRequest req = defaultResolver.resolve(request, clientRegistrationId);
                return customizeAuthorizationRequest(req, request);
            }

            private OAuth2AuthorizationRequest customizeAuthorizationRequest(OAuth2AuthorizationRequest req, HttpServletRequest request) {
                if (req == null) {
                    return null;
                }
                java.util.Locale locale = org.springframework.web.servlet.support.RequestContextUtils.getLocale(request);
                Map<String, Object> additionalParameters = new java.util.HashMap<>(req.getAdditionalParameters());
                additionalParameters.put("ui_locales", locale.getLanguage());
                // Use Keycloak SSO session for silent re-authentication after service restarts.
                // If the Keycloak SSO session is still active, the user is re-authenticated
                // transparently without interaction. If not, Keycloak shows the login page.
                String prompt = request.getParameter("prompt");
                if ("none".equals(prompt)) {
                    additionalParameters.put("prompt", "none");
                }
                return OAuth2AuthorizationRequest.from(req).additionalParameters(additionalParameters).build();
            }
        };
    }

    @Bean
    @SuppressWarnings("unchecked")
    public GrantedAuthoritiesMapper userAuthoritiesMapper() {
        return (authorities) -> {
            Set<GrantedAuthority> mappedAuthorities = new HashSet<>();

            authorities.forEach(authority -> {
                mappedAuthorities.add(authority);
                if (authority instanceof OidcUserAuthority oidcUserAuthority) {
                    log.debug("OidcUserAuthority attributes: {}", oidcUserAuthority.getAttributes());
                    Map<String, Object> realmAccess = (Map<String, Object>) oidcUserAuthority.getAttributes().get("realm_access");
                    if (realmAccess != null && realmAccess.containsKey("roles")) {
                        Collection<String> roles = (Collection<String>) realmAccess.get("roles");
                        log.info("Mapping realm roles from Keycloak: {}", roles);
                        roles.forEach(role -> {
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

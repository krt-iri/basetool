package de.greluc.krt.iri.basetool.frontend.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

class SecurityConfigTest {

  @Test
  void userAuthoritiesMapper_shouldMapRealmRoles() {
    // Arrange
    RequestLoggingFilter loggingFilter = mock(RequestLoggingFilter.class);
    BackendRoleSyncFilter roleSyncFilter = mock(BackendRoleSyncFilter.class);
    BotProtectionFilter botProtectionFilter = mock(BotProtectionFilter.class);
    SessionDebugFilter sessionDebugFilter = mock(SessionDebugFilter.class);
    SsoReAuthenticationEntryPoint ssoEntryPoint = mock(SsoReAuthenticationEntryPoint.class);
    CspNonceFilter cspNonceFilter = mock(CspNonceFilter.class);
    SecurityConfig config =
        new SecurityConfig(
            loggingFilter,
            roleSyncFilter,
            botProtectionFilter,
            sessionDebugFilter,
            ssoEntryPoint,
            cspNonceFilter);
    GrantedAuthoritiesMapper mapper = config.userAuthoritiesMapper();

    Map<String, Object> realmAccess = new HashMap<>();
    realmAccess.put("roles", List.of("Officer", "Squadron Member", "admin"));

    Map<String, Object> claims = new HashMap<>();
    claims.put("realm_access", realmAccess);
    claims.put("sub", "user-id");

    OidcIdToken idToken = new OidcIdToken("token-value", null, null, claims);
    OidcUserAuthority oidcAuth = new OidcUserAuthority(idToken);

    // Act
    Collection<? extends GrantedAuthority> mappedAuthorities =
        mapper.mapAuthorities(List.of(oidcAuth));

    // Assert
    Set<String> authorityStrings =
        mappedAuthorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

    assertTrue(authorityStrings.contains("ROLE_OFFICER"));
    assertTrue(authorityStrings.contains("ROLE_SQUADRON_MEMBER"));
    assertTrue(authorityStrings.contains("ROLE_ADMIN"));
  }
}

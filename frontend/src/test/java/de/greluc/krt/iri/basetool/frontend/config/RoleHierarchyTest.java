package de.greluc.krt.iri.basetool.frontend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = SecurityConfig.class)
@ActiveProfiles("test")
class RoleHierarchyTest {

    @MockitoBean
    private RequestLoggingFilter requestLoggingFilter;

    @MockitoBean
    private BackendRoleSyncFilter backendRoleSyncFilter;

    @MockitoBean
    private BotProtectionFilter botProtectionFilter;

    @MockitoBean
    private SessionDebugFilter sessionDebugFilter;

    @MockitoBean
    private SsoReAuthenticationEntryPoint ssoReAuthenticationEntryPoint;

    @MockitoBean
    private CspNonceFilter cspNonceFilter;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private RoleHierarchy roleHierarchy;

    @Test
    void officerShouldReachLogistician() {
        Collection<? extends GrantedAuthority> reachable = roleHierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_OFFICER"))
        );
        
        assertTrue(reachable.stream().anyMatch(a -> a.getAuthority().equals("ROLE_LOGISTICIAN")),
                "ROLE_OFFICER should reach ROLE_LOGISTICIAN. Reachable: " + reachable);
    }

    @Test
    void adminShouldReachLogistician() {
        Collection<? extends GrantedAuthority> reachable = roleHierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        
        assertTrue(reachable.stream().anyMatch(a -> a.getAuthority().equals("ROLE_LOGISTICIAN")),
                "ROLE_ADMIN should reach ROLE_LOGISTICIAN. Reachable: " + reachable);
    }
}

package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class InventoryLogisticianSyncTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private BackendApiClient backendApiClient;

    @MockitoBean
    private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void memberWithLogisticianFlag_ShouldSeeActions() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UserDto userDto = new UserDto(userId, "testuser", "Test User", "Test User", null, null, null, null, null, Set.of("MEMBER"), Collections.emptySet(), null, true, false, true, 1L);
        when(backendApiClient.get(eq("/api/v1/users/me"), eq(UserDto.class))).thenReturn(userDto);
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());

        Map<String, Object> claims = new HashMap<>();
        claims.put(IdTokenClaimNames.SUB, "test-sub");
        claims.put("preferred_username", "testuser");
        OidcIdToken idToken = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(3600), claims);
        OidcUser oidcUser = new DefaultOidcUser(Collections.emptyList(), idToken);
        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(oidcUser, Collections.emptyList(), "keycloak");

        // When & Then
        mockMvc.perform(get("/inventory/all").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Neuen Eintrag erfassen")));
    }

    @Test
    void memberWithoutLogisticianFlag_ShouldNotSeeActions() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UserDto userDto = new UserDto(userId, "testuser", "Test User", "Test User", null, null, null, null, null, Set.of("MEMBER"), Collections.emptySet(), null, false, false, true, 1L);
        when(backendApiClient.get(eq("/api/v1/users/me"), eq(UserDto.class))).thenReturn(userDto);
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());

        Map<String, Object> claims = new HashMap<>();
        claims.put(IdTokenClaimNames.SUB, "test-sub");
        claims.put("preferred_username", "testuser");
        OidcIdToken idToken = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(3600), claims);
        OidcUser oidcUser = new DefaultOidcUser(Collections.emptyList(), idToken);
        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(oidcUser, Collections.emptyList(), "keycloak");

        // When & Then
        mockMvc.perform(get("/inventory/all").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Neuen Eintrag erfassen"))));
    }

    @Test
    void officerWithoutLogisticianFlag_ShouldSeeActionsByHierarchy() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UserDto userDto = new UserDto(userId, "testuser", "Test User", "Test User", null, null, null, null, null, Set.of("OFFICER"), Collections.emptySet(), null, false, false, true, 1L);
        when(backendApiClient.get(eq("/api/v1/users/me"), eq(UserDto.class))).thenReturn(userDto);
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());

        Map<String, Object> claims = new HashMap<>();
        claims.put(IdTokenClaimNames.SUB, "test-sub");
        claims.put("preferred_username", "testuser");
        OidcIdToken idToken = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(3600), claims);
        OidcUser oidcUser = new DefaultOidcUser(Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OFFICER")), idToken);
        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "keycloak");

        // When & Then
        mockMvc.perform(get("/inventory/all").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Neuen Eintrag erfassen")));
    }
}

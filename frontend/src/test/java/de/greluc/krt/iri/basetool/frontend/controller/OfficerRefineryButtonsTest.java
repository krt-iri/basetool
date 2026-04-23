package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.RefineryOrderDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class OfficerRefineryButtonsTest {

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
    void refineryOrderDetail_AsOfficer_ShouldShowLogisticianButtons() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        
        // record RefineryOrderDto(UUID id, UserReferenceDto owner, LocationDto location, MissionReferenceDto mission, OffsetDateTime startedAt, Integer durationMinutes, Integer expenses, RefiningMethodDto refiningMethod, List<RefineryGoodDto> goods, RefineryOrderStatus status, Long version)
        RefineryOrderDto order = new RefineryOrderDto(
                orderId, null, null, null, java.time.Instant.now(), 60L, 100.0, 0d, 0d, null, java.util.Collections.emptyList(), de.greluc.krt.iri.basetool.frontend.model.dto.RefineryOrderStatus.OPEN, 1L
        );
        
        when(backendApiClient.get(eq("/api/v1/refinery-orders/" + orderId), eq(RefineryOrderDto.class))).thenReturn(order);
        when(backendApiClient.get(eq("/api/v1/users/me"), eq(UserDto.class))).thenReturn(null);

        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put(org.springframework.security.oauth2.core.oidc.IdTokenClaimNames.SUB, userId.toString());
        claims.put("preferred_username", "officer");
        org.springframework.security.oauth2.core.oidc.OidcIdToken idToken = new org.springframework.security.oauth2.core.oidc.OidcIdToken("token-value", java.time.Instant.now(), java.time.Instant.now().plusSeconds(3600), claims);
        org.springframework.security.oauth2.core.oidc.user.OidcUser oidcUser = new org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser(java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OFFICER")), idToken);
        org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken authToken = new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "keycloak");

        System.out.println("[DEBUG_LOG] Officer Authorities: " + authToken.getAuthorities());

        mockMvc.perform(get("/refinery-orders/" + orderId).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Einlagern"))); // Should be visible for LOGISTICIAN (Officer)
    }
}

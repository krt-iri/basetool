package de.greluc.krt.iri.basetool.frontend.controller;

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

import de.greluc.krt.iri.basetool.frontend.model.dto.*;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.time.Instant;
import java.util.*;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class OfficerRefineryAccessTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @MockitoBean private BackendApiClient backendApiClient;

  @MockitoBean
  private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
      clientRegistrationRepository;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void officer_ShouldBeAbleToSelectUserInCreateForm() throws Exception {
    // Given
    UUID userId = UUID.randomUUID();
    UserDto userDto =
        new UserDto(
            userId,
            "officer",
            "Officer",
            "Officer",
            null,
            null,
            null,
            null,
            null,
            Set.of("OFFICER"),
            Collections.emptySet(),
            null,
            false,
            false,
            true,
            1L,
            null);
    when(backendApiClient.get(eq("/api/v1/users/me"), eq(UserDto.class))).thenReturn(userDto);

    // Mocking user list for dropdown
    UserDto memberDto =
        new UserDto(
            UUID.randomUUID(),
            "member",
            "Member",
            "Member",
            null,
            null,
            null,
            null,
            null,
            Set.of("MEMBER"),
            Collections.emptySet(),
            null,
            false,
            false,
            true,
            1L,
            null);
    PageResponse<UserDto> userPage =
        new PageResponse<>(List.of(userDto, memberDto), 0, 10, 2L, 1, Collections.emptyList());
    when(backendApiClient.get(eq("/api/v1/users?size=1000"), any(ParameterizedTypeReference.class)))
        .thenReturn(userPage);

    // Mock other data
    when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class)))
        .thenReturn(Collections.emptyList());
    when(backendApiClient.get(
            eq("/api/v1/settings/refinery.rounding.mode"), eq(SystemSettingDto.class)))
        .thenReturn(new SystemSettingDto("refinery.rounding.mode", "UP", 1L));

    Map<String, Object> claims = new HashMap<>();
    claims.put(IdTokenClaimNames.SUB, userId.toString());
    claims.put("preferred_username", "officer");
    OidcIdToken idToken =
        new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(3600), claims);
    OidcUser oidcUser =
        new DefaultOidcUser(
            Collections.singletonList(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "ROLE_OFFICER")),
            idToken);
    OAuth2AuthenticationToken auth =
        new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "keycloak");

    // When & Then
    mockMvc
        .perform(get("/refinery-orders/create").with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("disabled=\"disabled\" id=\"ownerId\""))))
        .andExpect(content().string(containsString("id=\"ownerId\"")))
        .andExpect(content().string(containsString("Member")));
  }

  @Test
  void officer_ShouldSeeEditButtonsInDetails() throws Exception {
    // Given
    UUID officerId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();

    UserDto officerDto =
        new UserDto(
            officerId,
            "officer",
            "Officer",
            "Officer",
            null,
            null,
            null,
            null,
            null,
            Set.of("OFFICER"),
            Collections.emptySet(),
            null,
            false,
            false,
            true,
            1L,
            null);
    when(backendApiClient.get(eq("/api/v1/users/me"), eq(UserDto.class))).thenReturn(officerDto);

    UserReferenceDto ownerRef = new UserReferenceDto(otherUserId, "other", "Other", "Other", null);
    RefineryOrderDto orderDto =
        new RefineryOrderDto(
            orderId,
            ownerRef,
            null,
            null,
            java.time.Instant.now(),
            60L,
            1000.0,
            0d,
            0d,
            0d,
            null,
            Collections.emptyList(),
            RefineryOrderStatus.OPEN,
            null,
            1L);
    when(backendApiClient.get(eq("/api/v1/refinery-orders/" + orderId), eq(RefineryOrderDto.class)))
        .thenReturn(orderDto);

    when(backendApiClient.get(
            eq("/api/v1/settings/refinery.rounding.mode"), eq(SystemSettingDto.class)))
        .thenReturn(new SystemSettingDto("refinery.rounding.mode", "UP", 1L));

    Map<String, Object> claims = new HashMap<>();
    claims.put(IdTokenClaimNames.SUB, officerId.toString());
    claims.put("preferred_username", "officer");
    OidcIdToken idToken =
        new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(3600), claims);
    OidcUser oidcUser =
        new DefaultOidcUser(
            Collections.singletonList(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "ROLE_OFFICER")),
            idToken);
    OAuth2AuthenticationToken auth =
        new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "keycloak");

    // When & Then
    mockMvc
        .perform(get("/refinery-orders/" + orderId).with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Speichern")))
        .andExpect(content().string(containsString("Abbrechen")))
        .andExpect(content().string(containsString("Einlagern")));
  }
}

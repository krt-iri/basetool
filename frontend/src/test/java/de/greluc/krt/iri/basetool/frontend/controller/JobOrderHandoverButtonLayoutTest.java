package de.greluc.krt.iri.basetool.frontend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Verifiziert, dass der Button "Uebergabe protokollieren" auf der Auftragsdetailseite unterhalb des
 * Bearbeiter-Bereichs erscheint und nicht mehr im Header-Bereich.
 *
 * <p>Konkret wird geprueft:
 *
 * <ul>
 *   <li>Der Button erscheint nach dem Bearbeiter-Bereich (assignees) im HTML.
 *   <li>Der Button erscheint nicht mehr im Header-Navigationsbereich (flex-between).
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class JobOrderHandoverButtonLayoutTest {

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

  private OAuth2AuthenticationToken logisticianToken(UUID userId) {
    Map<String, Object> claims = new HashMap<>();
    claims.put(IdTokenClaimNames.SUB, userId.toString());
    claims.put("preferred_username", "logistician");
    OidcIdToken idToken =
        new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(3600), claims);
    OidcUser oidcUser =
        new DefaultOidcUser(
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_LOGISTICIAN")), idToken);
    return new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "keycloak");
  }

  @Test
  void orderDetail_HandoverButton_ShouldAppearAfterAssigneesSection_NotInHeader() throws Exception {
    // Given
    UUID orderId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    JobOrderDto order =
        new JobOrderDto(
            orderId,
            1,
            "SQR",
            null,
            null,
            "Handle",
            1,
            "OPEN",
            List.of(),
            List.of(),
            List.of(),
            Instant.now(),
            1L);

    when(backendApiClient.get(eq("/api/v1/orders/" + orderId), eq(JobOrderDto.class)))
        .thenReturn(order);
    when(backendApiClient.get(
            eq("/api/v1/users/me"),
            eq(de.greluc.krt.iri.basetool.frontend.model.dto.UserDto.class)))
        .thenReturn(null);

    // When
    MvcResult result =
        mockMvc
            .perform(get("/orders/" + orderId).with(authentication(logisticianToken(userId))))
            .andExpect(status().isOk())
            .andReturn();

    String html = result.getResponse().getContentAsString();

    // Then: Button muss nach dem Bearbeiter-Bereich erscheinen
    int assigneesSectionIndex = html.indexOf("Bearbeiter");
    int handoverButtonIndex = html.indexOf("openHandoverModal()");

    assertThat(assigneesSectionIndex)
        .as("Bearbeiter-Bereich muss im HTML vorhanden sein")
        .isGreaterThan(0);
    assertThat(handoverButtonIndex)
        .as("Handover-Button muss im HTML vorhanden sein")
        .isGreaterThan(0);
    assertThat(handoverButtonIndex)
        .as("Handover-Button muss nach dem Bearbeiter-Bereich erscheinen")
        .isGreaterThan(assigneesSectionIndex);

    // Then: Button darf nicht im Header-Bereich (flex-between) erscheinen
    int headerEnd = html.indexOf("</div>", html.indexOf("flex-between"));
    assertThat(handoverButtonIndex)
        .as("Handover-Button darf nicht im Header-Bereich (flex-between) erscheinen")
        .isGreaterThan(headerEnd);
  }
}

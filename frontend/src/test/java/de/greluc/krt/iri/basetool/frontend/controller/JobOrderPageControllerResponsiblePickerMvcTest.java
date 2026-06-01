package de.greluc.krt.iri.basetool.frontend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.SpecialCommandDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.SquadronDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Regression test for the "profit-eligible Spezialkommando never appears in the Job-Order
 * responsible picker" bug. The squadron catalog ({@code /api/v1/squadrons}) is permitAll, but the
 * SK catalog ({@code /api/v1/special-commands}) requires authentication; fetching it through the
 * anonymous public WebClient silently 401s and drops every SK from the picker, so an authenticated
 * caller saw only squadrons. The fix routes {@code fetchSpecialCommands()} through the
 * authenticated WebClient ({@code isPublic = false}). This test pins that an eligible SK reaches
 * the rendered picker and that the catalog is fetched authenticated, never via the public client.
 */
@SpringBootTest
@SuppressWarnings("unchecked")
class JobOrderPageControllerResponsiblePickerMvcTest {

  private static final String SQUADRONS_URI = "/api/v1/squadrons?size=1000&sort=name,asc";
  private static final String SK_URI = "/api/v1/special-commands?size=1000&sort=name,asc";

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
  @WithMockUser(roles = {"MEMBER", "LOGISTICIAN"})
  void viewCreateForm_eligibleSpecialCommand_appearsInResponsiblePicker() throws Exception {
    SquadronDto squadron =
        new SquadronDto(UUID.randomUUID(), "Test Staffel", "TS", null, true, false, true, 0L);
    SpecialCommandDto sk =
        new SpecialCommandDto(
            UUID.randomUUID(), "Profit Spezialkommando", "PSK", null, true, true, 0L);

    when(backendApiClient.getCached(
            eq(SQUADRONS_URI), any(ParameterizedTypeReference.class), eq(true)))
        .thenReturn(new PageResponse<>(List.of(squadron), 0, 1000, 1, 1, List.of()));
    when(backendApiClient.getCached(eq(SK_URI), any(ParameterizedTypeReference.class), eq(false)))
        .thenReturn(new PageResponse<>(List.of(sk), 0, 1000, 1, 1, List.of()));

    mockMvc
        .perform(get("/orders/create"))
        .andExpect(status().isOk())
        .andExpect(view().name("orders-create"))
        .andExpect(content().string(Matchers.containsString("Profit Spezialkommando")));

    // The SK catalog must be fetched through the authenticated (bearer-relaying) client, never the
    // anonymous public one — the latter 401s and silently drops every SK from the picker.
    verify(backendApiClient)
        .getCached(eq(SK_URI), any(ParameterizedTypeReference.class), eq(false));
    verify(backendApiClient, never())
        .getCached(eq(SK_URI), any(ParameterizedTypeReference.class), eq(true));
  }
}

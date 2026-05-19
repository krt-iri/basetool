package de.greluc.krt.iri.basetool.frontend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.frontend.config.SquadronContextAdvice;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.SquadronDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.List;
import java.util.UUID;
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
 * End-to-end frontend assertion for the per-squadron promotion-feature flag's deny path.
 *
 * <p>When a non-admin caller's home squadron has the flag OFF, every {@code /promotion/...} URL the
 * user could still try (typed by hand, stale bookmark, stale tab) must come back as HTTP 403 — even
 * though the regular {@code @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")} on the admin- subpath
 * is satisfied. The gate runs inside the page-controller method body, layered on top of the role
 * check, by reading the {@code promotionFeatureEnabled} model attribute populated by {@link
 * SquadronContextAdvice}.
 *
 * <p>Sidebar visibility is enforced by a single {@code th:if="${promotionFeatureEnabled}"} on the
 * fragments/sidebar.html group; the model attribute that drives it is the same one the controller
 * gate reads, so verifying the deny path here also covers the hide path on the rendered sidebar.
 */
@SpringBootTest
class PromotionFeatureFlagPageGateTest {

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private BackendApiClient backendApiClient;

  @MockitoBean
  private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
      clientRegistrationRepository;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private void stubSquadronContext(UUID squadronId, boolean promotionEnabled) {
    SquadronDto squadron =
        new SquadronDto(squadronId, "IRIDIUM", "IRI", null, true, promotionEnabled, 0L);
    when(backendApiClient.get(contains("/api/v1/squadrons"), any(ParameterizedTypeReference.class)))
        .thenReturn(new PageResponse<>(List.of(squadron), 0, 1000, 1, 1, List.of()));
    when(backendApiClient.get(
            eq("/api/v1/me/active-squadron"),
            eq(SquadronContextAdvice.ActiveSquadronResponse.class)))
        .thenReturn(new SquadronContextAdvice.ActiveSquadronResponse(squadronId));
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void officerWithFlagOff_overviewIsForbidden() throws Exception {
    stubSquadronContext(UUID.randomUUID(), false);
    mockMvc.perform(get("/promotion/overview")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void officerWithFlagOff_myEvaluationsIsForbidden() throws Exception {
    stubSquadronContext(UUID.randomUUID(), false);
    mockMvc.perform(get("/promotion/my-evaluations")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void officerWithFlagOff_manageIsForbidden() throws Exception {
    stubSquadronContext(UUID.randomUUID(), false);
    mockMvc.perform(get("/promotion/manage")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void officerWithFlagOff_adminTopicsIsForbidden() throws Exception {
    stubSquadronContext(UUID.randomUUID(), false);
    mockMvc.perform(get("/promotion/admin/topics")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void officerWithFlagOff_rankRequirementsIsForbidden() throws Exception {
    stubSquadronContext(UUID.randomUUID(), false);
    mockMvc.perform(get("/promotion/admin/rank-requirements")).andExpect(status().isForbidden());
  }
}

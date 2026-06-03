package de.greluc.krt.iri.basetool.frontend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.frontend.model.dto.AreaLeadershipDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OrgChartDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.SquadronChartDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.List;
import java.util.Map;
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
 * Full Thymeleaf render tests for the org-chart page. Renders {@code org-chart.html} together with
 * the {@code ocNode} person-node fragment through the real template engine, so a broken node
 * expression fails the build instead of only surfacing as a 500 at runtime (the pure-method {@link
 * OrgChartPageControllerTest} never touches the template).
 *
 * <p>Pins the empty-chart path: an unfilled Bereichsleiter or Staffelleiter seat must render its
 * vacant placeholder rather than invoking {@code ocNode} with a {@code null} node. That null-node
 * invocation is exactly what threw {@code EL1011E: ... positionType() on null context object} when
 * the {@code th:if} guard sat on the same element as {@code th:replace} (fragment inclusion runs
 * before the conditional, so the guard was dead code).
 */
@SpringBootTest
@SuppressWarnings("unchecked")
class OrgChartPageRenderTest {

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
  @WithMockUser(roles = "SQUADRON_MEMBER")
  void emptyChart_member_rendersVacantSeatInsteadOfInvokingNodeFragmentWithNull() throws Exception {
    // Given: a completely empty chart — the Bereichsleiter seat (area.lead) is null. Pre-fix this
    // rendered ocNode(null) and NPE'd; now the vacant placeholder must render and the page 200s.
    when(backendApiClient.get("/api/v1/org-chart", OrgChartDto.class))
        .thenReturn(
            new OrgChartDto(
                new AreaLeadershipDto(null, List.of(), List.of(), List.of()),
                List.of(),
                List.of()));

    // When / Then
    String html =
        mockMvc
            .perform(get("/org-chart"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(html).as("chart root rendered").contains("oc-chart");
    assertThat(html)
        .as("vacant Bereichsleiter placeholder, not an NPE")
        .contains("oc-node--vacant");
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void unledSquadron_admin_rendersVacantSquadronLeadAndEditorAffordance() throws Exception {
    // Given: a profit-eligible Staffel with no Staffelleiter (sq.lead == null) — the second
    // same-element guard that previously NPE'd. The admin view additionally renders the inline
    // editor's add affordances (sec:authorize ADMIN).
    when(backendApiClient.get("/api/v1/org-chart", OrgChartDto.class))
        .thenReturn(
            new OrgChartDto(
                new AreaLeadershipDto(null, List.of(), List.of(), List.of()),
                List.of(
                    new SquadronChartDto(
                        UUID.randomUUID(),
                        "IRIDIUM",
                        "IRI",
                        null,
                        List.of(),
                        List.of(),
                        true,
                        true)),
                List.of()));
    when(backendApiClient.get(eq("/api/v1/users/lookup"), any(ParameterizedTypeReference.class)))
        .thenReturn(List.of(Map.of("id", UUID.randomUUID().toString(), "effectiveName", "Pilot")));

    // When / Then
    String html =
        mockMvc
            .perform(get("/org-chart"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(html).as("Staffel column rendered").contains("IRIDIUM");
    assertThat(html).as("vacant Staffelleiter placeholder, not an NPE").contains("oc-node--vacant");
    assertThat(html)
        .as("admin assign-Staffelleiter affordance")
        .contains("data-position-type=\"SQUADRON_LEAD\"");
  }
}

/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.iri.basetool.frontend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.frontend.model.dto.AreaLeadershipDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.CommandChartDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OrgChartDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OrgChartNodeDto;
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
    assertThat(html).as("chart exposes an ARIA tree").contains("role=\"tree\"");
    assertThat(html).as("vacant Bereichsleiter is a level-1 treeitem").contains("aria-level=\"1\"");
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
    assertThat(html).as("child rows are ARIA groups").contains("role=\"group\"");
    assertThat(html).as("boxes are ARIA treeitems").contains("role=\"treeitem\"");
    assertThat(html).as("unit box sits at level 2").contains("aria-level=\"2\"");
    assertThat(html).as("vacant Staffelleiter sits at level 3").contains("aria-level=\"3\"");
    assertThat(html).as("edit toggle exposes its pressed state").contains("aria-pressed=\"false\"");
    assertThat(html).as("edit-mode hint rendered (hidden until editing)").contains("oc-edit-hint");
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void namedLeaderlessCommand_admin_rendersGroupHeaderVacantLeadAndChildren() throws Exception {
    // Given: a named Kommando with a vacant Kommandoleiter but an already-attached Stv. + Ensign —
    // the exact shape the decoupled command-group model introduces. Renders the group header
    // (name), the vacant-leader placeholder + assign affordance, and the child person-nodes via
    // ocNode. A broken expression in any of those new paths fails here instead of as a runtime 500.
    OrgChartNodeDto deputy =
        new OrgChartNodeDto(
            UUID.randomUUID(), "DEPUTY_COMMAND_LEAD", UUID.randomUUID(), "Deputy", 0, 0L);
    OrgChartNodeDto ensign =
        new OrgChartNodeDto(UUID.randomUUID(), "ENSIGN", UUID.randomUUID(), "Ensign", 0, 0L);
    CommandChartDto command =
        new CommandChartDto(UUID.randomUUID(), "Alpha", 0L, 0, null, null, deputy, List.of(ensign));
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
                        List.of(command),
                        List.of(),
                        true,
                        true)),
                List.of()));
    when(backendApiClient.get(eq("/api/v1/users/lookup"), any(ParameterizedTypeReference.class)))
        .thenReturn(List.of(Map.of("id", UUID.randomUUID().toString(), "effectiveName", "Pilot")));

    String html =
        mockMvc
            .perform(get("/org-chart"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(html).as("Kommando group header").contains("oc-command-head");
    assertThat(html).as("Kommando name rendered").contains("Alpha");
    assertThat(html).as("admin rename affordance").contains("data-trigger=\"oc-rename\"");
    assertThat(html).as("vacant Kommandoleiter placeholder").contains("oc-node--vacant");
    assertThat(html).as("child Stv. node rendered via ocNode").contains("Deputy");
    assertThat(html).as("child Ensign node rendered via ocNode").contains("Ensign");
    assertThat(html).as("command head is a level-4 treeitem").contains("aria-level=\"4\"");
    assertThat(html).as("vacant Kommandoleiter sits at level 5").contains("aria-level=\"5\"");
    assertThat(html).as("child Stv./Ensign nodes sit at level 6").contains("aria-level=\"6\"");
  }
}

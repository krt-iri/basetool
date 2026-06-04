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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.frontend.config.SquadronContextAdvice;
import de.greluc.krt.iri.basetool.frontend.model.dto.AggregatedMaterialDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.BlueprintReferenceDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.ClaimDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.GameItemReferenceDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderItemDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderItemHandoverDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderItemHandoverEntryDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderItemMaterialDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderMaterialDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.SquadronReferenceDto;
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
 * Verifies that the order-detail page renders the ITEM-order branch end to end: the ordered-items
 * table (with sub-assembly provenance and delivery progress), the internal aggregated-materials
 * panel (one row per material+quality with a Gut/Keine badge), and the warning banner for items
 * whose blueprint derived no procurable material. Renders through the real Thymeleaf template so a
 * broken expression in the new branch fails the build rather than only surfacing at runtime.
 */
@SpringBootTest
@ActiveProfiles("test")
class JobOrderItemDetailRenderTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @MockitoBean private BackendApiClient backendApiClient;

  @MockitoBean
  private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
      clientRegistrationRepository;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    // The logistician caller is a non-admin, so the order-detail profit gate would otherwise
    // redirect to /orders/create. Stub the capability as a profit-eligible viewer so the detail
    // render path runs.
    when(backendApiClient.get(
            "/api/v1/me/capabilities", SquadronContextAdvice.CapabilitiesResponse.class))
        .thenReturn(new SquadronContextAdvice.CapabilitiesResponse(true, true));
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

  private MaterialDto material(String name, String quantityType) {
    return new MaterialDto(
        UUID.randomUUID(),
        null,
        name,
        null,
        quantityType,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        1L);
  }

  @Test
  void itemOrderDetail_RendersItemTableAggregatedPanelAndUnresolvedBanner() throws Exception {
    // Given: an ITEM order with one fully-derived top-level item and one sub-assembly line whose
    // blueprint derived no material (empty materials list -> the no-materials banner must appear).
    UUID orderId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID parentId = UUID.randomUUID();

    MaterialDto acryliPlex = material("AcryliPlex Composite", "SCU");
    MaterialDto agricium = material("Agricium", "SCU");

    JobOrderItemDto topItem =
        new JobOrderItemDto(
            parentId,
            new GameItemReferenceDto(UUID.randomUUID(), "A03 Sniper Rifle", "WEAPON"),
            new BlueprintReferenceDto(UUID.randomUUID(), "A03 Sniper Rifle", "wiki-a03"),
            3,
            1,
            null,
            List.of(
                new JobOrderItemMaterialDto(UUID.randomUUID(), acryliPlex, 7.5, "GOOD", 1L),
                new JobOrderItemMaterialDto(UUID.randomUUID(), agricium, 12.0, "NONE", 1L)),
            1L);
    JobOrderItemDto subItem =
        new JobOrderItemDto(
            UUID.randomUUID(),
            new GameItemReferenceDto(UUID.randomUUID(), "A03 Optic Scope", "WEAPON_ATTACHMENT"),
            new BlueprintReferenceDto(UUID.randomUUID(), "A03 Optic Scope", "wiki-scope"),
            2,
            0,
            parentId,
            List.of(),
            1L);

    JobOrderDto order =
        new JobOrderDto(
            orderId,
            7,
            null,
            null,
            "Handle",
            null,
            1,
            "OPEN",
            "ITEM",
            List.of(),
            List.of(topItem, subItem),
            List.of(
                new AggregatedMaterialDto(acryliPlex, "GOOD", 7.5, List.of(), null),
                new AggregatedMaterialDto(agricium, "NONE", 12.0, List.of(), null)),
            List.of(),
            List.of(),
            List.of(),
            Instant.now(),
            1L);

    when(backendApiClient.get(eq("/api/v1/orders/" + orderId), eq(JobOrderDto.class)))
        .thenReturn(order);

    // When
    MvcResult result =
        mockMvc
            .perform(get("/orders/" + orderId).with(authentication(logisticianToken(userId))))
            .andExpect(status().isOk())
            .andReturn();

    String html = result.getResponse().getContentAsString();

    // Then: the ITEM-kind chip and ordered items render.
    assertThat(html).as("ITEM kind badge").contains("order-kind-item");
    assertThat(html)
        .as("does not show the MATERIAL chip on an item order")
        .doesNotContain("order-kind-material");
    assertThat(html).as("top-level ordered item name").contains("A03 Sniper Rifle");
    assertThat(html).as("sub-assembly ordered item name").contains("A03 Optic Scope");

    // Then: the sub-assembly provenance marker tags the adopted line.
    assertThat(html).as("sub-assembly provenance tag").contains("subassembly-tag");

    // Then: the aggregated-materials panel shows one Gut and one Keine row.
    assertThat(html).as("aggregated material name (GOOD)").contains("AcryliPlex Composite");
    assertThat(html).as("aggregated material name (NONE)").contains("Agricium");
    assertThat(html).as("GOOD quality badge").contains("quality-good");
    assertThat(html).as("NONE quality badge").contains("quality-none");

    // Then: the no-materials banner appears and names the unresolved sub-assembly line.
    int bannerIndex = html.indexOf("alert-warning");
    assertThat(bannerIndex).as("no-materials warning banner").isGreaterThan(0);
    assertThat(html.indexOf("A03 Optic Scope", bannerIndex))
        .as("unresolved item is listed inside the banner")
        .isGreaterThan(bannerIndex);

    // Then: no MATERIAL-only requirement row renders for an item order. The clickable drill-down
    // rows carry data-material-id; the always-present JS handler references od-toggle-inventory, so
    // assert on the row attribute the MATERIAL branch would have emitted instead.
    assertThat(html)
        .as("material requirement rows are gated out")
        .doesNotContain("data-material-id=");
  }

  @Test
  void itemOrderDetail_RendersHandoverModalAndHistory() throws Exception {
    // Given: an item order with one outstanding line (3 ordered, 1 delivered -> 2 outstanding) and
    // one already-recorded item handover. The handover button + modal must render (outstanding > 0)
    // and the history row must offer a PDF delivery note.
    UUID orderId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID lineId = UUID.randomUUID();
    UUID handoverId = UUID.randomUUID();

    JobOrderItemDto line =
        new JobOrderItemDto(
            lineId,
            new GameItemReferenceDto(UUID.randomUUID(), "A03 Sniper Rifle", "WEAPON"),
            new BlueprintReferenceDto(UUID.randomUUID(), "A03 Sniper Rifle", "wiki-a03"),
            3,
            1,
            null,
            List.of(
                new JobOrderItemMaterialDto(
                    UUID.randomUUID(), material("Agricium", "SCU"), 12.0, "NONE", 1L)),
            1L);
    JobOrderItemHandoverDto handover =
        new JobOrderItemHandoverDto(
            handoverId,
            orderId,
            Instant.now(),
            "Recipient",
            null,
            null,
            List.of(
                new JobOrderItemHandoverEntryDto(
                    UUID.randomUUID(),
                    lineId,
                    new GameItemReferenceDto(UUID.randomUUID(), "A03 Sniper Rifle", "WEAPON"),
                    1)),
            1L);

    JobOrderDto order =
        new JobOrderDto(
            orderId,
            8,
            null,
            null,
            "Handle",
            null,
            1,
            "IN_PROGRESS",
            "ITEM",
            List.of(),
            List.of(line),
            List.of(
                new AggregatedMaterialDto(
                    material("Agricium", "SCU"), "NONE", 12.0, List.of(), null)),
            List.of(),
            List.of(),
            List.of(handover),
            Instant.now(),
            1L);

    when(backendApiClient.get(eq("/api/v1/orders/" + orderId), eq(JobOrderDto.class)))
        .thenReturn(order);

    // When
    MvcResult result =
        mockMvc
            .perform(get("/orders/" + orderId).with(authentication(logisticianToken(userId))))
            .andExpect(status().isOk())
            .andReturn();

    String html = result.getResponse().getContentAsString();

    // Then: the handover button and modal render (an outstanding line exists), targeting the
    // item-handover POST and exposing one bind-able line row.
    assertThat(html).as("item-handover open button").contains("data-testid=\"item-handover-open\"");
    assertThat(html).as("item-handover modal").contains("id=\"item-handover-modal\"");
    assertThat(html).as("modal posts to the item-handover endpoint").contains("/item-handovers");
    assertThat(html)
        .as("line amount input bound by request-param name")
        .contains("entries[0].amount");
    assertThat(html)
        .as("line id hidden input bound by request-param name")
        .contains("entries[0].jobOrderItemId");

    // Then: the history table shows the recorded handover with a PDF download trigger.
    assertThat(html).as("item-handover history row").contains("data-testid=\"item-handover-row\"");
    assertThat(html).as("PDF download trigger").contains("od-download-item-report");
    assertThat(html).as("recipient handle in history").contains("Recipient");
  }

  @Test
  void materialOrder_skResponsible_RendersClaimColumns() throws Exception {
    // Given: a public SK MATERIAL order (openAmount populated) with one squadron claim of 6 against
    // a required 10 → 4 open. The backend signals SK-ness by populating openAmount.
    UUID orderId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    ClaimDto claim =
        new ClaimDto(
            UUID.randomUUID(),
            new SquadronReferenceDto(UUID.randomUUID(), "Alpha Flight", "ALF"),
            6.0,
            null,
            Instant.now(),
            1L);
    JobOrderMaterialDto mat =
        new JobOrderMaterialDto(
            UUID.randomUUID(),
            material("Agricium", "SCU"),
            null,
            10.0,
            0.0,
            List.of(claim),
            4.0,
            1L);
    JobOrderDto order =
        new JobOrderDto(
            orderId,
            9,
            null,
            null,
            "Handle",
            null,
            1,
            "OPEN",
            "MATERIAL",
            List.of(mat),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Instant.now(),
            1L);
    when(backendApiClient.get(eq("/api/v1/orders/" + orderId), eq(JobOrderDto.class)))
        .thenReturn(order);

    String html =
        mockMvc
            .perform(
                get("/orders/" + orderId)
                    .header("Accept-Language", "de")
                    .with(authentication(logisticianToken(userId))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(html).as("claims column header (de)").contains("Eingetragen");
    assertThat(html).as("open column header (de)").contains("Offen");
    assertThat(html).as("claim chip rendered").contains("claim-chip");
    assertThat(html).as("claiming squadron shorthand").contains("ALF");
  }

  @Test
  void materialOrder_privateSquadron_HidesClaimColumns() throws Exception {
    // Given: a private squadron MATERIAL order — the backend leaves openAmount null and claims
    // empty, so the detail page renders no claim columns.
    UUID orderId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    JobOrderMaterialDto mat =
        new JobOrderMaterialDto(
            UUID.randomUUID(), material("Agricium", "SCU"), null, 10.0, 0.0, List.of(), null, 1L);
    JobOrderDto order =
        new JobOrderDto(
            orderId,
            10,
            null,
            null,
            "Handle",
            null,
            1,
            "OPEN",
            "MATERIAL",
            List.of(mat),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Instant.now(),
            1L);
    when(backendApiClient.get(eq("/api/v1/orders/" + orderId), eq(JobOrderDto.class)))
        .thenReturn(order);

    String html =
        mockMvc
            .perform(
                get("/orders/" + orderId)
                    .header("Accept-Language", "de")
                    .with(authentication(logisticianToken(userId))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(html).as("no claims column on a private order").doesNotContain("Eingetragen");
    assertThat(html).as("no claim chips on a private order").doesNotContain("claim-chip");
  }
}

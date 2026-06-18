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

package de.greluc.krt.profit.basetool.frontend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import de.greluc.krt.profit.basetool.frontend.model.dto.OrgUnitMembershipOptionDto;
import de.greluc.krt.profit.basetool.frontend.service.BackendApiClient;
import java.util.Collections;
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
 * responsible picker" bug. Both owner-pickers are now fed by the single {@code permitAll} {@code
 * GET /api/v1/org-units/active} catalog (each option carries its {@code isProfitEligible} flag), so
 * a profit-eligible SK reaches the responsible picker for authenticated callers <em>and</em>
 * anonymous guests alike. The previous design fetched the authenticated-only SK catalog ({@code
 * /api/v1/special-commands}) separately, which 401s for a guest and dropped every SK. This test
 * pins that an eligible SK reaches the rendered picker, that the catalog is fetched through the
 * public client, and that the deprecated SK-catalog call is no longer made for the picker.
 */
@SpringBootTest
@SuppressWarnings("unchecked")
class JobOrderPageControllerResponsiblePickerMvcTest {

  private static final String ACTIVE_URI = "/api/v1/org-units/active";
  private static final String SK_CATALOG_URI = "/api/v1/special-commands?size=1000&sort=name,asc";

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
    OrgUnitMembershipOptionDto profitStaffel =
        new OrgUnitMembershipOptionDto(UUID.randomUUID(), "Test Staffel", "TS", "SQUADRON", true);
    OrgUnitMembershipOptionDto profitSk =
        new OrgUnitMembershipOptionDto(
            UUID.randomUUID(), "Profit Spezialkommando", "PSK", "SPECIAL_COMMAND", true);

    // Reference catalogs (materials / orderable items / squadrons) go through the cached client;
    // empty keeps them from blocking the render.
    when(backendApiClient.getCached(
            anyString(), any(ParameterizedTypeReference.class), anyBoolean()))
        .thenReturn(Collections.emptyList());
    when(backendApiClient.get(eq(ACTIVE_URI), any(ParameterizedTypeReference.class), eq(true)))
        .thenReturn(List.of(profitStaffel, profitSk));

    mockMvc
        .perform(get("/orders/create"))
        .andExpect(status().isOk())
        .andExpect(view().name("orders-create"))
        .andExpect(content().string(Matchers.containsString("Profit Spezialkommando")));

    // The catalog must come from the permitAll active endpoint via the public client — the previous
    // design fetched the authenticated SK catalog, which 401s and drops every SK for a guest.
    verify(backendApiClient).get(eq(ACTIVE_URI), any(ParameterizedTypeReference.class), eq(true));
    verify(backendApiClient, never())
        .getCached(eq(SK_CATALOG_URI), any(ParameterizedTypeReference.class), anyBoolean());
  }
}

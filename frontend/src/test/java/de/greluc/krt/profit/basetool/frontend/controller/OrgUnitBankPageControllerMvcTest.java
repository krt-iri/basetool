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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import de.greluc.krt.profit.basetool.frontend.model.dto.BankBookingRequestDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.OrgUnitBankBalanceDto;
import de.greluc.krt.profit.basetool.frontend.service.BackendApiClient;
import java.math.BigDecimal;
import java.time.Instant;
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
 * Renders the org-unit officer/lead bank page (epic #666 F1/F2) to pin that the balance card, the
 * request form modal and the own-request list with a cancel action all render without a Thymeleaf
 * error, that the page is gated to leadership roles (not {@code BANK_EMPLOYEE}), and that the
 * {@code orgUnitBank} fragment view resolves for the in-place swap.
 */
@SpringBootTest
@SuppressWarnings("unchecked")
class OrgUnitBankPageControllerMvcTest {

  private static final String BALANCES_URI = "/api/v1/org-units/bank/balances";
  private static final String REQUESTS_URI = "/api/v1/org-units/bank/requests";

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

  private void stubData(UUID orgUnitId) {
    OrgUnitBankBalanceDto balance =
        new OrgUnitBankBalanceDto(
            UUID.randomUUID(),
            "KB-0001",
            "Staffel IRIDIUM",
            "ACTIVE",
            orgUnitId,
            "IRIDIUM",
            "IRI",
            "SQUADRON",
            new BigDecimal("1850000"));
    BankBookingRequestDto request =
        new BankBookingRequestDto(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "KB-0001",
            orgUnitId,
            "IRIDIUM",
            "IRI",
            "DEPOSIT",
            new BigDecimal("5000"),
            "from sale",
            "PENDING",
            "officerX",
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.parse("2026-06-17T14:02:00Z"),
            0L);
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(null);
    when(backendApiClient.get(eq(BALANCES_URI), any(ParameterizedTypeReference.class)))
        .thenReturn(List.of(balance));
    when(backendApiClient.get(eq(REQUESTS_URI), any(ParameterizedTypeReference.class)))
        .thenReturn(List.of(request));
  }

  @Test
  @WithMockUser(roles = {"OFFICER"})
  void orgUnitBank_rendersBalanceCardRequestModalAndOwnRequests() throws Exception {
    UUID orgUnitId = UUID.randomUUID();
    stubData(orgUnitId);

    mockMvc
        .perform(get("/org-unit-bank"))
        .andExpect(status().isOk())
        .andExpect(view().name("org-unit-bank"))
        .andExpect(content().string(Matchers.containsString("Staffel IRIDIUM")))
        // The request modal exists and the card primes it with the org unit id.
        .andExpect(content().string(Matchers.containsString("org-unit-request-modal")))
        .andExpect(
            content().string(Matchers.containsString("data-field-orgunitid=\"" + orgUnitId + "\"")))
        // The own-request row renders with a cancel form.
        .andExpect(content().string(Matchers.containsString("org-unit-bank-cancel-btn")));
  }

  @Test
  @WithMockUser(roles = {"OFFICER"})
  void orgUnitBank_fragmentViewResolves() throws Exception {
    stubData(UUID.randomUUID());

    mockMvc
        .perform(get("/org-unit-bank").param("fragment", "orgUnitBank"))
        .andExpect(status().isOk())
        .andExpect(view().name("org-unit-bank :: orgUnitBank"));
  }

  @Test
  @WithMockUser(roles = {"SQUADRON_MEMBER"})
  void orgUnitBank_plainMemberIsForbidden() throws Exception {
    mockMvc.perform(get("/org-unit-bank")).andExpect(status().isForbidden());
  }
}

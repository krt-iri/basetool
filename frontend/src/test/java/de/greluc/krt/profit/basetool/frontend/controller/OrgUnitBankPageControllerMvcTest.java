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

import de.greluc.krt.profit.basetool.frontend.model.dto.BankAccountDetailDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.BankAccountDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.BankBookingDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.BankBookingRequestDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.BankCapabilitiesDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.OrgUnitBankAccountDetailDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.OrgUnitBankAccountSettingsDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.OrgUnitBankBalanceDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.OrgUnitBankViewUserDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.profit.basetool.frontend.model.dto.UserReferenceDto;
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
            "ORG_UNIT",
            orgUnitId,
            "IRIDIUM",
            "IRI",
            "SQUADRON",
            new BigDecimal("1850000"),
            true,
            new BigDecimal("420000"),
            List.of(new BigDecimal("1430000"), new BigDecimal("1850000")),
            new BigDecimal("2000000"),
            true,
            null);
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
            null,
            null,
            false,
            null,
            false,
            null,
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
        // The 30-day trend renders: the sign-colored delta label + the inline SVG sparkline,
        // mirroring the bank dashboard cards (REQ-BANK-016).
        .andExpect(content().string(Matchers.containsString("kpi-delta")))
        .andExpect(content().string(Matchers.containsString("kpi-sparkline")))
        // The single page-level request CTA shows (an account is requestable) and opens the modal.
        .andExpect(content().string(Matchers.containsString("org-unit-bank-request-btn")))
        .andExpect(content().string(Matchers.containsString("org-unit-request-modal")))
        // The modal's account selector lists the requestable account as an option, keyed by the
        // account id now that eligibility is view-based (REQ-BANK-039) — and offers the TRANSFER
        // op.
        .andExpect(content().string(Matchers.containsString("name=\"sourceAccountId\"")))
        .andExpect(content().string(Matchers.containsString("org-unit-request-account")))
        .andExpect(content().string(Matchers.containsString("name=\"type\"")))
        // REQ-BANK-040: the transfer destination select MUST be named targetAccountId to match the
        // CreateBankBookingRequest DTO field — a mismatch silently 400s every transfer request.
        .andExpect(content().string(Matchers.containsString("name=\"targetAccountId\"")))
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
  @WithMockUser(roles = {"KRT_MEMBER"})
  void orgUnitBank_memberIsPermitted() throws Exception {
    // REQ-BANK-037: the page is reachable by any KRT member (the cartel account is visible to all,
    // and a member may have been granted access to other accounts); the backend seam scopes the
    // visible accounts. The member sees an empty page here because no data is stubbed.
    mockMvc.perform(get("/org-unit-bank")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {"GUEST"})
  void orgUnitBank_guestIsForbidden() throws Exception {
    mockMvc.perform(get("/org-unit-bank")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = {"LOGISTICIAN"})
  void orgUnitBank_specialAccountRendersRowWithoutRequestButton() throws Exception {
    // REQ-BANK-028: a special account (Sonderkonto) carries no org-unit identity and is not
    // requestable. A page of only such accounts renders the row but offers NO page-level request
    // CTA at all (it is gated on at least one requestable account). Pins that the template handles
    // the null org unit.
    OrgUnitBankBalanceDto special =
        new OrgUnitBankBalanceDto(
            UUID.randomUUID(),
            "KB-0042",
            "Event Sonderkonto",
            "ACTIVE",
            "SPECIAL",
            null,
            null,
            null,
            null,
            new BigDecimal("250000"),
            false,
            BigDecimal.ZERO,
            List.of(),
            null,
            false,
            null);
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(null);
    when(backendApiClient.get(eq(BALANCES_URI), any(ParameterizedTypeReference.class)))
        .thenReturn(List.of(special));
    when(backendApiClient.get(eq(REQUESTS_URI), any(ParameterizedTypeReference.class)))
        .thenReturn(List.of());

    mockMvc
        .perform(get("/org-unit-bank"))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("Event Sonderkonto")))
        // The account row renders (its testid is unchanged from the former card).
        .andExpect(content().string(Matchers.containsString("org-unit-bank-card")))
        // No requestable account -> neither the page-level CTA nor the request modal is rendered.
        .andExpect(
            content().string(Matchers.not(Matchers.containsString("org-unit-bank-request-btn"))))
        .andExpect(
            content().string(Matchers.not(Matchers.containsString("org-unit-request-modal"))));
  }

  /**
   * Stubs the read-only account drill-in (REQ-BANK-038) plus the holder/OL settings region: an
   * ORG_UNIT account with a balance target, a single booking, one granted role bucket and one
   * granted user, and a user-lookup for the "grant a user" picker.
   *
   * @param accountId the account id used in every backend URI
   */
  private void stubDetail(UUID accountId) {
    BankAccountDto account =
        new BankAccountDto(
            accountId,
            "KB-0001",
            "Staffel IRIDIUM",
            "ORG_UNIT",
            "ACTIVE",
            null,
            null,
            new BigDecimal("1850000"),
            new BigDecimal("2000000"),
            3L,
            Instant.parse("2026-01-01T00:00:00Z"));
    BankAccountDetailDto inner =
        new BankAccountDetailDto(
            account,
            new BigDecimal("420000"),
            128L,
            new BankCapabilitiesDto(false, false, false, false),
            new de.greluc.krt.profit.basetool.frontend.model.dto.BankApprovalLimitsDto(
                false,
                false,
                false,
                java.util.List.of(),
                java.util.Map.of(),
                null,
                java.util.List.of()));
    OrgUnitBankAccountDetailDto detail =
        new OrgUnitBankAccountDetailDto(inner, true, true, true, true, true, null);
    OrgUnitBankAccountSettingsDto settings =
        new OrgUnitBankAccountSettingsDto(
            accountId,
            "KB-0001",
            "Staffel IRIDIUM",
            "ORG_UNIT",
            "SQUADRON",
            new BigDecimal("2000000"),
            3L,
            true,
            true,
            true,
            true,
            false,
            List.of("LOGISTICIAN", "MISSION_MANAGER"),
            List.of("LOGISTICIAN"),
            false,
            List.of(new OrgUnitBankViewUserDto(UUID.randomUUID(), "greluc")),
            true,
            new de.greluc.krt.profit.basetool.frontend.model.dto.BankApprovalLimitsDto(
                false,
                false,
                false,
                java.util.List.of(),
                java.util.Map.of(),
                null,
                java.util.List.of()));
    BankBookingDto booking =
        new BankBookingDto(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "DEPOSIT",
            new BigDecimal("250000"),
            "someHolder",
            "Missionsertrag",
            Instant.parse("2026-06-10T18:30:00Z"),
            null,
            null,
            null,
            null,
            false,
            BigDecimal.ZERO);
    PageResponse<BankBookingDto> bookings =
        new PageResponse<>(List.of(booking), 0, 20, 1L, 1, List.of());
    UserReferenceDto user =
        new UserReferenceDto(UUID.randomUUID(), "cmdr.valk", "cmdr.valk", "cmdr.valk", 1);

    String detailUri = "/api/v1/org-units/bank/accounts/" + accountId;
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(null);
    when(backendApiClient.get(eq(detailUri), eq(OrgUnitBankAccountDetailDto.class)))
        .thenReturn(detail);
    when(backendApiClient.get(eq(detailUri + "/settings"), eq(OrgUnitBankAccountSettingsDto.class)))
        .thenReturn(settings);
    when(backendApiClient.get(
            eq(detailUri + "/transactions?page=0"), any(ParameterizedTypeReference.class)))
        .thenReturn(bookings);
    when(backendApiClient.get(eq("/api/v1/users/lookup"), any(ParameterizedTypeReference.class)))
        .thenReturn(List.of(user));
  }

  @Test
  @WithMockUser(roles = {"OFFICER"})
  void orgUnitBankAccount_rendersDetailWithSettingsAndVisibilityToggles() throws Exception {
    UUID accountId = UUID.randomUUID();
    stubDetail(accountId);

    mockMvc
        .perform(get("/org-unit-bank/accounts/" + accountId))
        .andExpect(status().isOk())
        .andExpect(view().name("org-unit-bank-account-detail"))
        .andExpect(content().string(Matchers.containsString("Staffel IRIDIUM")))
        // Facts render as the kpi-total grid, target fact included.
        .andExpect(content().string(Matchers.containsString("ou-facts")))
        .andExpect(content().string(Matchers.containsString("org-unit-bank-detail-target")))
        // Settings region with the quiet per-audience visibility toggles.
        .andExpect(content().string(Matchers.containsString("org-unit-bank-settings")))
        .andExpect(content().string(Matchers.containsString("vis-row")))
        .andExpect(content().string(Matchers.containsString("org-unit-vis-role-LOGISTICIAN")))
        // History panel kept (4-column, Halter-redacted).
        .andExpect(content().string(Matchers.containsString("org-unit-bank-bookings-panel")))
        // The always-"Aktiv" status pill was dropped from the header.
        .andExpect(content().string(Matchers.not(Matchers.containsString("status-pill"))));
  }

  @Test
  @WithMockUser(roles = {"OFFICER"})
  void orgUnitBankAccount_settingsFragmentViewResolves() throws Exception {
    UUID accountId = UUID.randomUUID();
    stubDetail(accountId);

    mockMvc
        .perform(
            get("/org-unit-bank/accounts/" + accountId).param("fragment", "orgUnitBankSettings"))
        .andExpect(status().isOk())
        .andExpect(view().name("org-unit-bank-account-detail :: orgUnitBankSettings"));
  }
}

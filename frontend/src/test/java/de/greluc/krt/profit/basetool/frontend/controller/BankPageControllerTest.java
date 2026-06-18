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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.profit.basetool.frontend.model.dto.BankAccountDetailDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.BankAccountDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.BankBookingDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.BankCapabilitiesDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.BankDashboardAccountDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.BankDashboardDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.BankDashboardTotalsDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.BankHolderBalanceDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.BankHolderDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.profit.basetool.frontend.service.BackendApiClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

@SuppressWarnings("unchecked")
class BankPageControllerTest {

  private static BankDashboardAccountDto dashboardAccount(List<BigDecimal> sparkline) {
    return new BankDashboardAccountDto(
        UUID.randomUUID(),
        "KB-0001",
        "Staffel IRIDIUM",
        "ORG_UNIT",
        "ACTIVE",
        new BigDecimal("1850000"),
        new BigDecimal("420000"),
        sparkline);
  }

  @Test
  void dashboard_ShouldScaleSparklineIntoPolylinePoints() {
    // Given
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    BankPageController controller = new BankPageController(backendApiClient);
    Model model = new ConcurrentModel();
    BankDashboardDto dashboard =
        new BankDashboardDto(
            true,
            List.of(dashboardAccount(List.of(BigDecimal.ZERO, BigDecimal.TEN))),
            new BankDashboardTotalsDto(
                new BigDecimal("1850000"), new BigDecimal("500"), new BigDecimal("-100"), 1, 0));
    when(backendApiClient.get(eq("/api/v1/bank/dashboard"), eq(BankDashboardDto.class)))
        .thenReturn(dashboard);

    // When
    String view = controller.dashboard(model);

    // Then
    assertEquals("bank-dashboard", view);
    List<BankPageController.BankDashboardCardView> cards =
        (List<BankPageController.BankDashboardCardView>) model.getAttribute("cards");
    assertNotNull(cards);
    assertEquals(1, cards.size());
    // Rising series: first point at the padded bottom (y=24), last at the padded top (y=2).
    assertEquals("0.0,24.0 96.0,2.0", cards.get(0).sparklinePoints());
    assertFalse(cards.get(0).flat());
  }

  @Test
  void dashboard_ShouldRenderFlatSeriesAsMidLine() {
    // Given
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    BankPageController controller = new BankPageController(backendApiClient);
    Model model = new ConcurrentModel();
    BigDecimal level = new BigDecimal("500");
    BankDashboardDto dashboard =
        new BankDashboardDto(false, List.of(dashboardAccount(List.of(level, level))), null);
    when(backendApiClient.get(eq("/api/v1/bank/dashboard"), eq(BankDashboardDto.class)))
        .thenReturn(dashboard);

    // When
    controller.dashboard(model);

    // Then
    List<BankPageController.BankDashboardCardView> cards =
        (List<BankPageController.BankDashboardCardView>) model.getAttribute("cards");
    assertNotNull(cards);
    assertTrue(cards.get(0).flat());
    assertEquals("0.0,13.0 96.0,13.0", cards.get(0).sparklinePoints());
  }

  @Test
  void dashboard_ShouldHandleEmptySparklineAndNullDashboard() {
    // Given
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    BankPageController controller = new BankPageController(backendApiClient);
    Model model = new ConcurrentModel();
    when(backendApiClient.get(eq("/api/v1/bank/dashboard"), eq(BankDashboardDto.class)))
        .thenReturn(new BankDashboardDto(false, List.of(dashboardAccount(List.of())), null));

    // When
    controller.dashboard(model);

    // Then
    List<BankPageController.BankDashboardCardView> cards =
        (List<BankPageController.BankDashboardCardView>) model.getAttribute("cards");
    assertNotNull(cards);
    assertNull(cards.get(0).sparklinePoints());
    assertTrue(cards.get(0).flat());
  }

  @Test
  void accountDetail_ShouldFilterTransferTargetsAndComputePercents() {
    // Given
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    BankPageController controller = new BankPageController(backendApiClient);
    Model model = new ConcurrentModel();
    UUID accountId = UUID.randomUUID();
    UUID holderA = UUID.randomUUID();
    UUID holderB = UUID.randomUUID();

    BankAccountDto self = account(accountId, "KB-0001", "ACTIVE", "1000");
    BankAccountDto activeOther = account(UUID.randomUUID(), "KB-0002", "ACTIVE", "0");
    BankAccountDto closedOther = account(UUID.randomUUID(), "KB-0003", "CLOSED", "0");
    BankAccountDetailDto detail =
        new BankAccountDetailDto(
            self,
            new BigDecimal("100"),
            5,
            2,
            List.of(
                new BankHolderBalanceDto(holderA, "alpha", true, new BigDecimal("600")),
                new BankHolderBalanceDto(holderB, "bravo", true, new BigDecimal("400"))),
            new BankCapabilitiesDto(true, true, true, false));

    when(backendApiClient.get(
            eq("/api/v1/bank/accounts/" + accountId), eq(BankAccountDetailDto.class)))
        .thenReturn(detail);
    when(backendApiClient.get(contains("/transactions"), any(ParameterizedTypeReference.class)))
        .thenReturn(
            new PageResponse<BankBookingDto>(List.of(), 0, 20, 0, 0, Collections.emptyList()));
    when(backendApiClient.get(eq("/api/v1/bank/holders"), any(ParameterizedTypeReference.class)))
        .thenReturn(
            List.of(
                new BankHolderDto(
                    holderA, UUID.randomUUID(), "alpha", true, BigDecimal.ZERO, 1, 0L),
                new BankHolderDto(
                    holderB, UUID.randomUUID(), "bravo", false, BigDecimal.ZERO, 1, 0L)));
    when(backendApiClient.get(
            eq("/api/v1/bank/accounts?size=500"), any(ParameterizedTypeReference.class)))
        .thenReturn(
            new PageResponse<>(
                List.of(self, activeOther, closedOther), 0, 500, 3, 1, Collections.emptyList()));

    // When
    String view = controller.accountDetail(accountId, null, null, model);

    // Then
    assertEquals("bank-account-detail", view);
    List<BankAccountDto> targets = (List<BankAccountDto>) model.getAttribute("transferTargets");
    assertNotNull(targets);
    assertEquals(1, targets.size());
    assertEquals("KB-0002", targets.get(0).accountNo());
    List<BankHolderDto> activeHolders = (List<BankHolderDto>) model.getAttribute("activeHolders");
    assertNotNull(activeHolders);
    assertEquals(1, activeHolders.size());
    assertEquals("alpha", activeHolders.get(0).handle());
    Map<UUID, Integer> percents = (Map<UUID, Integer>) model.getAttribute("distributionPercents");
    assertNotNull(percents);
    assertEquals(60, percents.get(holderA));
    assertEquals(40, percents.get(holderB));
    assertEquals("/bank/accounts/" + accountId, model.getAttribute("paginationBaseUrl"));
  }

  @Test
  void accountDetail_ShouldSkipPercentsOnZeroBalance() {
    // Given
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    BankPageController controller = new BankPageController(backendApiClient);
    Model model = new ConcurrentModel();
    UUID accountId = UUID.randomUUID();
    BankAccountDetailDto detail =
        new BankAccountDetailDto(
            account(accountId, "KB-0001", "ACTIVE", "0"),
            BigDecimal.ZERO,
            0,
            0,
            List.of(),
            new BankCapabilitiesDto(false, false, false, false));
    when(backendApiClient.get(
            eq("/api/v1/bank/accounts/" + accountId), eq(BankAccountDetailDto.class)))
        .thenReturn(detail);
    when(backendApiClient.get(any(String.class), any(ParameterizedTypeReference.class)))
        .thenReturn(null);

    // When
    controller.accountDetail(accountId, -3, null, model);

    // Then
    Map<UUID, Integer> percents = (Map<UUID, Integer>) model.getAttribute("distributionPercents");
    assertNotNull(percents);
    assertTrue(percents.isEmpty());
    assertEquals(List.of(), model.getAttribute("transferTargets"));
    assertEquals(List.of(), model.getAttribute("activeHolders"));
  }

  // covers REQ-FE-002 — an AJAX swap request (fragment=bookings) renders only the booking-history
  // fragment and fetches ONLY the transactions page: the account-detail, holders and accounts
  // round-trips the full page does are skipped, and the model carries just bookings +
  // paginationBaseUrl.
  @Test
  void accountDetail_fragmentBookings_rendersOnlyBookingsFragment_andSkipsOtherFetches() {
    // Given
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    BankPageController controller = new BankPageController(backendApiClient);
    Model model = new ConcurrentModel();
    UUID accountId = UUID.randomUUID();
    PageResponse<BankBookingDto> bookings =
        new PageResponse<>(List.of(), 0, 20, 0, 0, Collections.emptyList());
    when(backendApiClient.get(contains("/transactions"), any(ParameterizedTypeReference.class)))
        .thenReturn(bookings);

    // When
    String view = controller.accountDetail(accountId, 2, "bookings", model);

    // Then
    assertEquals("bank-account-detail :: bookings", view);
    assertEquals(bookings, model.getAttribute("bookings"));
    assertEquals("/bank/accounts/" + accountId, model.getAttribute("paginationBaseUrl"));
    // The fragment path must not load the account detail, holder registry or accounts list.
    verify(backendApiClient, never())
        .get(eq("/api/v1/bank/accounts/" + accountId), eq(BankAccountDetailDto.class));
    verify(backendApiClient, never())
        .get(eq("/api/v1/bank/holders"), any(ParameterizedTypeReference.class));
  }

  // covers REQ-FE-005 (#579) — an in-place money-write re-render (fragment=accountBody) returns the
  // whole account body fragment with the SAME full model the page builds (detail, holders,
  // transferTargets, distributionPercents, bookings) so balance, distribution AND the modals'
  // distribution-derived holder selects all refresh from one swap.
  @Test
  void accountDetail_fragmentAccountBody_rendersBodyFragment_withFullModel() {
    // Given
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    BankPageController controller = new BankPageController(backendApiClient);
    Model model = new ConcurrentModel();
    UUID accountId = UUID.randomUUID();
    UUID holderA = UUID.randomUUID();
    BankAccountDto self = account(accountId, "KB-0001", "ACTIVE", "1000");
    BankAccountDetailDto detail =
        new BankAccountDetailDto(
            self,
            new BigDecimal("10"),
            1,
            1,
            List.of(new BankHolderBalanceDto(holderA, "alpha", true, new BigDecimal("1000"))),
            new BankCapabilitiesDto(true, true, true, false));
    when(backendApiClient.get(
            eq("/api/v1/bank/accounts/" + accountId), eq(BankAccountDetailDto.class)))
        .thenReturn(detail);
    when(backendApiClient.get(contains("/transactions"), any(ParameterizedTypeReference.class)))
        .thenReturn(
            new PageResponse<BankBookingDto>(List.of(), 0, 20, 0, 0, Collections.emptyList()));
    when(backendApiClient.get(eq("/api/v1/bank/holders"), any(ParameterizedTypeReference.class)))
        .thenReturn(
            List.of(
                new BankHolderDto(
                    holderA, UUID.randomUUID(), "alpha", true, BigDecimal.ZERO, 1, 0L)));
    when(backendApiClient.get(
            eq("/api/v1/bank/accounts?size=500"), any(ParameterizedTypeReference.class)))
        .thenReturn(new PageResponse<>(List.of(self), 0, 500, 1, 1, Collections.emptyList()));

    // When
    String view = controller.accountDetail(accountId, null, "accountBody", model);

    // Then
    assertEquals("bank-account-detail :: accountBody", view);
    // The accountBody fragment needs the FULL model (unlike the bookings-only fragment): detail,
    // the
    // distribution-derived holder selects, transfer targets and percents must all be present.
    assertNotNull(model.getAttribute("detail"));
    assertNotNull(model.getAttribute("activeHolders"));
    assertNotNull(model.getAttribute("transferTargets"));
    assertNotNull(model.getAttribute("distributionPercents"));
    assertNotNull(model.getAttribute("bookings"));
  }

  private static BankAccountDto account(UUID id, String no, String status, String balance) {
    return new BankAccountDto(
        id,
        no,
        "Konto " + no,
        "ORG_UNIT",
        status,
        null,
        null,
        new BigDecimal(balance),
        0L,
        Instant.parse("2026-01-15T10:00:00Z"));
  }
}

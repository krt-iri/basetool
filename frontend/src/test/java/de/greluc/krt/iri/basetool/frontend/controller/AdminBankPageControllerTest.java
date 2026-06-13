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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.frontend.model.dto.BankAuditEventDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.BankWipeResetResultDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@SuppressWarnings("unchecked")
class AdminBankPageControllerTest {

  private BackendApiClient backendApiClient;
  private AdminBankPageController controller;

  @BeforeEach
  void setUp() {
    backendApiClient = mock(BackendApiClient.class);
    controller = new AdminBankPageController(backendApiClient);
  }

  @Test
  void wipeReset_withoutConfirmToken_skipsBackendAndFlagsError() {
    // Given
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();

    // When
    String view = controller.wipeReset("nope", attrs);

    // Then
    assertEquals("redirect:/admin/bank", view);
    assertEquals("admin.bank.wipe.error.confirm", attrs.getFlashAttributes().get("error"));
    verify(backendApiClient, never()).post(any(), any(), any());
  }

  @Test
  void wipeReset_withCounts_flashesResult() {
    // Given
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
    when(backendApiClient.post(
            eq("/api/v1/bank/admin/wipe-reset"), any(), eq(BankWipeResetResultDto.class)))
        .thenReturn(new BankWipeResetResultDto(5, 12, new BigDecimal("1250")));

    // When
    String view = controller.wipeReset("WIPE", attrs);

    // Then
    assertEquals("redirect:/admin/bank", view);
    BankWipeResetResultDto result =
        (BankWipeResetResultDto) attrs.getFlashAttributes().get("wipeResult");
    assertNotNull(result);
    assertEquals(5, result.accountsReset());
  }

  @Test
  void wipeReset_zeroCounts_flashesNoop() {
    // Given
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
    when(backendApiClient.post(
            eq("/api/v1/bank/admin/wipe-reset"), any(), eq(BankWipeResetResultDto.class)))
        .thenReturn(new BankWipeResetResultDto(0, 0, BigDecimal.ZERO));

    // When
    controller.wipeReset("WIPE", attrs);

    // Then
    assertEquals(Boolean.TRUE, attrs.getFlashAttributes().get("wipeNoop"));
  }

  @Test
  void wipeReset_backendFailure_flashesError() {
    // Given
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
    when(backendApiClient.post(
            eq("/api/v1/bank/admin/wipe-reset"), any(), eq(BankWipeResetResultDto.class)))
        .thenThrow(new RuntimeException("boom"));

    // When
    controller.wipeReset("WIPE", attrs);

    // Then
    assertEquals("admin.bank.wipe.error.failed", attrs.getFlashAttributes().get("error"));
  }

  @Test
  void bankAudit_buildsFilteredUriAndExposesEventTypes() {
    // Given
    Model model = new ConcurrentModel();
    PageResponse<BankAuditEventDto> empty =
        new PageResponse<>(List.of(), 0, 50, 0, 0, Collections.emptyList());
    when(backendApiClient.get(any(String.class), any(ParameterizedTypeReference.class)))
        .thenReturn(empty);

    // When
    String view =
        controller.bankAudit(
            "2026-01-01T00:00:00Z",
            "2026-02-01T00:00:00Z",
            "acc-1",
            "DEPOSIT_BOOKED",
            0,
            null,
            model);

    // Then
    assertEquals("admin/bank-audit", view);
    List<String> eventTypes = (List<String>) model.getAttribute("eventTypes");
    assertNotNull(eventTypes);
    assertTrue(eventTypes.contains("WIPE_RESET_EXECUTED"));
    assertEquals("DEPOSIT_BOOKED", model.getAttribute("filterEventType"));
    String base = (String) model.getAttribute("paginationBaseUrl");
    assertTrue(base.contains("eventType=DEPOSIT_BOOKED"));
    assertTrue(base.contains("accountId=acc-1"));
  }

  @Test
  void bankAudit_backendFailure_setsErrorAttribute() {
    // Given
    Model model = new ConcurrentModel();
    when(backendApiClient.get(any(String.class), any(ParameterizedTypeReference.class)))
        .thenThrow(new RuntimeException("down"));

    // When
    controller.bankAudit(null, null, null, null, 0, null, model);

    // Then
    assertEquals("admin.bank.audit.error.load", model.getAttribute("error"));
  }

  @Test
  void bankAudit_unfilteredBaseUrlHasNoQuery() {
    // Given
    Model model = new ConcurrentModel();
    when(backendApiClient.get(any(String.class), any(ParameterizedTypeReference.class)))
        .thenReturn(new PageResponse<BankAuditEventDto>(List.of(), 0, 50, 0, 0, List.of()));

    // When
    controller.bankAudit(null, null, null, null, 0, null, model);

    // Then
    assertEquals("/admin/bank-audit", model.getAttribute("paginationBaseUrl"));
  }

  @Test
  void bankAudit_fragmentResults_returnsResultsFragmentSelector() {
    // Given — an AJAX swap request (fragment=results) for in-place filter/paging (#573).
    Model model = new ConcurrentModel();
    when(backendApiClient.get(any(String.class), any(ParameterizedTypeReference.class)))
        .thenReturn(new PageResponse<BankAuditEventDto>(List.of(), 0, 50, 0, 0, List.of()));

    // When
    String view = controller.bankAudit(null, null, null, null, 0, "results", model);

    // Then — only the results fragment is rendered, not the full page.
    assertEquals("admin/bank-audit :: auditResults", view);
  }

  @Test
  void bankAdmin_returnsView() {
    assertEquals("admin/bank", controller.bankAdmin());
  }

  @Test
  void wipeReset_emptyBackendBody_treatedAsNoop() {
    // Given
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
    when(backendApiClient.post(
            eq("/api/v1/bank/admin/wipe-reset"), eq(Map.of()), eq(BankWipeResetResultDto.class)))
        .thenReturn(null);

    // When
    controller.wipeReset("WIPE", attrs);

    // Then
    assertEquals(Boolean.TRUE, attrs.getFlashAttributes().get("wipeNoop"));
  }
}

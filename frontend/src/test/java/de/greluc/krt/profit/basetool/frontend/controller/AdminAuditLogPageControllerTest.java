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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.greluc.krt.profit.basetool.frontend.model.dto.AuditEventDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.AuditRowView;
import de.greluc.krt.profit.basetool.frontend.model.dto.BankAuditEventDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.profit.basetool.frontend.service.BackendApiClient;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

/**
 * Unit tests for {@link AdminAuditLogPageController}: the five-way tab routing (bank vs the four
 * generic areas), the adaptation of both DTO shapes into the uniform {@link AuditRowView}, the
 * per-tab export endpoint + event-type list, and the in-place fragment selector (REQ-AUDIT-002).
 */
@SuppressWarnings("unchecked")
class AdminAuditLogPageControllerTest {

  private BackendApiClient backendApiClient;
  private AdminAuditLogPageController controller;

  @BeforeEach
  void setUp() {
    backendApiClient = mock(BackendApiClient.class);
    controller = new AdminAuditLogPageController(backendApiClient);
  }

  @Test
  void bankTab_readsBankEndpointAndAdaptsAccountNoAsSubject() {
    // Given
    Model model = new ConcurrentModel();
    BankAuditEventDto bankRow =
        new BankAuditEventDto(
            UUID.randomUUID(),
            Instant.now(),
            "banker_jo",
            "DEPOSIT_BOOKED",
            UUID.randomUUID(),
            "KB-0001",
            null,
            null,
            "+100 aUEC");
    when(backendApiClient.get(
            contains("/api/v1/bank/admin/audit"), any(ParameterizedTypeReference.class)))
        .thenReturn(new PageResponse<>(List.of(bankRow), 0, 50, 1, 1, List.of()));

    // When
    String view = controller.auditLog("BANK", null, null, null, null, 0, null, model);

    // Then
    assertEquals("admin/audit-log", view);
    assertEquals("BANK", model.getAttribute("activeDomain"));
    assertEquals("/api/proxy/audit/BANK/export", model.getAttribute("exportEndpoint"));
    PageResponse<AuditRowView> events = (PageResponse<AuditRowView>) model.getAttribute("events");
    assertNotNull(events);
    AuditRowView row = events.content().get(0);
    assertEquals("KB-0001", row.subject());
    assertEquals("admin.bank.audit.event.DEPOSIT_BOOKED", row.eventLabelKey());
    List<String> eventTypes = (List<String>) model.getAttribute("eventTypes");
    assertTrue(eventTypes.contains("WIPE_RESET_EXECUTED"));
  }

  @Test
  void inventoryTab_readsGenericEndpointAndAdaptsSubjectLabel() {
    // Given
    Model model = new ConcurrentModel();
    AuditEventDto genericRow =
        new AuditEventDto(
            UUID.randomUUID(),
            Instant.now(),
            "INVENTORY",
            "INVENTORY_ITEM_CREATED",
            "logi_jo",
            UUID.randomUUID(),
            "Quantanium @ Port Olisar",
            null,
            "qty=5.0");
    when(backendApiClient.get(
            contains("/api/v1/audit/INVENTORY"), any(ParameterizedTypeReference.class)))
        .thenReturn(new PageResponse<>(List.of(genericRow), 0, 50, 1, 1, List.of()));

    // When
    String view = controller.auditLog("INVENTORY", null, null, null, null, 0, null, model);

    // Then
    assertEquals("admin/audit-log", view);
    assertEquals("INVENTORY", model.getAttribute("activeDomain"));
    assertEquals("/api/proxy/audit/INVENTORY/export", model.getAttribute("exportEndpoint"));
    PageResponse<AuditRowView> events = (PageResponse<AuditRowView>) model.getAttribute("events");
    AuditRowView row = events.content().get(0);
    assertEquals("Quantanium @ Port Olisar", row.subject());
    assertEquals("admin.audit.event.INVENTORY_ITEM_CREATED", row.eventLabelKey());
  }

  @Test
  void unknownDomain_fallsBackToBankTab() {
    // Given
    Model model = new ConcurrentModel();
    when(backendApiClient.get(any(String.class), any(ParameterizedTypeReference.class)))
        .thenReturn(new PageResponse<>(List.of(), 0, 50, 0, 0, List.of()));

    // When
    controller.auditLog("NONSENSE", null, null, null, null, 0, null, model);

    // Then
    assertEquals("BANK", model.getAttribute("activeDomain"));
  }

  @Test
  void fragmentResults_returnsResultsFragmentSelector() {
    // Given
    Model model = new ConcurrentModel();
    when(backendApiClient.get(any(String.class), any(ParameterizedTypeReference.class)))
        .thenReturn(new PageResponse<>(List.of(), 0, 50, 0, 0, List.of()));

    // When
    String view = controller.auditLog("REFINERY", null, null, null, null, 0, "results", model);

    // Then
    assertEquals("admin/audit-log :: auditResults", view);
  }

  @Test
  void backendFailure_setsErrorAttribute() {
    // Given
    Model model = new ConcurrentModel();
    when(backendApiClient.get(any(String.class), any(ParameterizedTypeReference.class)))
        .thenThrow(new RuntimeException("down"));

    // When
    controller.auditLog("JOB_ORDER", null, null, null, null, 0, null, model);

    // Then
    assertEquals("admin.audit.error.load", model.getAttribute("error"));
  }
}

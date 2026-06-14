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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.frontend.model.dto.BankAccountDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.BankHolderDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

@SuppressWarnings("unchecked")
class BankManagePageControllerTest {

  @Test
  void manage_ShouldDefaultToAccountsTabAndFillModel() {
    // Given
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    BankManagePageController controller = new BankManagePageController(backendApiClient);
    Model model = new ConcurrentModel();
    BankAccountDto account =
        new BankAccountDto(
            UUID.randomUUID(),
            "KB-0001",
            "Staffel IRIDIUM",
            "ORG_UNIT",
            "ACTIVE",
            null,
            null,
            BigDecimal.ZERO,
            0L,
            Instant.parse("2026-01-15T10:00:00Z"));
    BankHolderDto holder =
        new BankHolderDto(
            UUID.randomUUID(), UUID.randomUUID(), "greluc", true, BigDecimal.ZERO, 0, 0L);
    when(backendApiClient.get(
            eq("/api/v1/bank/accounts?size=500"), any(ParameterizedTypeReference.class)))
        .thenReturn(new PageResponse<>(List.of(account), 0, 500, 1, 1, Collections.emptyList()));
    when(backendApiClient.get(eq("/api/v1/bank/holders"), any(ParameterizedTypeReference.class)))
        .thenReturn(List.of(holder));
    when(backendApiClient.get(
            eq("/api/v1/org-units/active"), any(ParameterizedTypeReference.class)))
        .thenReturn(List.of());
    when(backendApiClient.get(eq("/api/v1/users/lookup"), any(ParameterizedTypeReference.class)))
        .thenReturn(List.of());

    // When
    String view = controller.manage(null, null, model);

    // Then
    assertEquals("bank-manage", view);
    assertEquals("konten", model.getAttribute("activeTab"));
    List<BankAccountDto> accounts = (List<BankAccountDto>) model.getAttribute("accounts");
    assertNotNull(accounts);
    assertEquals(1, accounts.size());
    List<BankHolderDto> holders = (List<BankHolderDto>) model.getAttribute("holders");
    assertNotNull(holders);
    assertEquals("greluc", holders.get(0).handle());
  }

  @Test
  void manage_ShouldSelectHolderTabAndSurviveNullBackendResponses() {
    // Given
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    BankManagePageController controller = new BankManagePageController(backendApiClient);
    Model model = new ConcurrentModel();
    when(backendApiClient.get(any(String.class), any(ParameterizedTypeReference.class)))
        .thenReturn(null);

    // When
    String view = controller.manage("HALTER", null, model);

    // Then
    assertEquals("bank-manage", view);
    assertEquals("halter", model.getAttribute("activeTab"));
    assertEquals(List.of(), model.getAttribute("accounts"));
    assertEquals(List.of(), model.getAttribute("holders"));
    assertEquals(List.of(), model.getAttribute("orgUnits"));
    assertEquals(List.of(), model.getAttribute("users"));
  }

  // covers REQ-FE-005 (#579) — an in-place re-render (fragment=manageBody) returns only the tab-nav
  // + active panel fragment and skips the creation-modal lookups (org-units, users) that live
  // outside the swapped region.
  @Test
  void manage_fragmentManageBody_rendersOnlyBodyFragment_andSkipsModalLookups() {
    // Given
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    BankManagePageController controller = new BankManagePageController(backendApiClient);
    Model model = new ConcurrentModel();
    when(backendApiClient.get(
            eq("/api/v1/bank/accounts?size=500"), any(ParameterizedTypeReference.class)))
        .thenReturn(new PageResponse<>(List.of(), 0, 500, 0, 0, Collections.emptyList()));
    when(backendApiClient.get(eq("/api/v1/bank/holders"), any(ParameterizedTypeReference.class)))
        .thenReturn(List.of());

    // When
    String view = controller.manage("halter", "manageBody", model);

    // Then
    assertEquals("bank-manage :: manageBody", view);
    assertEquals("halter", model.getAttribute("activeTab"));
    assertNotNull(model.getAttribute("accounts"));
    assertNotNull(model.getAttribute("holders"));
    // The fragment path must not load the creation-modal lookups.
    verify(backendApiClient, never())
        .get(eq("/api/v1/org-units/active"), any(ParameterizedTypeReference.class));
    verify(backendApiClient, never())
        .get(eq("/api/v1/users/lookup"), any(ParameterizedTypeReference.class));
  }
}

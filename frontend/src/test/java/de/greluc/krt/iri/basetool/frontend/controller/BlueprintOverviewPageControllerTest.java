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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.frontend.model.dto.BlueprintOverviewEntryDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.BlueprintOverviewOwnerDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

/** Unit tests for {@link BlueprintOverviewPageController}: model population and owner relay. */
@ExtendWith(MockitoExtension.class)
class BlueprintOverviewPageControllerTest {

  @Mock private BackendApiClient backendApiClient;
  @InjectMocks private BlueprintOverviewPageController controller;

  @Test
  void view_populatesOverview_andReturnsViewName() {
    PageResponse<BlueprintOverviewEntryDto> page =
        new PageResponse<>(
            List.of(new BlueprintOverviewEntryDto("aurora", "Aurora MR", 3L)),
            0,
            1000,
            1,
            1,
            List.of());
    when(backendApiClient.get(
            contains("/api/v1/personal-blueprints/overview?size="),
            any(ParameterizedTypeReference.class)))
        .thenReturn(page);

    Model model = new ExtendedModelMap();
    String view = controller.view(model);

    assertEquals("blueprint-overview", view);
    Object overview = model.getAttribute("overview");
    assertTrue(overview instanceof List<?> && ((List<?>) overview).size() == 1);
  }

  @Test
  void view_onBackendError_setsErrorKey_andEmptyOverview() {
    when(backendApiClient.get(any(String.class), any(ParameterizedTypeReference.class)))
        .thenThrow(new RuntimeException("boom"));

    Model model = new ExtendedModelMap();
    String view = controller.view(model);

    assertEquals("blueprint-overview", view);
    assertEquals("error.blueprintOverview.load", model.getAttribute("error"));
    assertTrue(((List<?>) model.getAttribute("overview")).isEmpty());
  }

  @Test
  void owners_relaysProductKey_asUriVariable_andReturnsList() {
    // The product key is passed as a URI template variable (so the WebClient percent-encodes it),
    // not concatenated into the path — see BackendApiClient#get(String, ParameterizedTypeReference,
    // Object...). The raw key therefore arrives as a separate argument, not inside the template.
    when(backendApiClient.get(
            contains("/api/v1/personal-blueprints/overview/owners?productKey={productKey}"),
            any(ParameterizedTypeReference.class),
            eq("aurora")))
        .thenReturn(List.of(new BlueprintOverviewOwnerDto("Alpha")));

    List<BlueprintOverviewOwnerDto> result = controller.owners("aurora");

    assertEquals(1, result.size());
    assertEquals("Alpha", result.get(0).ownerName());
  }

  @Test
  void owners_onBackendError_returnsEmptyList() {
    when(backendApiClient.get(
            any(String.class), any(ParameterizedTypeReference.class), eq("aurora")))
        .thenThrow(new RuntimeException("boom"));

    assertTrue(controller.owners("aurora").isEmpty());
  }
}

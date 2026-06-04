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

import de.greluc.krt.iri.basetool.frontend.model.dto.BlueprintOverviewEntryDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.BlueprintOverviewOwnerDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Page controller for the org-unit blueprint availability overview (#364): renders which blueprints
 * are available among the members of the caller's oversight org units, and proxies the lazy owner
 * drill-down. The backend ({@code /api/v1/personal-blueprints/overview}) is the security boundary —
 * it returns data only to admins, officers (their Staffel) and Spezialkommando leads (their SK);
 * the sidebar entry is hidden from everyone else via the {@code canSeeBlueprintOverview} model
 * attribute. A direct hit by a non-eligible caller degrades to an empty page rather than a stack
 * trace.
 */
@Controller
@RequestMapping("/blueprint-overview")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Slf4j
public class BlueprintOverviewPageController {

  /** Page size for the overview fetch — generous so the client-side filter sees every product. */
  private static final int PAGE_SIZE = 1000;

  private final BackendApiClient backendApiClient;

  /**
   * Renders the blueprint availability list (one row per product, with the owning-member count and
   * a lazy owner drill-down). A backend failure collapses to an empty list plus an error banner.
   *
   * @param model Thymeleaf model populated with the overview list
   * @return the {@code blueprint-overview} view name
   */
  @GetMapping
  public String view(Model model) {
    List<BlueprintOverviewEntryDto> overview = new ArrayList<>();
    try {
      PageResponse<BlueprintOverviewEntryDto> res =
          backendApiClient.get(
              "/api/v1/personal-blueprints/overview?size=" + PAGE_SIZE,
              new ParameterizedTypeReference<>() {});
      if (res != null && res.content() != null) {
        overview = new ArrayList<>(res.content());
      }
    } catch (Exception e) {
      log.error("Failed to fetch blueprint availability overview", e);
      model.addAttribute("error", "error.blueprintOverview.load");
    }
    model.addAttribute("overview", overview);
    return "blueprint-overview";
  }

  /**
   * Lazy owner drill-down proxy: relays to the backend owners endpoint for one product and returns
   * the in-scope owners' display names as JSON. Failures collapse to an empty list so the expand
   * panel renders a graceful "no data" state rather than a stack trace.
   *
   * @param productKey the normalized product key whose owners to list
   * @return the owning in-scope members, or an empty list on any backend failure
   */
  @GetMapping("/owners")
  @ResponseBody
  public List<BlueprintOverviewOwnerDto> owners(@RequestParam String productKey) {
    try {
      List<BlueprintOverviewOwnerDto> owners =
          backendApiClient.get(
              "/api/v1/personal-blueprints/overview/owners?productKey={productKey}",
              new ParameterizedTypeReference<>() {},
              productKey);
      return owners != null ? owners : List.of();
    } catch (Exception e) {
      log.warn(
          "Failed to fetch blueprint owners for productKey='{}': {}", productKey, e.getMessage());
      return List.of();
    }
  }
}

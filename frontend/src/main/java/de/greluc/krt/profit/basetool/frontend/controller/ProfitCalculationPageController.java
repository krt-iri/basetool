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

import de.greluc.krt.profit.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.profit.basetool.frontend.model.dto.ShipTypeDto;
import de.greluc.krt.profit.basetool.frontend.service.BackendApiClient;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Spring MVC controller for the profit-calculation page ({@code /materials/profit-calculation}).
 *
 * <p>Pulls the cached ship-type catalog (filtered to ships with non-zero SCU capacity — the only
 * ones a profit calculation makes sense for) and the terminal list (to derive the unique set of
 * star systems for the dropdown). The C2 Hercules Starlifter is the default ship choice when
 * present, mirroring the gameplay convention that profit runs are usually planned around the C2.
 */
@Slf4j
@Controller
@RequestMapping("/materials/profit-calculation")
@RequiredArgsConstructor
public class ProfitCalculationPageController {

  private final BackendApiClient backendApiClient;

  /**
   * Renders the profit-calculation page after loading the ship-type catalog (capacity-filtered) and
   * the distinct set of star systems from the terminal list. A backend failure for either call
   * surfaces as a generic page-level error and renders an empty form rather than aborting.
   *
   * @param model Thymeleaf model populated with {@code shipTypes}, {@code defaultShipId} and {@code
   *     starSystems}
   * @return the {@code materials-profit-calculation} view name
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('SQUADRON_MEMBER', 'MEMBER', 'OFFICER', 'ADMIN')")
  public String showProfitCalculationPage(Model model) {
    log.debug("Showing profit calculation page");
    try {
      // Fetch ship types for the dropdown
      PageResponse<ShipTypeDto> shipTypesPage =
          backendApiClient.getCached(
              "/api/v1/ship-types?size=1000&sort=name,asc",
              new ParameterizedTypeReference<PageResponse<ShipTypeDto>>() {});

      List<ShipTypeDto> shipTypes =
          (shipTypesPage != null && shipTypesPage.content() != null)
              ? shipTypesPage.content().stream()
                  .filter(s -> s.scu() != null && s.scu() > 0)
                  .toList()
              : List.of();

      model.addAttribute("shipTypes", shipTypes);

      // Set C2 as default ship if present
      shipTypes.stream()
          .filter(s -> s.name().contains("C2 Hercules Starlifter"))
          .findFirst()
          .ifPresent(c2 -> model.addAttribute("defaultShipId", c2.id()));

      // Fetch terminals to get unique star systems
      PageResponse<Map<String, Object>> terminalsPage =
          backendApiClient.getCached(
              "/api/v1/terminals?size=10000",
              new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {});

      Set<String> starSystems = new TreeSet<>();
      if (terminalsPage != null && terminalsPage.content() != null) {
        for (Map<String, Object> terminal : terminalsPage.content()) {
          Object system = terminal.get("starSystemName");
          if (system != null && !system.toString().isBlank()) {
            starSystems.add(system.toString());
          }
        }
      }
      model.addAttribute("starSystems", starSystems);

    } catch (Exception e) {
      log.error("Error loading data for profit calculation page", e);
      model.addAttribute("error", "error.profit_calculation.load");
    }

    return "materials-profit-calculation";
  }
}

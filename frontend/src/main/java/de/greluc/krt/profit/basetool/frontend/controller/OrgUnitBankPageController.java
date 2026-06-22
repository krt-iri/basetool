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

import de.greluc.krt.profit.basetool.frontend.model.dto.BankBookingRequestDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.OrgUnitBankBalanceDto;
import de.greluc.krt.profit.basetool.frontend.service.BackendApiClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Renders the org-unit officer/lead bank view (epic #666 F1/F2): the balance-only cards of the
 * accounts the caller oversees, the request form to raise a confirm-before-post deposit/withdrawal,
 * and the caller's own request list with a cancel action. This is the org-unit-facing surface —
 * gated to leadership roles, deliberately <em>not</em> {@code BANK_EMPLOYEE}; the backend's
 * oversight scope decides the actual data (a caller who oversees nothing sees an empty page). The
 * booking actions are AJAX writes via {@code /api/proxy/org-units/bank/**} that swap the {@code
 * orgUnitBank} fragment in place (no reload, REQ-FE-005).
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class OrgUnitBankPageController {

  private final BackendApiClient backendApiClient;

  /**
   * Renders the page (or its {@code orgUnitBank} fragment for an in-place swap after a write).
   *
   * @param fragment when {@code "orgUnitBank"} only the balances + own-request region is
   *     re-rendered (AJAX swap after create/cancel); otherwise the full page is returned
   * @param model Spring MVC model
   * @return the template, or its {@code orgUnitBank} fragment view
   */
  @GetMapping("/org-unit-bank")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER', 'LOGISTICIAN', 'MISSION_MANAGER')")
  public String orgUnitBank(@RequestParam(required = false) String fragment, Model model) {
    List<OrgUnitBankBalanceDto> balances =
        backendApiClient.get(
            "/api/v1/org-units/bank/balances",
            new ParameterizedTypeReference<List<OrgUnitBankBalanceDto>>() {});
    List<BankBookingRequestDto> ownRequests =
        backendApiClient.get(
            "/api/v1/org-units/bank/requests",
            new ParameterizedTypeReference<List<BankBookingRequestDto>>() {});
    List<OrgUnitBankBalanceDto> safeBalances =
        balances == null ? List.<OrgUnitBankBalanceDto>of() : balances;
    model.addAttribute("balances", safeBalances);
    model.addAttribute(
        "ownRequests", ownRequests == null ? List.<BankBookingRequestDto>of() : ownRequests);
    model.addAttribute("sparks", sparksByAccountId(safeBalances));
    if ("orgUnitBank".equals(fragment)) {
      return "org-unit-bank :: orgUnitBank";
    }
    return "org-unit-bank";
  }

  /**
   * Pre-scales each balance card's 30-day end-of-day series into its SVG sparkline polyline ({@link
   * BankSparkline}), keyed by account id — Thymeleaf should not carry the scaling logic, and keying
   * by account id lets the template look the spark up per card the same way the bank-detail page
   * reads its distribution percents.
   *
   * @param balances the visible balance cards (never {@code null})
   * @return account id to its scaled sparkline; same iteration order as {@code balances}
   */
  private static Map<UUID, BankSparkline.Spark> sparksByAccountId(
      List<OrgUnitBankBalanceDto> balances) {
    Map<UUID, BankSparkline.Spark> sparks = new LinkedHashMap<>();
    for (OrgUnitBankBalanceDto balance : balances) {
      sparks.put(balance.accountId(), BankSparkline.of(balance.sparkline()));
    }
    return sparks;
  }
}

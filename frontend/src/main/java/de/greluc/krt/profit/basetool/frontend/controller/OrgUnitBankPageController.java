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

import de.greluc.krt.profit.basetool.frontend.model.dto.BankAccountRefDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.BankBookingDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.BankBookingRequestDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.OrgUnitBankAccountDetailDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.OrgUnitBankAccountSettingsDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.OrgUnitBankBalanceDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.profit.basetool.frontend.model.dto.UserReferenceDto;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Renders the org-unit bank view (epic #666 + REQ-BANK-034..038): the balance cards of every
 * account the caller may view, the read-only account drill-in (history + Halter-redacted
 * statement), the responsible-holder/OL settings (balance target + configurable visibility), and
 * the officer/lead booking-request flow. This is the org-unit-facing surface — reachable by any KRT
 * member (the cartel account is visible to all, REQ-BANK-037, and a member may have been granted
 * access to other accounts), deliberately <em>not</em> {@code BANK_EMPLOYEE}; the backend seam
 * decides the actual data per account. Booking/settings writes are AJAX swaps via {@code
 * /api/proxy/org-units/bank/**} (no reload, REQ-FE-005).
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class OrgUnitBankPageController {

  /** Any KRT member (or above) may reach the page; the backend seam scopes the data per account. */
  private static final String MEMBER_OR_ABOVE =
      "hasAnyRole('ADMIN','OFFICER','LOGISTICIAN','MISSION_MANAGER','KRT_MEMBER')";

  private final BackendApiClient backendApiClient;

  /**
   * Renders the overview page (or its {@code orgUnitBank} fragment for an in-place swap after a
   * booking write).
   *
   * @param fragment when {@code "orgUnitBank"} only the balances + own-request region is
   *     re-rendered
   * @param model Spring MVC model
   * @return the template, or its {@code orgUnitBank} fragment view
   */
  @GetMapping("/org-unit-bank")
  @PreAuthorize(MEMBER_OR_ABOVE)
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
    // Drives the single page-level "request" CTA + its modal account selector (REQ-BANK-022/-039):
    // shown iff at least one visible account is request-capable (canRequest = the caller may view a
    // request-capable account).
    boolean anyCanRequest = safeBalances.stream().anyMatch(OrgUnitBankBalanceDto::canRequest);
    model.addAttribute("anyCanRequest", anyCanRequest);
    // "Fremde Anträge" tab (REQ-BANK-041): requests on the accounts the caller is responsible for.
    // The tab is shown whenever the caller manages any account (responsible holder / OL / admin),
    // even when it currently holds no request.
    List<BankBookingRequestDto> foreignRequests =
        backendApiClient.get(
            "/api/v1/org-units/bank/requests/foreign",
            new ParameterizedTypeReference<List<BankBookingRequestDto>>() {});
    model.addAttribute(
        "foreignRequests",
        foreignRequests == null ? List.<BankBookingRequestDto>of() : foreignRequests);
    // Show the tab when the caller manages a REQUEST-CAPABLE account (canManageSettings on an
    // ORG_UNIT/AREA/CARTEL account == its responsible holder). This matches the backend scope of
    // /requests/foreign (responsibleAccountIds), so the tab is never shown empty to an
    // OL/management
    // user who can only configure a SPECIAL account's visibility (SPECIAL is never
    // request-capable).
    model.addAttribute(
        "hasResponsibleAccounts",
        safeBalances.stream().anyMatch(b -> b.canManageSettings() && b.canRequest()));
    // Transfer-request destination picker (REQ-BANK-040): any active account. Only fetched when the
    // request modal is shown at all.
    List<BankAccountRefDto> transferTargets =
        anyCanRequest
            ? backendApiClient.get(
                "/api/v1/org-units/bank/transfer-targets",
                new ParameterizedTypeReference<List<BankAccountRefDto>>() {})
            : List.<BankAccountRefDto>of();
    model.addAttribute(
        "requestTransferTargets",
        transferTargets == null ? List.<BankAccountRefDto>of() : transferTargets);
    if ("orgUnitBank".equals(fragment)) {
      return "org-unit-bank :: orgUnitBank";
    }
    return "org-unit-bank";
  }

  /**
   * Renders the read-only account drill-in (REQ-BANK-038) — history (Halter redacted) + the
   * Kontoauszug export — plus, for the responsible holder / OL, the settings region (balance target
   * + configurable visibility). The {@code orgUnitBankBookings} fragment re-renders the paginated
   * history in place; the {@code orgUnitBankSettings} fragment re-renders the facts + settings
   * region after a target/visibility write.
   *
   * @param id the account id
   * @param page zero-based booking-history page index
   * @param fragment {@code "orgUnitBankBookings"} (pager swap) or {@code "orgUnitBankSettings"}
   *     (settings swap), else the full page
   * @param model Spring MVC model
   * @return the template, or one of its fragment views
   */
  @GetMapping("/org-unit-bank/accounts/{id}")
  @PreAuthorize(MEMBER_OR_ABOVE)
  public String orgUnitBankAccount(
      @PathVariable UUID id,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) String fragment,
      Model model) {
    OrgUnitBankAccountDetailDto detail =
        backendApiClient.get(
            "/api/v1/org-units/bank/accounts/" + id, OrgUnitBankAccountDetailDto.class);
    int effectivePage = page == null || page < 0 ? 0 : page;
    PageResponse<BankBookingDto> bookings =
        backendApiClient.get(
            "/api/v1/org-units/bank/accounts/" + id + "/transactions?page=" + effectivePage,
            new ParameterizedTypeReference<PageResponse<BankBookingDto>>() {});
    model.addAttribute("detail", detail);
    model.addAttribute("bookings", bookings);
    model.addAttribute("paginationBaseUrl", "/org-unit-bank/accounts/" + id);

    boolean canManage =
        detail != null
            && (detail.canSetTarget()
                || detail.canConfigureVisibility()
                || detail.canConfigureApprovalLimits());
    OrgUnitBankAccountSettingsDto settings = null;
    List<UserReferenceDto> users = List.of();
    if (canManage) {
      settings =
          backendApiClient.get(
              "/api/v1/org-units/bank/accounts/" + id + "/settings",
              OrgUnitBankAccountSettingsDto.class);
      // The user dropdown feeds both the individual-visibility and the individual-limit pickers.
      if (detail.canConfigureVisibility() || detail.canConfigureApprovalLimits()) {
        List<UserReferenceDto> lookup =
            backendApiClient.get(
                "/api/v1/users/lookup",
                new ParameterizedTypeReference<List<UserReferenceDto>>() {});
        users = lookup == null ? List.<UserReferenceDto>of() : lookup;
      }
    }
    model.addAttribute("settings", settings);
    model.addAttribute("users", users);

    if ("orgUnitBankBookings".equals(fragment)) {
      return "org-unit-bank-account-detail :: orgUnitBankBookings";
    }
    if ("orgUnitBankSettings".equals(fragment)) {
      return "org-unit-bank-account-detail :: orgUnitBankSettings";
    }
    return "org-unit-bank-account-detail";
  }

  /**
   * Pre-scales each balance card's 30-day end-of-day series into its SVG sparkline polyline ({@link
   * BankSparkline}), keyed by account id.
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

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

import de.greluc.krt.profit.basetool.frontend.model.dto.BankAccountDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.BankHolderDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.OrgUnitMembershipOptionDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.profit.basetool.frontend.model.dto.UserReferenceDto;
import de.greluc.krt.profit.basetool.frontend.service.BackendApiClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Renders the bank management page ({@code /bank/manage}, W1 mockup): the account-lifecycle tab and
 * the holder-registry tab, each with its creation modal. Management-only — admins pass via the role
 * hierarchy (REQ-BANK-010).
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class BankManagePageController {

  private final BackendApiClient backendApiClient;

  /**
   * Renders the management page with both tabs' data: all accounts (incl. balances — the
   * zero-balance rule disables the close button server-knowledge-first) and the holder registry
   * with custody totals. The org-unit list and the user lookup feed the two creation modals.
   *
   * @param tab the active tab ({@code konten} default, {@code halter})
   * @param fragment when {@code "manageBody"} only the tab-nav + active panel are re-rendered after
   *     an account/holder lifecycle write (REQ-FE-005), refreshing the row plus the tab-count
   *     aggregates in place; the creation-modal lookups (org-units, users) are then skipped because
   *     the modals live outside the swapped region
   * @param model Spring MVC model
   * @return the manage template, or its {@code manageBody} fragment for an AJAX swap
   */
  @GetMapping("/bank/manage")
  @PreAuthorize("hasRole('BANK_MANAGEMENT')")
  public String manage(
      @RequestParam(required = false) String tab,
      @RequestParam(required = false) String fragment,
      Model model) {
    PageResponse<BankAccountDto> accounts =
        backendApiClient.get(
            "/api/v1/bank/accounts?size=500", new ParameterizedTypeReference<>() {});
    List<BankHolderDto> holders =
        backendApiClient.get(
            "/api/v1/bank/holders", new ParameterizedTypeReference<List<BankHolderDto>>() {});
    model.addAttribute(
        "accounts", accounts == null ? List.<BankAccountDto>of() : accounts.content());
    model.addAttribute("holders", holders == null ? List.<BankHolderDto>of() : holders);
    model.addAttribute("activeTab", "halter".equalsIgnoreCase(tab) ? "halter" : "konten");
    if ("manageBody".equals(fragment)) {
      return "bank-manage :: manageBody";
    }

    List<OrgUnitMembershipOptionDto> orgUnits =
        backendApiClient.get(
            "/api/v1/org-units/active",
            new ParameterizedTypeReference<List<OrgUnitMembershipOptionDto>>() {});
    List<UserReferenceDto> users =
        backendApiClient.get(
            "/api/v1/users/lookup", new ParameterizedTypeReference<List<UserReferenceDto>>() {});
    model.addAttribute(
        "orgUnits", orgUnits == null ? List.<OrgUnitMembershipOptionDto>of() : orgUnits);
    model.addAttribute("users", users == null ? List.<UserReferenceDto>of() : users);
    return "bank-manage";
  }
}

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

import de.greluc.krt.iri.basetool.frontend.model.dto.BankAuditEventDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.BankWipeResetResultDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Spring MVC controller for the admin bank pages (epic #556 Phase 4, REQ-BANK-013/-012): the
 * wipe-reset danger card ({@code /admin/bank}, A1 mockup) and the audit-log viewer ({@code
 * /admin/bank-audit}, A2 mockup). Admin-only — class-level {@code @PreAuthorize} matches the
 * backend's admin URL gate; bank management does NOT reach these pages (REQ-BANK-010).
 *
 * <p>The wipe-reset is a server-side PRG form post (not AJAX) gated by a type-to-confirm hurdle in
 * the browser; the backend re-enforces the admin role and the idempotency.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminBankPageController {

  /** Audit-log page size; the table is dense and read-only, so a large page is fine. */
  private static final int AUDIT_PAGE_SIZE = 50;

  /** The event types offered in the audit filter dropdown, in a sensible grouping order. */
  private static final List<String> EVENT_TYPES =
      List.of(
          "ACCOUNT_CREATED",
          "ACCOUNT_RENAMED",
          "ACCOUNT_CLOSED",
          "ACCOUNT_REOPENED",
          "HOLDER_REGISTERED",
          "HOLDER_DEACTIVATED",
          "HOLDER_REACTIVATED",
          "GRANT_CREATED",
          "GRANT_UPDATED",
          "GRANT_REVOKED",
          "DEPOSIT_BOOKED",
          "WITHDRAWAL_BOOKED",
          "TRANSFER_BOOKED",
          "HOLDER_REBOOKED",
          "TRANSACTION_REVERSED",
          "WIPE_RESET_EXECUTED",
          "STATEMENT_EXPORTED",
          "MANAGEMENT_REPORT_EXPORTED");

  private final BackendApiClient backendApiClient;

  /**
   * Renders the wipe-reset danger card.
   *
   * @param model Thymeleaf model
   * @return the {@code admin/bank} view name
   */
  @GetMapping("/admin/bank")
  public String bankAdmin(Model model) {
    return "admin/bank";
  }

  /**
   * Executes the wipe reset against the backend and redirects back with a flash result: the
   * affected counts on success (incl. the idempotent no-op = zero counts), or an error flag on
   * failure. The {@code confirm} field must equal {@code WIPE} — a server-side backstop to the
   * type-to-confirm modal so a crafted POST without the hurdle is still rejected.
   *
   * @param confirm the type-to-confirm token; must equal {@code WIPE}
   * @param redirectAttributes flash attributes carrier
   * @return redirect back to the admin bank page
   */
  @PostMapping("/admin/bank/wipe-reset")
  public String wipeReset(
      @RequestParam(required = false) String confirm, RedirectAttributes redirectAttributes) {
    if (!"WIPE".equals(confirm)) {
      redirectAttributes.addFlashAttribute("error", "admin.bank.wipe.error.confirm");
      return "redirect:/admin/bank";
    }
    try {
      BankWipeResetResultDto result =
          backendApiClient.post(
              "/api/v1/bank/admin/wipe-reset", Map.of(), BankWipeResetResultDto.class);
      if (result == null || result.accountsReset() == 0) {
        redirectAttributes.addFlashAttribute("wipeNoop", true);
      } else {
        redirectAttributes.addFlashAttribute("wipeResult", result);
      }
    } catch (Exception e) {
      log.error("Bank wipe reset failed", e);
      redirectAttributes.addFlashAttribute("error", "admin.bank.wipe.error.failed");
    }
    return "redirect:/admin/bank";
  }

  /**
   * Renders the paged, filterable audit-log viewer.
   *
   * @param from period start filter (ISO instant), or absent
   * @param to period end filter (ISO instant), or absent
   * @param accountId account filter, or absent
   * @param eventType event-type filter, or absent
   * @param page zero-based page index
   * @param model Thymeleaf model
   * @return the {@code admin/bank-audit} view name
   */
  @GetMapping("/admin/bank-audit")
  public String bankAudit(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String accountId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false, defaultValue = "0") int page,
      Model model) {
    UriComponentsBuilder uri =
        UriComponentsBuilder.fromPath("/api/v1/bank/admin/audit")
            .queryParam("page", Math.max(page, 0))
            .queryParam("size", AUDIT_PAGE_SIZE);
    if (from != null && !from.isBlank()) {
      uri.queryParam("from", from);
    }
    if (to != null && !to.isBlank()) {
      uri.queryParam("to", to);
    }
    if (accountId != null && !accountId.isBlank()) {
      uri.queryParam("accountId", accountId);
    }
    if (eventType != null && !eventType.isBlank()) {
      uri.queryParam("eventType", eventType);
    }

    PageResponse<BankAuditEventDto> events = null;
    try {
      events =
          backendApiClient.get(
              uri.toUriString(),
              new ParameterizedTypeReference<PageResponse<BankAuditEventDto>>() {});
    } catch (Exception e) {
      log.error("Failed to load bank audit log", e);
      model.addAttribute("error", "admin.bank.audit.error.load");
    }

    model.addAttribute("events", events);
    model.addAttribute("eventTypes", EVENT_TYPES);
    model.addAttribute("filterFrom", from);
    model.addAttribute("filterTo", to);
    model.addAttribute("filterAccountId", accountId);
    model.addAttribute("filterEventType", eventType);
    model.addAttribute("paginationBaseUrl", buildBaseUrl(from, to, accountId, eventType));
    return "admin/bank-audit";
  }

  /**
   * Builds the pagination base URL preserving the active filters so paging keeps the filter set.
   *
   * @param from period start filter, or {@code null}
   * @param to period end filter, or {@code null}
   * @param accountId account filter, or {@code null}
   * @param eventType event-type filter, or {@code null}
   * @return the base URL with the filter query parameters (no page/size)
   */
  private static String buildBaseUrl(String from, String to, String accountId, String eventType) {
    UriComponentsBuilder base = UriComponentsBuilder.fromPath("/admin/bank-audit");
    if (from != null && !from.isBlank()) {
      base.queryParam("from", from);
    }
    if (to != null && !to.isBlank()) {
      base.queryParam("to", to);
    }
    if (accountId != null && !accountId.isBlank()) {
      base.queryParam("accountId", accountId);
    }
    if (eventType != null && !eventType.isBlank()) {
      base.queryParam("eventType", eventType);
    }
    return base.toUriString();
  }
}

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

package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.BankAuditEventType;
import de.greluc.krt.iri.basetool.backend.model.dto.BankAuditEventDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BankWipeResetResultDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.service.BankAuditService;
import de.greluc.krt.iri.basetool.backend.service.BankLedgerService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only bank surface (epic #556, REQ-BANK-012/-013): the wipe reset and the audit-log viewer.
 * Double-gated — the {@code /api/v1/bank/admin/**} URL matcher requires {@code ADMIN} before these
 * method gates even run; bank management explicitly does NOT see the audit log (REQ-BANK-010).
 */
@RestController
@RequestMapping("/api/v1/bank/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BankAdminController {

  private static final Set<String> AUDIT_SORT_FIELDS = Set.of("occurredAt", "id");

  private final BankLedgerService bankLedgerService;
  private final BankAuditService bankAuditService;

  /**
   * Resets all account balances and holder sub-balances to zero after a Star Citizen wipe
   * (REQ-BANK-013). Idempotent — the result reports zero accounts on an all-zero bank.
   *
   * @return counts and total for the admin notice
   */
  @Operation(summary = "Reset all bank balances to zero after an SC wipe (admin)")
  @PostMapping("/wipe-reset")
  @Transactional
  public BankWipeResetResultDto wipeReset() {
    return bankLedgerService.resetAllBalances();
  }

  /**
   * Pages over the filtered audit log (REQ-BANK-012), newest first by default.
   *
   * @param from period start (inclusive, ISO instant), or absent
   * @param to period end (inclusive, ISO instant), or absent
   * @param actorUserId filter on the acting user, or absent
   * @param accountId filter on the affected account, or absent
   * @param eventType filter on the event type, or absent
   * @param page zero-based page index
   * @param size page size
   * @param sort whitelisted sort spec
   * @return one page of audit events
   */
  @Operation(summary = "Read the bank audit log (admin; paged, filterable)")
  @GetMapping("/audit")
  @Transactional(readOnly = true)
  public PageResponse<BankAuditEventDto> getAuditLog(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) UUID actorUserId,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(required = false) BankAuditEventType eventType,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    String effectiveSort = sort == null || sort.isBlank() ? "occurredAt,desc" : sort;
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page, size, effectiveSort, AUDIT_SORT_FIELDS, "occurredAt");
    Page<BankAuditEventDto> result =
        bankAuditService.getEvents(from, to, actorUserId, accountId, eventType, pageable);
    return new PageResponse<>(
        result.getContent(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages(),
        PaginationUtil.toSortStrings(result.getSort()));
  }
}

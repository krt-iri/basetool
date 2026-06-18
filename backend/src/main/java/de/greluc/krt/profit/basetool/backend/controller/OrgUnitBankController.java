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

package de.greluc.krt.profit.basetool.backend.controller;

import de.greluc.krt.profit.basetool.backend.model.dto.BankBookingRequestDto;
import de.greluc.krt.profit.basetool.backend.model.dto.OrgUnitBankBalanceDto;
import de.greluc.krt.profit.basetool.backend.model.dto.request.CancelBankBookingRequest;
import de.greluc.krt.profit.basetool.backend.model.dto.request.CreateBankBookingRequest;
import de.greluc.krt.profit.basetool.backend.service.OrgUnitBankAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The org-unit-facing slice of the bank, living at {@code /api/v1/org-units/bank} — deliberately
 * <em>outside</em> the {@code /api/v1/bank/**} space that URL-gates {@code BANK_EMPLOYEE}. It
 * serves the officers and leads who oversee an org unit, never the bank staff: an officer or lead
 * may read the balance of their own org unit's account (F1, REQ-BANK-021) and, in later phases,
 * raise confirm-before-post booking requests against it (F2, REQ-BANK-022). All org-unit logic
 * lives in {@link OrgUnitBankAccessService}; this controller only relays. Authorization is
 * coarse-gated to any authenticated caller and finely scoped in the service via the oversight
 * scope, so a caller who oversees nothing receives an empty result rather than another caller's
 * data.
 */
@RestController
@RequestMapping("/api/v1/org-units/bank")
@RequiredArgsConstructor
@Tag(
    name = "org-unit-bank-controller",
    description = "Org-unit officer/lead bank access (balance view, booking requests)")
public class OrgUnitBankController {

  private final OrgUnitBankAccessService orgUnitBankAccessService;

  /**
   * Returns the balance-only view of every org-unit account the caller oversees (REQ-BANK-021, F1).
   * {@code isAuthenticated}: the fine-grained scope is decided in the service from the caller's
   * oversight scope (officer → own Staffel, SK lead → led SK(s), admin → all/pinned), so a plain
   * member gets an empty list and the endpoint never reveals accounts outside the caller's scope.
   *
   * @return the overseen org-unit balances, ordered by account number; empty when the caller
   *     oversees no org unit that owns an account
   */
  @GetMapping("/balances")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "List the balances of the org-unit accounts the caller oversees",
      description =
          "Returns the current balance of each org-unit bank account the authenticated officer or"
              + " lead oversees (their own Staffel / the Spezialkommando(s) they lead; admins see"
              + " all or the pinned org unit). Balance-only by design — no history, no holders, no"
              + " audit. A caller with no oversight scope receives an empty list.")
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "Overseen org-unit balances")})
  public List<OrgUnitBankBalanceDto> listOverseenBalances() {
    return orgUnitBankAccessService.listOverseenOrgUnitBalances();
  }

  /**
   * Raises a confirm-before-post deposit/withdrawal request for an org unit the caller oversees
   * (REQ-BANK-022, F2). The request is recorded as {@code PENDING} and audited, but moves no money
   * until a bank employee confirms it. The service rejects an org unit outside the caller's
   * oversight scope.
   *
   * @param request the create payload (org unit, type, amount, note)
   * @return the created pending request
   */
  @PostMapping("/requests")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Raise a confirm-before-post booking request for an overseen org unit",
      description =
          "Creates a PENDING deposit/withdrawal request against the bank account of an org unit the"
              + " authenticated officer/lead oversees. The request is audited immediately but moves"
              + " no money until a bank employee confirms it; no holder is chosen by the"
              + " requester.")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Request created")})
  public BankBookingRequestDto createBookingRequest(
      @Valid @RequestBody CreateBankBookingRequest request) {
    return orgUnitBankAccessService.createBookingRequest(request);
  }

  /**
   * Lists the caller's own booking requests with their current status (REQ-BANK-022). Per-user
   * isolation — never another requester's requests.
   *
   * @return the caller's requests, newest first
   */
  @GetMapping("/requests")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "List the caller's own booking requests and their status")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The caller's requests")})
  public List<BankBookingRequestDto> listOwnBookingRequests() {
    return orgUnitBankAccessService.listOwnBookingRequests();
  }

  /**
   * Cancels one of the caller's own pending booking requests (REQ-BANK-022). A request that is not
   * the caller's, or no longer pending, is rejected.
   *
   * @param id the request to cancel
   * @param request the echoed optimistic-locking version
   * @return the cancelled request
   */
  @PostMapping("/requests/{id}/cancel")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Cancel one of the caller's own pending booking requests")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Request cancelled")})
  public BankBookingRequestDto cancelOwnBookingRequest(
      @PathVariable UUID id, @Valid @RequestBody CancelBankBookingRequest request) {
    return orgUnitBankAccessService.cancelOwnBookingRequest(id, request.version());
  }
}

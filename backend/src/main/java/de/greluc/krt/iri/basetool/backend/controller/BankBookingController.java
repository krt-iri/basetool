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

import de.greluc.krt.iri.basetool.backend.model.dto.BankTransactionDto;
import de.greluc.krt.iri.basetool.backend.model.dto.request.BankDepositRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.request.BankTransferRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.request.BankWithdrawalRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.request.ReverseBankTransactionRequest;
import de.greluc.krt.iri.basetool.backend.service.BankLedgerService;
import de.greluc.krt.iri.basetool.backend.service.BankSecurityService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the booking flows (epic #556, REQ-BANK-004/-011): deposits, withdrawals,
 * transfers (incl. intra-account holder rebookings) and reversals. The capability gates evaluate
 * the caller's grant flags on the affected account via {@code BankSecurityService} — management and
 * admins pass unrestricted; reversals are management-only (spec open-question 3, v1 decision).
 */
@RestController
@RequestMapping("/api/v1/bank")
@RequiredArgsConstructor
public class BankBookingController {

  private final BankLedgerService bankLedgerService;
  private final BankSecurityService bankSecurityService;

  /**
   * Books a deposit onto an account the caller may deposit to (REQ-BANK-009).
   *
   * @param request validated deposit payload
   * @return acknowledgement of the created transaction
   */
  @Operation(summary = "Book a deposit")
  @PostMapping("/deposits")
  @PreAuthorize(
      "hasRole('BANK_EMPLOYEE') and @bankSecurityService.canDeposit(#request.accountId,"
          + " authentication)")
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  public BankTransactionDto bookDeposit(@RequestBody @Valid BankDepositRequest request) {
    return bankLedgerService.bookDeposit(request);
  }

  /**
   * Books a withdrawal from an account the caller may withdraw from (REQ-BANK-009), guarded by the
   * no-overdraft rule (REQ-BANK-006).
   *
   * @param request validated withdrawal payload
   * @return acknowledgement of the created transaction
   */
  @Operation(summary = "Book a withdrawal")
  @PostMapping("/withdrawals")
  @PreAuthorize(
      "hasRole('BANK_EMPLOYEE') and @bankSecurityService.canWithdraw(#request.accountId,"
          + " authentication)")
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  public BankTransactionDto bookWithdrawal(@RequestBody @Valid BankWithdrawalRequest request) {
    return bankLedgerService.bookWithdrawal(request);
  }

  /**
   * Books a transfer: {@code can_transfer} on the source account is the gate; the destination must
   * be visible to the caller (REQ-BANK-011 v1 destination rule) — evaluated here and handed to the
   * service.
   *
   * @param request validated transfer payload
   * @param authentication the caller's authentication (for the destination-visibility check)
   * @return acknowledgement of the created transaction
   */
  @Operation(summary = "Book a transfer or intra-account holder rebooking")
  @PostMapping("/transfers")
  @PreAuthorize(
      "hasRole('BANK_EMPLOYEE') and @bankSecurityService.canTransfer(#request.sourceAccountId,"
          + " authentication)")
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  public BankTransactionDto bookTransfer(
      @RequestBody @Valid BankTransferRequest request, Authentication authentication) {
    boolean destinationVisible =
        bankSecurityService.canSee(request.destinationAccountId(), authentication);
    return bankLedgerService.bookTransfer(request, destinationVisible);
  }

  /**
   * Reverses a transaction with a negated-mirror correction booking (REQ-BANK-004, ADR-0010).
   *
   * @param id the transaction to reverse
   * @param request optional correction note
   * @return acknowledgement of the created reversal
   */
  @Operation(summary = "Reverse a transaction (management)")
  @PostMapping("/transactions/{id}/reversal")
  @PreAuthorize("hasRole('BANK_MANAGEMENT')")
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  public BankTransactionDto reverseTransaction(
      @PathVariable @NotNull UUID id,
      @RequestBody(required = false) @Valid ReverseBankTransactionRequest request) {
    return bankLedgerService.reverseTransaction(id, request == null ? null : request.note());
  }
}

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

import de.greluc.krt.profit.basetool.backend.model.dto.BankAccountDetailDto;
import de.greluc.krt.profit.basetool.backend.model.dto.BankAccountDto;
import de.greluc.krt.profit.basetool.backend.model.dto.BankBookingDto;
import de.greluc.krt.profit.basetool.backend.model.dto.BankCapabilitiesDto;
import de.greluc.krt.profit.basetool.backend.model.dto.BankHolderBalanceDto;
import de.greluc.krt.profit.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.profit.basetool.backend.model.dto.request.BankAccountLifecycleRequest;
import de.greluc.krt.profit.basetool.backend.model.dto.request.CreateBankAccountRequest;
import de.greluc.krt.profit.basetool.backend.model.dto.request.RenameBankAccountRequest;
import de.greluc.krt.profit.basetool.backend.service.AuthHelperService;
import de.greluc.krt.profit.basetool.backend.service.BankAccountService;
import de.greluc.krt.profit.basetool.backend.service.BankSecurityService;
import de.greluc.krt.profit.basetool.backend.service.BankStatementReportService;
import de.greluc.krt.profit.basetool.backend.web.PaginationUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for bank accounts (epic #556, REQ-BANK-001/-002/-010): paged listing, detail with
 * holder distribution, booking history, creation, rename and the close/reopen lifecycle. All gates
 * evaluate only bank roles and grants via {@code BankSecurityService} — org-unit scope has no
 * influence (REQ-BANK-008).
 */
@RestController
@RequestMapping("/api/v1/bank/accounts")
@RequiredArgsConstructor
public class BankAccountController {

  private static final Set<String> ACCOUNT_SORT_FIELDS =
      Set.of("accountNo", "name", "type", "status", "id");
  private static final Set<String> BOOKING_SORT_FIELDS = Set.of("createdAt", "id");

  private final BankAccountService bankAccountService;
  private final BankSecurityService bankSecurityService;
  private final AuthHelperService authHelperService;
  private final BankStatementReportService bankStatementReportService;

  /**
   * Pages over the accounts visible to the caller: management/admin see all, employees their
   * granted accounts (REQ-BANK-010).
   *
   * @param page zero-based page index
   * @param size page size
   * @param sort whitelisted sort spec ({@code field,asc|desc})
   * @return one page of accounts incl. compute-on-read balances
   */
  @Operation(summary = "List the bank accounts visible to the caller (paged)")
  @GetMapping
  @PreAuthorize("hasRole('BANK_EMPLOYEE')")
  @Transactional(readOnly = true)
  public PageResponse<BankAccountDto> getAccounts(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    Pageable pageable =
        PaginationUtil.createPageRequest(page, size, sort, ACCOUNT_SORT_FIELDS, "accountNo");
    Page<BankAccountDto> result =
        bankAccountService.getAccounts(
            bankSecurityService.isManagement(), currentUserId(), pageable);
    return toPageResponse(result);
  }

  /**
   * Creates an account of any type at runtime — including the two singletons (REQ-BANK-002).
   *
   * @param request validated creation payload
   * @return the created account
   */
  @Operation(summary = "Create a bank account (management)")
  @PostMapping
  @PreAuthorize("hasRole('BANK_MANAGEMENT')")
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  public BankAccountDto createAccount(@RequestBody @Valid CreateBankAccountRequest request) {
    return bankAccountService.createAccount(request);
  }

  /**
   * Loads one account's detail aggregate: balance, 30-day delta, facts and the holder distribution
   * (REQ-BANK-003).
   *
   * @param id the account
   * @param authentication the caller's authentication, used to evaluate the capability flags
   * @return the detail payload
   */
  @Operation(summary = "Read one bank account incl. holder distribution")
  @GetMapping("/{id}")
  @PreAuthorize("@bankSecurityService.canSee(#id, authentication)")
  @Transactional(readOnly = true)
  public BankAccountDetailDto getAccount(
      @PathVariable @NotNull UUID id, Authentication authentication) {
    BankCapabilitiesDto capabilities =
        new BankCapabilitiesDto(
            bankSecurityService.canDeposit(id, authentication),
            bankSecurityService.canWithdraw(id, authentication),
            bankSecurityService.canTransfer(id, authentication),
            bankSecurityService.isManagement());
    return bankAccountService.getAccountDetail(id, capabilities);
  }

  /**
   * Renames an account (REQ-BANK-001).
   *
   * @param id the account
   * @param request the new name plus echoed version
   * @return the updated account
   */
  @Operation(summary = "Rename a bank account (management)")
  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('BANK_MANAGEMENT')")
  @Transactional
  public BankAccountDto renameAccount(
      @PathVariable @NotNull UUID id, @RequestBody @Valid RenameBankAccountRequest request) {
    return bankAccountService.renameAccount(id, request);
  }

  /**
   * Closes an account; requires a zero balance (REQ-BANK-002).
   *
   * @param id the account
   * @param request the echoed version
   * @return the updated account
   */
  @Operation(summary = "Close a bank account (management; zero balance required)")
  @PostMapping("/{id}/close")
  @PreAuthorize("hasRole('BANK_MANAGEMENT')")
  @Transactional
  public BankAccountDto closeAccount(
      @PathVariable @NotNull UUID id, @RequestBody @Valid BankAccountLifecycleRequest request) {
    return bankAccountService.closeAccount(id, request);
  }

  /**
   * Reopens a closed account (REQ-BANK-002).
   *
   * @param id the account
   * @param request the echoed version
   * @return the updated account
   */
  @Operation(summary = "Reopen a closed bank account (management)")
  @PostMapping("/{id}/reopen")
  @PreAuthorize("hasRole('BANK_MANAGEMENT')")
  @Transactional
  public BankAccountDto reopenAccount(
      @PathVariable @NotNull UUID id, @RequestBody @Valid BankAccountLifecycleRequest request) {
    return bankAccountService.reopenAccount(id, request);
  }

  /**
   * Reads the account's per-holder sub-balances (REQ-BANK-003).
   *
   * @param id the account
   * @return the holder distribution, largest stash first
   */
  @Operation(summary = "Read the holder distribution of a bank account")
  @GetMapping("/{id}/holders")
  @PreAuthorize("@bankSecurityService.canSee(#id, authentication)")
  @Transactional(readOnly = true)
  public List<BankHolderBalanceDto> getHolderDistribution(@PathVariable @NotNull UUID id) {
    return bankAccountService.getHolderDistribution(id);
  }

  /**
   * Pages over the account's booking history, newest first by default.
   *
   * @param id the account
   * @param page zero-based page index
   * @param size page size
   * @param sort whitelisted sort spec
   * @return one page of booking rows incl. transfer counter-legs
   */
  @Operation(summary = "List the booking history of a bank account (paged)")
  @GetMapping("/{id}/transactions")
  @PreAuthorize("@bankSecurityService.canSee(#id, authentication)")
  @Transactional(readOnly = true)
  public PageResponse<BankBookingDto> getBookings(
      @PathVariable @NotNull UUID id,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    String effectiveSort = sort == null || sort.isBlank() ? "createdAt,desc" : sort;
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page, size, effectiveSort, BOOKING_SORT_FIELDS, "createdAt");
    return toPageResponse(bankAccountService.getBookings(id, pageable));
  }

  /**
   * Renders the account statement PDF for a caller-chosen period (REQ-BANK-014): opening balance,
   * chronological postings with running balance and holder, closing balance and the closing holder
   * distribution. The optional {@code X-User-Time-Zone} header overrides UTC for the document
   * timestamps; an invalid IANA zone is silently dropped. Each export is audit-logged.
   *
   * @param id the account
   * @param from period start (inclusive, ISO-8601 instant)
   * @param to period end (inclusive, ISO-8601 instant)
   * @param userTimeZone IANA zone (e.g. {@code Europe/Berlin}); optional
   * @return PDF body with {@code application/pdf} and attachment Content-Disposition
   */
  @Operation(summary = "Download the account statement PDF for a period")
  @GetMapping("/{id}/statement")
  @PreAuthorize("@bankSecurityService.canSee(#id, authentication)")
  public ResponseEntity<byte[]> downloadStatement(
      @PathVariable @NotNull UUID id,
      @RequestParam @NotNull Instant from,
      @RequestParam @NotNull Instant to,
      @RequestHeader(value = "X-User-Time-Zone", required = false) String userTimeZone) {
    byte[] pdf = bankStatementReportService.generateStatement(id, from, to, parse(userTimeZone));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDispositionFormData("attachment", "kontoauszug-" + id + ".pdf");
    return ResponseEntity.ok().headers(headers).body(pdf);
  }

  /**
   * Parses the {@code X-User-Time-Zone} header value, silently dropping invalid IANA zones (the
   * report services fall back to UTC).
   *
   * @param userTimeZone the raw header value; may be {@code null} or blank
   * @return the parsed zone or {@code null}
   */
  static ZoneId parse(String userTimeZone) {
    if (userTimeZone == null || userTimeZone.isBlank()) {
      return null;
    }
    try {
      return ZoneId.of(userTimeZone);
    } catch (DateTimeException ex) {
      return null;
    }
  }

  /**
   * Resolves the caller's user id; bank URLs require authentication, so absence is a hard error.
   *
   * @return the caller's user id
   */
  private UUID currentUserId() {
    return authHelperService
        .currentUserId()
        .orElseThrow(() -> new AccessDeniedException("Authentication required"));
  }

  /**
   * Wraps a Spring page into the project-wide {@link PageResponse} envelope.
   *
   * @param page the mapped page
   * @param <T> the payload type
   * @return the response envelope
   */
  private static <T> PageResponse<T> toPageResponse(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        PaginationUtil.toSortStrings(page.getSort()));
  }
}

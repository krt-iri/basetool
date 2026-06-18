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

import de.greluc.krt.profit.basetool.backend.model.dto.BankHolderDto;
import de.greluc.krt.profit.basetool.backend.model.dto.request.RegisterBankHolderRequest;
import de.greluc.krt.profit.basetool.backend.model.dto.request.UpdateBankHolderRequest;
import de.greluc.krt.profit.basetool.backend.service.BankHolderService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the bank-local holder registry (epic #556, REQ-BANK-003): listing with custody
 * totals, registration via the user lookup, and activity toggling. Reads are open to all bank staff
 * (holders appear in every booking modal); writes are management-only.
 */
@RestController
@RequestMapping("/api/v1/bank/holders")
@RequiredArgsConstructor
public class BankHolderController {

  private final BankHolderService bankHolderService;

  /**
   * Lists the full holder registry with cross-account custody totals.
   *
   * @return every holder row, ordered by handle
   */
  @Operation(summary = "List the bank holder registry")
  @GetMapping
  @PreAuthorize("hasRole('BANK_EMPLOYEE')")
  @Transactional(readOnly = true)
  public List<BankHolderDto> getHolders() {
    return bankHolderService.getHolders();
  }

  /**
   * Registers a basetool user as holder (REQ-BANK-003).
   *
   * @param request the user to register
   * @return the created holder row
   */
  @Operation(summary = "Register a user as bank holder (management)")
  @PostMapping
  @PreAuthorize("hasRole('BANK_MANAGEMENT')")
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  public BankHolderDto registerHolder(@RequestBody @Valid RegisterBankHolderRequest request) {
    return bankHolderService.registerHolder(request);
  }

  /**
   * Toggles a holder's active flag (REQ-BANK-003).
   *
   * @param id the holder row
   * @param request the new flag plus echoed version
   * @return the updated holder row
   */
  @Operation(summary = "Activate or deactivate a bank holder (management)")
  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('BANK_MANAGEMENT')")
  @Transactional
  public BankHolderDto updateHolder(
      @PathVariable @NotNull UUID id, @RequestBody @Valid UpdateBankHolderRequest request) {
    return bankHolderService.updateHolder(id, request);
  }
}

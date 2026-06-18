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

import de.greluc.krt.profit.basetool.backend.service.BankManagementReportService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Management-level bank export surface (epic #556 Phase 3): the rolling three-month report over all
 * accounts (REQ-BANK-015). Statement exports live on the account resource ({@code
 * BankAccountController}); this controller carries the account-spanning documents.
 */
@RestController
@RequestMapping("/api/v1/bank/export")
@RequiredArgsConstructor
public class BankExportController {

  private final BankManagementReportService bankManagementReportService;

  /**
   * Renders the three-month report PDF: every account with summary, itemized bookings and closing
   * holder distribution over the rolling window ending now. The optional {@code X-User-Time-Zone}
   * header overrides UTC for the document timestamps; an invalid IANA zone is silently dropped.
   * Each export is audit-logged.
   *
   * @param userTimeZone IANA zone (e.g. {@code Europe/Berlin}); optional
   * @return PDF body with {@code application/pdf} and attachment Content-Disposition
   */
  @Operation(summary = "Download the management three-month report PDF")
  @GetMapping("/three-month-report")
  @PreAuthorize("hasRole('BANK_MANAGEMENT')")
  public ResponseEntity<byte[]> downloadThreeMonthReport(
      @RequestHeader(value = "X-User-Time-Zone", required = false) String userTimeZone) {
    byte[] pdf =
        bankManagementReportService.generateThreeMonthReport(
            BankAccountController.parse(userTimeZone));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDispositionFormData("attachment", "bank-3-monats-report.pdf");
    return ResponseEntity.ok().headers(headers).body(pdf);
  }
}

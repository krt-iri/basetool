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

package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.BankAccount;
import de.greluc.krt.iri.basetool.backend.model.BankAccountStatus;
import de.greluc.krt.iri.basetool.backend.model.BankAccountType;
import de.greluc.krt.iri.basetool.backend.model.BankHolder;
import de.greluc.krt.iri.basetool.backend.model.dto.request.BankDepositRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.request.BankWithdrawalRequest;
import de.greluc.krt.iri.basetool.backend.repository.BankAccountRepository;
import de.greluc.krt.iri.basetool.backend.repository.BankAuditEventRepository;
import de.greluc.krt.iri.basetool.backend.repository.BankHolderRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for the two bank PDF exports against the real Testcontainers PostgreSQL:
 * statement balance math, period filtering, holder-distribution section and audit events
 * (REQ-BANK-014), and the three-month report's per-account summaries (REQ-BANK-015). Content is
 * asserted through {@code PdfTextExtractor} — the same channel the handover regression tests use.
 */
@SpringBootTest
@ActiveProfiles("test")
class BankReportServiceTest {

  @Autowired private BankStatementReportService statementService;
  @Autowired private BankManagementReportService managementReportService;
  @Autowired private BankLedgerService bankLedgerService;
  @Autowired private BankAccountRepository accountRepository;
  @Autowired private BankHolderRepository holderRepository;
  @Autowired private BankAuditEventRepository auditEventRepository;

  private BankAccount account;
  private BankHolder holder;
  private String holderHandle;

  /** Seeds a fresh account and holder per test (unique names per run). */
  @BeforeEach
  void seed() {
    account = newAccount("Report Konto " + UUID.randomUUID());
    holderHandle = "report-holder-" + UUID.randomUUID();
    holder = newHolder(holderHandle);
  }

  @Test
  void statement_containsBalancesRunningColumnAndDistribution() throws IOException {
    // Given
    Instant before = Instant.now().minus(1, ChronoUnit.HOURS);
    deposit("500");
    withdraw("200");
    Instant after = Instant.now().plus(1, ChronoUnit.HOURS);

    // When
    byte[] pdf = statementService.generateStatement(account.getId(), before, after, null);

    // Then
    String text = extractText(pdf);
    assertTrue(text.contains("KONTOAUSZUG"), "title present");
    assertTrue(text.contains(account.getAccountNo()), "account number present");
    assertTrue(text.contains("ANFANGSSALDO"), "opening label present");
    assertTrue(text.contains("ENDSALDO"), "closing label present");
    assertTrue(text.contains("300 aUEC"), "closing balance 300 present");
    assertTrue(text.contains("+500"), "deposit amount present");
    assertTrue(text.contains("-200"), "withdrawal amount present");
    assertTrue(text.contains("Einzahlung"), "deposit type label present");
    assertTrue(text.contains("Auszahlung"), "withdrawal type label present");
    assertTrue(text.contains(holderHandle), "holder handle present");
    assertTrue(text.contains("HALTER-VERTEILUNG ZUM STICHTAG"), "distribution section present");
  }

  @Test
  void statement_appliesPeriodFilterToBothDirections() throws IOException, InterruptedException {
    // Given: a deposit strictly before tMid, a withdrawal strictly after
    Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
    deposit("500");
    Thread.sleep(75);
    Instant mid = Instant.now();
    Thread.sleep(75);
    withdraw("200");
    Instant end = Instant.now().plus(1, ChronoUnit.HOURS);

    // When
    String firstHalf =
        extractText(statementService.generateStatement(account.getId(), start, mid, null));
    String secondHalf =
        extractText(statementService.generateStatement(account.getId(), mid, end, null));

    // Then: first half sees only the deposit (opening 0 -> closing 500)
    assertTrue(firstHalf.contains("+500"), "deposit inside first period");
    assertFalse(firstHalf.contains("-200"), "withdrawal outside first period");
    assertTrue(firstHalf.contains("500 aUEC"), "closing 500 in first period");
    // ... and the second half only the withdrawal on an opening of 500
    assertFalse(secondHalf.contains("+500"), "deposit outside second period");
    assertTrue(secondHalf.contains("-200"), "withdrawal inside second period");
    assertTrue(secondHalf.contains("300 aUEC"), "closing 300 in second period");
  }

  @Test
  void statement_emptyPeriodShowsEmptyRowAndZeroMovement() throws IOException {
    // Given: bookings exist, but the period ends before them
    deposit("500");
    Instant farPast = Instant.now().minus(48, ChronoUnit.HOURS);
    Instant past = Instant.now().minus(24, ChronoUnit.HOURS);

    // When
    String text =
        extractText(statementService.generateStatement(account.getId(), farPast, past, null));

    // Then
    assertTrue(text.contains("Keine Buchungen im Zeitraum."), "empty row present");
    assertFalse(text.contains("+500"), "no booking row leaks into the period");
  }

  @Test
  void statement_recordsOneAuditEventPerExport() {
    // Given
    deposit("100");
    long before = auditEventRepository.count();

    // When
    statementService.generateStatement(
        account.getId(),
        Instant.now().minus(1, ChronoUnit.HOURS),
        Instant.now().plus(1, ChronoUnit.HOURS),
        null);

    // Then
    assertEquals(before + 1, auditEventRepository.count(), "exactly one STATEMENT_EXPORTED row");
  }

  @Test
  void statement_rejectsInvertedPeriodAndUnknownAccount() {
    // Given
    Instant now = Instant.now();

    // When / Then
    assertThrows(
        BadRequestException.class,
        () ->
            statementService.generateStatement(
                account.getId(), now, now.minus(1, ChronoUnit.HOURS), null));
    assertThrows(
        NotFoundException.class,
        () ->
            statementService.generateStatement(
                UUID.randomUUID(), now.minus(1, ChronoUnit.HOURS), now, null));
  }

  @Test
  void threeMonthReport_containsAccountSummariesAndAuditEvent() throws IOException {
    // Given
    deposit("750");
    long auditBefore = auditEventRepository.count();

    // When
    byte[] pdf = managementReportService.generateThreeMonthReport(null);

    // Then
    String text = extractText(pdf);
    assertTrue(text.contains("3-MONATS-REPORT"), "title present");
    assertTrue(text.contains(account.getAccountNo()), "seeded account section present");
    assertTrue(text.contains("Anfangssaldo"), "summary opening label present");
    assertTrue(text.contains("Eingänge"), "summary inflow label present");
    assertTrue(text.contains("Endsaldo"), "summary closing label present");
    assertTrue(text.contains("+750"), "itemized booking present");
    assertTrue(text.contains(holderHandle), "closing distribution names the holder");
    assertEquals(
        auditBefore + 1, auditEventRepository.count(), "exactly one MANAGEMENT_REPORT_EXPORTED");
  }

  /**
   * Extracts the text of every page of the given PDF.
   *
   * @param pdf the PDF bytes
   * @return the concatenated page texts
   * @throws IOException when the PDF cannot be parsed
   */
  private static String extractText(byte[] pdf) throws IOException {
    assertNotNull(pdf);
    assertTrue(pdf.length > 0, "PDF must not be empty");
    PdfReader reader = new PdfReader(pdf);
    try {
      PdfTextExtractor extractor = new PdfTextExtractor(reader);
      StringBuilder sb = new StringBuilder();
      for (int i = 1; i <= reader.getNumberOfPages(); i++) {
        sb.append(extractor.getTextFromPage(i)).append('\n');
      }
      return sb.toString();
    } finally {
      reader.close();
    }
  }

  private void deposit(String amount) {
    bankLedgerService.bookDeposit(
        new BankDepositRequest(account.getId(), holder.getId(), new BigDecimal(amount), null));
  }

  private void withdraw(String amount) {
    bankLedgerService.bookWithdrawal(
        new BankWithdrawalRequest(account.getId(), holder.getId(), new BigDecimal(amount), null));
  }

  private BankAccount newAccount(String name) {
    BankAccount a = new BankAccount();
    a.setAccountNo(String.format("KB-%04d", accountRepository.nextAccountNoValue()));
    a.setName(name);
    a.setType(BankAccountType.SPECIAL);
    a.setStatus(BankAccountStatus.ACTIVE);
    BankAccount saved = accountRepository.save(a);
    assertNotNull(saved.getId());
    return saved;
  }

  private BankHolder newHolder(String handle) {
    BankHolder h = new BankHolder();
    h.setHandle(handle);
    h.setActive(true);
    return holderRepository.save(h);
  }
}

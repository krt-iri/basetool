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

package de.greluc.krt.profit.basetool.backend.service;

import de.greluc.krt.profit.basetool.backend.exception.ReportGenerationException;
import de.greluc.krt.profit.basetool.backend.model.BankAccount;
import de.greluc.krt.profit.basetool.backend.model.BankAuditEventType;
import de.greluc.krt.profit.basetool.backend.model.projection.BankBookingRow;
import de.greluc.krt.profit.basetool.backend.model.projection.BankHolderBalance;
import de.greluc.krt.profit.basetool.backend.repository.BankAccountRepository;
import de.greluc.krt.profit.basetool.backend.repository.BankPostingRepository;
import de.greluc.krt.profit.basetool.backend.service.pdf.BankBalanceChart;
import de.greluc.krt.profit.basetool.backend.service.pdf.BankPdfFormat;
import de.greluc.krt.profit.basetool.backend.service.pdf.KrtPdfSupport;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfPTable;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renders the management three-month report PDF (REQ-BANK-015, epic #556 Phase 3): every bank
 * account over the rolling three-month window ending now, each with a summary block (opening
 * balance, inflow, outflow, closing balance), the itemized bookings of the window and the closing
 * holder distribution. Management-only at the endpoint gate; each export writes one {@code
 * MANAGEMENT_REPORT_EXPORTED} audit event (REQ-BANK-012).
 *
 * <p>Labels are German from the backend message bundle; the visual layer is the shared {@link
 * KrtPdfSupport}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankManagementReportService {

  /** Timestamp pattern for booking rows and meta lines; zone bound per request. */
  private static final DateTimeFormatter DATE_TIME_PATTERN =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  /** Date-only pattern for the balance chart's compact x-axis labels; zone bound per request. */
  private static final DateTimeFormatter DATE_ONLY_PATTERN =
      DateTimeFormatter.ofPattern("dd.MM.yy");

  private final BankAccountRepository bankAccountRepository;
  private final BankPostingRepository bankPostingRepository;
  private final BankAuditService bankAuditService;
  private final MessageSource messageSource;

  /**
   * Generates the three-month report over all accounts and records the export in the audit log.
   * Write transaction on purpose: the audit insert runs {@code MANDATORY} inside it.
   *
   * @param userZone the zone to render timestamps in; {@code null} falls back to UTC
   * @return the PDF bytes
   */
  @Transactional
  public byte @NotNull [] generateThreeMonthReport(@Nullable ZoneId userZone) {
    Instant to = Instant.now();
    Instant from = ZonedDateTime.ofInstant(to, ZoneOffset.UTC).minusMonths(3).toInstant();
    List<BankAccount> accounts = bankAccountRepository.findAllByOrderByAccountNoAsc();

    byte[] pdf = buildPdf(accounts, from, to, userZone);
    bankAuditService.record(
        BankAuditEventType.MANAGEMENT_REPORT_EXPORTED,
        null,
        null,
        null,
        "period=" + from + ".." + to + ", accounts=" + accounts.size());
    log.info("Bank three-month report exported ({} accounts)", accounts.size());
    return pdf;
  }

  private byte @NotNull [] buildPdf(
      @NotNull List<BankAccount> accounts,
      @NotNull Instant from,
      @NotNull Instant to,
      @Nullable ZoneId userZone) {
    ZoneId zone = userZone != null ? userZone : ZoneOffset.UTC;
    DateTimeFormatter stamp = DATE_TIME_PATTERN.withZone(zone);
    DateTimeFormatter dateOnly = DATE_ONLY_PATTERN.withZone(zone);

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      KrtPdfSupport.KrtDocument krt = KrtPdfSupport.open(baos);

      KrtPdfSupport.addTitle(krt, label("pdf.bank.report.title"));

      PdfPTable meta = KrtPdfSupport.newMetaTable();
      KrtPdfSupport.addMetaRow(
          meta, label("pdf.bank.statement.period"), stamp.format(from) + " – " + stamp.format(to));
      KrtPdfSupport.addMetaRow(
          meta, label("pdf.bank.statement.generated"), stamp.format(Instant.now()));
      krt.document().add(meta);

      if (accounts.isEmpty()) {
        Paragraph empty =
            new Paragraph(
                label("pdf.bank.report.empty"),
                KrtPdfSupport.italic(10, KrtPdfSupport.COLOR_LIGHT_GRAY));
        krt.document().add(empty);
      }

      // Each account starts on its own page so a section never shares a page with the previous
      // account's tail (REQ-BANK-015 readability); the first follows the title/period meta.
      boolean firstAccount = true;
      for (BankAccount account : accounts) {
        if (!firstAccount) {
          krt.document().newPage();
        }
        addAccountSection(krt, account, from, to, stamp, dateOnly);
        firstAccount = false;
      }

      KrtPdfSupport.addFooter(krt);
      krt.document().close();
      return baos.toByteArray();
    } catch (Exception e) {
      throw new ReportGenerationException("PDF generation failed", e);
    }
  }

  /**
   * Adds one account's report section: header, summary block, itemized bookings and the closing
   * holder distribution.
   *
   * @param krt the open document handle
   * @param account the account to render
   * @param from window start (inclusive)
   * @param to window end (inclusive)
   * @param stamp the zone-bound timestamp formatter
   * @param dateOnly the zone-bound date-only formatter for the chart's x-axis labels
   */
  private void addAccountSection(
      @NotNull KrtPdfSupport.KrtDocument krt,
      @NotNull BankAccount account,
      @NotNull Instant from,
      @NotNull Instant to,
      @NotNull DateTimeFormatter stamp,
      @NotNull DateTimeFormatter dateOnly) {
    BigDecimal opening = bankPostingRepository.accountBalanceBefore(account.getId(), from);
    List<BankBookingRow> rows =
        bankPostingRepository.findBookingsInPeriod(account.getId(), from, to);

    BigDecimal inflow = BigDecimal.ZERO;
    BigDecimal outflow = BigDecimal.ZERO;
    for (BankBookingRow row : rows) {
      if (row.amount().signum() > 0) {
        inflow = inflow.add(row.amount());
      } else {
        outflow = outflow.add(row.amount());
      }
    }
    final BigDecimal closing = opening.add(inflow).add(outflow);
    krt.document().add(new Paragraph(" "));
    KrtPdfSupport.addSectionHeader(
        krt,
        label("pdf.bank.statement.account")
            + " "
            + account.getAccountNo()
            + " — "
            + account.getName());

    PdfPTable summary = KrtPdfSupport.newMetaTable();
    KrtPdfSupport.addMetaRow(
        summary, label("pdf.bank.report.opening"), BankPdfFormat.amount(opening));
    KrtPdfSupport.addMetaRow(
        summary, label("pdf.bank.report.inflow"), BankPdfFormat.signedAmount(inflow));
    KrtPdfSupport.addMetaRow(
        summary, label("pdf.bank.report.outflow"), BankPdfFormat.signedAmount(outflow));
    KrtPdfSupport.addMetaRow(
        summary, label("pdf.bank.report.closing"), BankPdfFormat.amount(closing));
    krt.document().add(summary);

    // Balance-over-time chart: the running balance across the three-month window as a step line.
    KrtPdfSupport.addSectionHeader(krt, label("pdf.bank.report.chart"));
    krt.document()
        .add(
            BankBalanceChart.render(
                krt.writer(), opening, rows, from, to, dateOnly.format(from), dateOnly.format(to)));
    krt.document().add(new Paragraph(" "));

    PdfPTable table = new PdfPTable(5);
    table.setWidthPercentage(100);
    table.setWidths(new float[] {1.6f, 1.3f, 1.5f, 2.2f, 1.3f});
    KrtPdfSupport.addTableHeader(table, label("pdf.bank.col.date"));
    KrtPdfSupport.addTableHeader(table, label("pdf.bank.col.type"));
    KrtPdfSupport.addTableHeader(table, label("pdf.bank.col.holder"));
    KrtPdfSupport.addTableHeader(table, label("pdf.bank.col.note"));
    KrtPdfSupport.addTableHeader(table, label("pdf.bank.col.amount"));

    boolean alt = false;
    for (BankBookingRow row : rows) {
      Color bg = KrtPdfSupport.rowBackground(alt);
      KrtPdfSupport.addTableCell(table, stamp.format(row.createdAt()), bg, false);
      KrtPdfSupport.addTableCell(table, label("pdf.bank.type." + row.type().name()), bg, false);
      KrtPdfSupport.addTableCell(table, row.holderHandle(), bg, false);
      KrtPdfSupport.addTableCell(table, row.note() != null ? row.note() : "", bg, false);
      KrtPdfSupport.addTableCell(table, BankPdfFormat.signedAmount(row.amount()), bg, true);
      alt = !alt;
    }
    if (rows.isEmpty()) {
      KrtPdfSupport.addEmptyRow(table, label("pdf.bank.statement.empty"), 5);
    }
    krt.document().add(table);

    krt.document().add(new Paragraph(" "));
    KrtPdfSupport.addSectionHeader(krt, label("pdf.bank.distribution.title"));
    List<BankHolderBalance> distribution =
        bankPostingRepository.holderDistributionUntil(account.getId(), to);
    krt.document().add(BankPdfFormat.distributionTable(distribution, this::label));
  }

  /**
   * Resolves one German PDF label from the backend message bundle.
   *
   * @param key the message key
   * @return the resolved label
   */
  private @NotNull String label(@NotNull String key) {
    return messageSource.getMessage(key, null, Locale.GERMAN);
  }
}

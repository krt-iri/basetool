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

import de.greluc.krt.profit.basetool.backend.exception.BadRequestException;
import de.greluc.krt.profit.basetool.backend.exception.NotFoundException;
import de.greluc.krt.profit.basetool.backend.exception.ReportGenerationException;
import de.greluc.krt.profit.basetool.backend.model.BankAccount;
import de.greluc.krt.profit.basetool.backend.model.BankAuditEventType;
import de.greluc.krt.profit.basetool.backend.model.BankTransactionType;
import de.greluc.krt.profit.basetool.backend.model.projection.BankBookingRow;
import de.greluc.krt.profit.basetool.backend.model.projection.BankHolderLeg;
import de.greluc.krt.profit.basetool.backend.repository.BankAccountRepository;
import de.greluc.krt.profit.basetool.backend.repository.BankHolderPostingRepository;
import de.greluc.krt.profit.basetool.backend.repository.BankPostingRepository;
import de.greluc.krt.profit.basetool.backend.service.pdf.BankPdfFormat;
import de.greluc.krt.profit.basetool.backend.service.pdf.KrtPdfSupport;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openpdf.text.pdf.PdfPTable;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renders the bank account statement PDF (REQ-BANK-014, epic #556 Phase 3): for a caller-chosen
 * period it shows the opening balance, every posting of the account in chronological order with a
 * running balance and the booking holder (derived from the holder ledger by amount sign, ADR-0039),
 * and the closing balance. Since holders are decoupled from accounts there is no per-account
 * closing holder distribution any more (REQ-BANK-003). Statements are computed on demand from the
 * append-only ledger and never persisted (owner decision on spec question 4); each export writes
 * one {@code STATEMENT_EXPORTED} audit event carrying the period (REQ-BANK-012).
 *
 * <p>Labels come from the backend message bundle and are German by design — the documents are
 * org-internal DAS KARTELL paperwork. The visual layer (Lato, dark background, orange accents) is
 * the shared {@link KrtPdfSupport}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankStatementReportService {

  /** Timestamp pattern for booking rows and the generated-at line; zone bound per request. */
  private static final DateTimeFormatter DATE_TIME_PATTERN =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  private final BankAccountRepository bankAccountRepository;
  private final BankPostingRepository bankPostingRepository;
  private final BankHolderPostingRepository bankHolderPostingRepository;
  private final BankAuditService bankAuditService;
  private final MessageSource messageSource;

  /**
   * Generates the statement PDF for one account and period and records the export in the audit log.
   * Write transaction on purpose: the audit insert runs {@code MANDATORY} inside it.
   *
   * @param accountId the account
   * @param from period start (inclusive)
   * @param to period end (inclusive); must not be before {@code from}
   * @param userZone the zone to render timestamps in; {@code null} falls back to UTC
   * @return the PDF bytes
   * @throws NotFoundException when the account is unknown
   * @throws BadRequestException when the period is inverted
   */
  @Transactional
  public byte @NotNull [] generateStatement(
      @NotNull UUID accountId,
      @NotNull Instant from,
      @NotNull Instant to,
      @Nullable ZoneId userZone) {
    BankAccount account =
        bankAccountRepository
            .findById(accountId)
            .orElseThrow(() -> new NotFoundException("Bank account not found"));
    if (from.isAfter(to)) {
      throw new BadRequestException("Statement period start must not be after its end");
    }

    BigDecimal opening = bankPostingRepository.accountBalanceBefore(accountId, from);
    List<BankBookingRow> rows = bankPostingRepository.findBookingsInPeriod(accountId, from, to);
    List<UUID> txIds = rows.stream().map(BankBookingRow::transactionId).distinct().toList();
    Map<UUID, List<BankHolderLeg>> holderLegsByTx =
        txIds.isEmpty()
            ? Map.of()
            : bankHolderPostingRepository.findHolderLegsByTransactionIds(txIds).stream()
                .collect(Collectors.groupingBy(BankHolderLeg::transactionId));

    byte[] pdf = buildPdf(account, from, to, opening, rows, holderLegsByTx, userZone);
    bankAuditService.record(
        BankAuditEventType.STATEMENT_EXPORTED, accountId, null, null, "period=" + from + ".." + to);
    log.info(
        "Bank statement exported for account {} ({} rows)", account.getAccountNo(), rows.size());
    return pdf;
  }

  private byte @NotNull [] buildPdf(
      @NotNull BankAccount account,
      @NotNull Instant from,
      @NotNull Instant to,
      @NotNull BigDecimal opening,
      @NotNull List<BankBookingRow> rows,
      @NotNull Map<UUID, List<BankHolderLeg>> holderLegsByTx,
      @Nullable ZoneId userZone) {
    ZoneId zone = userZone != null ? userZone : ZoneOffset.UTC;
    DateTimeFormatter stamp = DATE_TIME_PATTERN.withZone(zone);

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      KrtPdfSupport.KrtDocument krt = KrtPdfSupport.open(baos);

      KrtPdfSupport.addTitle(krt, label("pdf.bank.statement.title"));

      BigDecimal closing =
          rows.stream().map(BankBookingRow::amount).reduce(opening, BigDecimal::add);

      PdfPTable meta = KrtPdfSupport.newMetaTable();
      KrtPdfSupport.addMetaRow(
          meta,
          label("pdf.bank.statement.account"),
          account.getAccountNo() + " — " + account.getName());
      KrtPdfSupport.addMetaRow(
          meta, label("pdf.bank.statement.period"), stamp.format(from) + " – " + stamp.format(to));
      KrtPdfSupport.addMetaRow(
          meta, label("pdf.bank.statement.generated"), stamp.format(Instant.now()));
      KrtPdfSupport.addMetaRow(
          meta, label("pdf.bank.statement.openingBalance"), BankPdfFormat.amount(opening));
      KrtPdfSupport.addMetaRow(
          meta, label("pdf.bank.statement.closingBalance"), BankPdfFormat.amount(closing));
      krt.document().add(meta);

      KrtPdfSupport.addSectionHeader(krt, label("pdf.bank.statement.bookings"));

      PdfPTable table = new PdfPTable(6);
      table.setWidthPercentage(100);
      table.setWidths(new float[] {1.6f, 1.3f, 1.5f, 2.2f, 1.3f, 1.4f});
      KrtPdfSupport.addTableHeader(table, label("pdf.bank.col.date"));
      KrtPdfSupport.addTableHeader(table, label("pdf.bank.col.type"));
      KrtPdfSupport.addTableHeader(table, label("pdf.bank.col.holder"));
      KrtPdfSupport.addTableHeader(table, label("pdf.bank.col.note"));
      KrtPdfSupport.addTableHeader(table, label("pdf.bank.col.amount"));
      KrtPdfSupport.addTableHeader(table, label("pdf.bank.col.balance"));

      boolean alt = false;
      BigDecimal running = opening;
      for (BankBookingRow row : rows) {
        running = running.add(row.amount());
        Color bg = KrtPdfSupport.rowBackground(alt);
        String holder =
            row.type() == BankTransactionType.WIPE_RESET
                ? ""
                : matchHolderHandle(
                    holderLegsByTx.getOrDefault(row.transactionId(), List.of()),
                    row.amount().signum());
        KrtPdfSupport.addTableCell(table, stamp.format(row.createdAt()), bg, false);
        KrtPdfSupport.addTableCell(table, label("pdf.bank.type." + row.type().name()), bg, false);
        KrtPdfSupport.addTableCell(table, holder, bg, false);
        KrtPdfSupport.addTableCell(table, row.note() != null ? row.note() : "", bg, false);
        KrtPdfSupport.addTableCell(table, BankPdfFormat.signedAmount(row.amount()), bg, true);
        KrtPdfSupport.addTableCell(table, BankPdfFormat.amount(running), bg, true);
        alt = !alt;
      }
      if (rows.isEmpty()) {
        KrtPdfSupport.addEmptyRow(table, label("pdf.bank.statement.empty"), 6);
      }
      krt.document().add(table);

      KrtPdfSupport.addFooter(krt);
      krt.document().close();
      return baos.toByteArray();
    } catch (BadRequestException | NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new ReportGenerationException("PDF generation failed", e);
    }
  }

  /**
   * Picks the handle of the holder leg whose amount sign matches a posting row's sign — the holder
   * paired with that account leg in the same transaction (ADR-0039); empty when none exists.
   *
   * @param holderLegs the transaction's holder legs
   * @param sign the wanted amount sign (+1 / -1)
   * @return the matching holder's handle, or an empty string
   */
  private static @NotNull String matchHolderHandle(
      @NotNull List<BankHolderLeg> holderLegs, int sign) {
    return holderLegs.stream()
        .filter(leg -> leg.amount().signum() == sign)
        .map(BankHolderLeg::handle)
        .findFirst()
        .orElse("");
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

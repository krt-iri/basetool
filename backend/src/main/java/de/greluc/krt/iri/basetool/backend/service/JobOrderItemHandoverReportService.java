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

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.exception.ReportGenerationException;
import de.greluc.krt.iri.basetool.backend.model.JobOrderItemHandover;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderItemHandoverRepository;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates the item-handover delivery-note PDF in KRT Corporate Design — the item-order
 * counterpart of {@link JobOrderHandoverReportService}. Renders the persisted handover (by id) with
 * the produced items and their whole-unit quantities. The persisted {@code handoverTime} is UTC and
 * is rendered in the caller's zone (from {@code X-User-Time-Zone}), falling back to UTC.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class JobOrderItemHandoverReportService {

  private static final Color COLOR_BLACK = new Color(0x00, 0x00, 0x00);
  private static final Color COLOR_DARK_GRAY = new Color(0x14, 0x14, 0x14);
  private static final Color COLOR_ORANGE = new Color(0xE7, 0x7E, 0x23);
  private static final Color COLOR_WHITE = new Color(0xFF, 0xFF, 0xFF);
  private static final Color COLOR_LIGHT_GRAY = new Color(0xCC, 0xCC, 0xCC);
  private static final Color COLOR_TABLE_ROW_ALT = new Color(0x1E, 0x1E, 0x1E);

  private static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final DateTimeFormatter TIME_PATTERN = DateTimeFormatter.ofPattern("HH:mm");

  private static final Comparator<ItemRow> ITEM_COMPARATOR =
      Comparator.comparing(ItemRow::itemName, String.CASE_INSENSITIVE_ORDER)
          .thenComparing(Comparator.comparingInt(ItemRow::amount).reversed());

  private final JobOrderItemHandoverRepository jobOrderItemHandoverRepository;

  /**
   * Generates the delivery-note PDF for a persisted item handover.
   *
   * @param jobOrderId the item order id (validated against the handover's parent)
   * @param handoverId the item-handover id
   * @param userZone the zone to render the handover time in; {@code null} falls back to UTC
   * @return the PDF as a byte array
   * @throws NotFoundException when the handover is unknown or belongs to a different order
   */
  public byte @NotNull [] generateItemHandoverReport(
      @NotNull UUID jobOrderId, @NotNull UUID handoverId, ZoneId userZone) {
    JobOrderItemHandover handover =
        jobOrderItemHandoverRepository
            .findById(handoverId)
            .orElseThrow(() -> new NotFoundException("Item handover not found"));

    if (!handover.getJobOrder().getId().equals(jobOrderId)) {
      throw new NotFoundException("Item handover does not belong to this job order");
    }

    ZoneId effectiveZone = userZone != null ? userZone : ZoneOffset.UTC;
    DateTimeFormatter dateFormatter = DATE_PATTERN.withZone(effectiveZone);
    DateTimeFormatter timeFormatter = TIME_PATTERN.withZone(effectiveZone);

    String jobOrderNumber = "#" + handover.getJobOrder().getDisplayId();
    String handoverDate = dateFormatter.format(handover.getHandoverTime());
    String handoverTime = timeFormatter.format(handover.getHandoverTime());

    List<ItemRow> rows =
        handover.getEntries().stream()
            .map(
                entry ->
                    new ItemRow(
                        entry.getJobOrderItem() != null
                                && entry.getJobOrderItem().getGameItem() != null
                            ? entry.getJobOrderItem().getGameItem().getName()
                            : "-",
                        entry.getAmount() == null ? 0 : entry.getAmount()))
            .sorted(ITEM_COMPARATOR)
            .toList();

    return buildPdf(
        jobOrderNumber, handoverDate, handoverTime, handover.getRecipientHandle(), rows);
  }

  private byte @NotNull [] buildPdf(
      @NotNull String jobOrderNumber,
      @NotNull String handoverDate,
      @NotNull String handoverTime,
      @NotNull String recipientHandle,
      @NotNull List<ItemRow> rows) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Document document = new Document(PageSize.A4, 40, 40, 60, 60);
      PdfWriter writer = PdfWriter.getInstance(document, baos);
      writer.setCompressionLevel(0);
      writer.setPageEvent(new KrtPageBackground());
      document.open();

      Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, COLOR_ORANGE);
      Paragraph title = new Paragraph("ÜBERGABEPROTOKOLL", titleFont);
      title.setAlignment(Element.ALIGN_LEFT);
      title.setSpacingAfter(4f);
      document.add(title);

      PdfContentByte cb = writer.getDirectContent();
      float ypos = writer.getVerticalPosition(false);
      cb.setColorStroke(COLOR_ORANGE);
      cb.setLineWidth(0.5f);
      cb.moveTo(40, ypos);
      cb.lineTo(PageSize.A4.getWidth() - 40, ypos);
      cb.stroke();

      document.add(new Paragraph(" ", new Font(Font.HELVETICA, 6)));

      PdfPTable metaTable = new PdfPTable(2);
      metaTable.setWidthPercentage(100);
      metaTable.setWidths(new float[] {1f, 2f});
      metaTable.setSpacingAfter(20f);
      addMetaRow(metaTable, "AUFTRAGSNUMMER", jobOrderNumber);
      addMetaRow(metaTable, "DATUM DER ÜBERGABE", handoverDate);
      addMetaRow(metaTable, "UHRZEIT DER ÜBERGABE", handoverTime + " (Lokalzeit)");
      addMetaRow(metaTable, "EMPFÄNGER (HANDLE)", recipientHandle);
      document.add(metaTable);

      Font sectionFont = new Font(Font.HELVETICA, 12, Font.BOLD, COLOR_ORANGE);
      Paragraph itemHeader = new Paragraph("ÜBERGEBENE GÜTER", sectionFont);
      itemHeader.setSpacingAfter(8f);
      document.add(itemHeader);

      PdfPTable itemTable = new PdfPTable(2);
      itemTable.setWidthPercentage(100);
      itemTable.setWidths(new float[] {4f, 1.5f});
      addTableHeader(itemTable, "GUT");
      addTableHeader(itemTable, "MENGE");

      boolean alt = false;
      for (ItemRow row : rows) {
        Color rowBg = alt ? COLOR_TABLE_ROW_ALT : COLOR_DARK_GRAY;
        addTableCell(itemTable, row.itemName(), rowBg, false);
        addTableCell(itemTable, String.valueOf(row.amount()), rowBg, true);
        alt = !alt;
      }
      if (rows.isEmpty()) {
        Font emptyFont = new Font(Font.HELVETICA, 10, Font.ITALIC, COLOR_LIGHT_GRAY);
        PdfPCell emptyCell = new PdfPCell(new Phrase("Keine Güter vorhanden.", emptyFont));
        emptyCell.setColspan(2);
        emptyCell.setBackgroundColor(COLOR_DARK_GRAY);
        emptyCell.setBorderColor(COLOR_ORANGE);
        emptyCell.setBorderWidth(0.3f);
        emptyCell.setPadding(8f);
        itemTable.addCell(emptyCell);
      }
      document.add(itemTable);

      document.add(new Paragraph(" ", new Font(Font.HELVETICA, 10)));
      Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL, COLOR_LIGHT_GRAY);
      Paragraph footer = new Paragraph("Generiert von Profit Basetool", footerFont);
      footer.setAlignment(Element.ALIGN_CENTER);
      document.add(footer);

      document.close();
      return baos.toByteArray();
    } catch (Exception e) {
      throw new ReportGenerationException("PDF generation failed", e);
    }
  }

  private void addMetaRow(@NotNull PdfPTable table, @NotNull String label, @NotNull String value) {
    Font labelFont = new Font(Font.HELVETICA, 9, Font.BOLD, COLOR_ORANGE);
    Font valueFont = new Font(Font.HELVETICA, 10, Font.NORMAL, COLOR_WHITE);
    PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
    labelCell.setBackgroundColor(COLOR_DARK_GRAY);
    labelCell.setBorder(Rectangle.NO_BORDER);
    labelCell.setPadding(6f);
    PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
    valueCell.setBackgroundColor(COLOR_DARK_GRAY);
    valueCell.setBorder(Rectangle.NO_BORDER);
    valueCell.setPadding(6f);
    table.addCell(labelCell);
    table.addCell(valueCell);
  }

  private void addTableHeader(@NotNull PdfPTable table, @NotNull String text) {
    Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD, COLOR_BLACK);
    PdfPCell cell = new PdfPCell(new Phrase(text, headerFont));
    cell.setBackgroundColor(COLOR_ORANGE);
    cell.setBorderColor(COLOR_ORANGE);
    cell.setBorderWidth(0.3f);
    cell.setPadding(7f);
    table.addCell(cell);
  }

  private void addTableCell(
      @NotNull PdfPTable table, @NotNull String text, @NotNull Color bg, boolean centered) {
    Font cellFont = new Font(Font.HELVETICA, 9, Font.NORMAL, COLOR_WHITE);
    PdfPCell cell = new PdfPCell(new Phrase(text, cellFont));
    cell.setBackgroundColor(bg);
    cell.setBorderColor(COLOR_ORANGE);
    cell.setBorderWidth(0.3f);
    cell.setPadding(6f);
    if (centered) {
      cell.setHorizontalAlignment(Element.ALIGN_CENTER);
    }
    table.addCell(cell);
  }

  /** Internal value record for one delivered-item row in the PDF. */
  private record ItemRow(String itemName, int amount) {}

  /**
   * PdfPageEvent painting the KRT corporate-design background (dark fill, orange accent bars, logo)
   * on every page, mirroring {@link JobOrderHandoverReportService}.
   */
  private static class KrtPageBackground extends PdfPageEventHelper {

    @Override
    public void onStartPage(PdfWriter writer, Document document) {
      PdfContentByte canvas = writer.getDirectContentUnder();
      canvas.setColorFill(COLOR_BLACK);
      canvas.rectangle(0, 0, PageSize.A4.getWidth(), PageSize.A4.getHeight());
      canvas.fill();
      canvas.setColorFill(COLOR_ORANGE);
      canvas.rectangle(0, PageSize.A4.getHeight() - 8, PageSize.A4.getWidth(), 8);
      canvas.fill();
      canvas.setColorFill(COLOR_ORANGE);
      canvas.rectangle(0, 0, PageSize.A4.getWidth(), 4);
      canvas.fill();
      canvas.setColorFill(COLOR_ORANGE);
      canvas.rectangle(0, 0, 4, PageSize.A4.getHeight());
      canvas.fill();
      try (InputStream logoStream =
          getClass().getClassLoader().getResourceAsStream("META-INF/resources/logos/krt.png")) {
        if (logoStream != null) {
          Image logo = Image.getInstance(logoStream.readAllBytes());
          float logoHeight = 60f;
          float scale = logoHeight / logo.getHeight();
          float logoWidth = logo.getWidth() * scale;
          float margin = 20f;
          logo.scaleAbsolute(logoWidth, logoHeight);
          logo.setAbsolutePosition(PageSize.A4.getWidth() - logoWidth - margin, margin + 4f);
          canvas.addImage(logo);
        } else {
          log.warn("krt.png not found in classpath");
        }
      } catch (Exception e) {
        log.warn("Failed to render logo on page background", e);
      }
    }
  }
}

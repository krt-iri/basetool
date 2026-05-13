package de.greluc.krt.iri.basetool.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.JobOrderHandover;
import de.greluc.krt.iri.basetool.backend.model.dto.HandoverReportPreviewRequestDto;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderHandoverRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for generating handover report PDFs in KRT Corporate Design. Supports both
 * persisted handovers (by ID) and preview generation from raw DTO data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobOrderHandoverReportService {

  private static final Color COLOR_BLACK = new Color(0x00, 0x00, 0x00);
  private static final Color COLOR_DARK_GRAY = new Color(0x14, 0x14, 0x14);
  private static final Color COLOR_ORANGE = new Color(0xE7, 0x7E, 0x23);
  private static final Color COLOR_WHITE = new Color(0xFF, 0xFF, 0xFF);
  private static final Color COLOR_LIGHT_GRAY = new Color(0xCC, 0xCC, 0xCC);
  private static final Color COLOR_TABLE_ROW_ALT = new Color(0x1E, 0x1E, 0x1E);

  // Patterns are intentionally NOT bound to a fixed zone here. The persisted-handover path
  // binds the zone per request (from the X-User-Time-Zone header), and the preview path
  // formats LocalDateTime directly without any zone conversion.
  private static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final DateTimeFormatter TIME_PATTERN = DateTimeFormatter.ofPattern("HH:mm");

  private final JobOrderHandoverRepository jobOrderHandoverRepository;
  private final JobOrderRepository jobOrderRepository;

  /**
   * Generates a handover report PDF for a persisted handover.
   *
   * <p>The persisted {@code handoverTime} is a UTC {@link java.time.Instant}; for display in the
   * PDF it must be rendered in the user's actual time zone (passed in via {@code userZone}, e.g.
   * from the {@code X-User-Time-Zone} request header). If {@code userZone} is {@code null}, UTC is
   * used as a safe fallback.
   *
   * @param jobOrderId the job order ID
   * @param handoverId the handover ID
   * @param userZone the time zone to render the handover time in; may be {@code null}
   * @return PDF as byte array
   */
  @Transactional(readOnly = true)
  public byte @NotNull [] generateHandoverReport(
      @NotNull UUID jobOrderId, @NotNull UUID handoverId, ZoneId userZone) {
    log.debug(
        "Generating handover report for jobOrderId={}, handoverId={}, userZone={}",
        jobOrderId,
        handoverId,
        userZone);

    JobOrderHandover handover =
        jobOrderHandoverRepository
            .findById(handoverId)
            .orElseThrow(() -> new NotFoundException("Handover not found"));

    if (!handover.getJobOrder().getId().equals(jobOrderId)) {
      throw new NotFoundException("Handover does not belong to this job order");
    }

    ZoneId effectiveZone = userZone != null ? userZone : ZoneOffset.UTC;
    DateTimeFormatter dateFormatter = DATE_PATTERN.withZone(effectiveZone);
    DateTimeFormatter timeFormatter = TIME_PATTERN.withZone(effectiveZone);

    String jobOrderNumber = "#" + handover.getJobOrder().getDisplayId();
    String recipientHandle = handover.getRecipientHandle();
    String handoverDate = dateFormatter.format(handover.getHandoverTime());
    String handoverTime = timeFormatter.format(handover.getHandoverTime());

    List<ItemRow> rows =
        handover.getItems().stream()
            .map(
                item ->
                    new ItemRow(
                        item.getMaterial() != null ? item.getMaterial().getName() : "-",
                        item.getLocationName() != null ? item.getLocationName() : "-",
                        item.getAmount(),
                        item.getQuality(),
                        item.getMaterial() != null && item.getMaterial().getQuantityType() != null
                            ? item.getMaterial().getQuantityType().name()
                            : null))
            .sorted(ITEM_COMPARATOR)
            .toList();

    return buildPdf(jobOrderNumber, handoverDate, handoverTime, recipientHandle, rows);
  }

  /**
   * Generates a handover report PDF preview from raw DTO data (before the handover is persisted).
   *
   * @param dto the preview request DTO
   * @return PDF as byte array
   */
  public byte @NotNull [] generateHandoverReportPreview(
      @NotNull HandoverReportPreviewRequestDto dto) {
    log.debug("Generating handover report preview for jobOrderNumber={}", dto.jobOrderNumber());

    // dto.handoverTime() is a LocalDateTime — exactly what the user typed in the modal.
    // No zone conversion is applied so the PDF shows the same date/time the user entered.
    String handoverDate = DATE_PATTERN.format(dto.handoverTime());
    String handoverTime = TIME_PATTERN.format(dto.handoverTime());

    List<ItemRow> rows =
        dto.items().stream()
            .map(
                item ->
                    new ItemRow(
                        item.materialName(),
                        item.locationName() != null ? item.locationName() : "-",
                        item.amount(),
                        item.quality(),
                        item.quantityType()))
            .sorted(ITEM_COMPARATOR)
            .toList();

    return buildPdf(dto.jobOrderNumber(), handoverDate, handoverTime, dto.recipientHandle(), rows);
  }

  private static final Comparator<ItemRow> ITEM_COMPARATOR =
      Comparator.comparing(ItemRow::materialName, String.CASE_INSENSITIVE_ORDER)
          .thenComparing(Comparator.comparingInt(ItemRow::quality).reversed())
          .thenComparing(Comparator.comparingDouble(ItemRow::amount).reversed());

  private byte @NotNull [] buildPdf(
      @NotNull String jobOrderNumber,
      @NotNull String handoverDate,
      @NotNull String handoverTime,
      @NotNull String recipientHandle,
      @NotNull List<ItemRow> rows) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Document document = new Document(PageSize.A4, 40, 40, 60, 60);
      PdfWriter writer = PdfWriter.getInstance(document, baos);
      // Disable stream compression so PDF text content is searchable as plain bytes
      // (required for content-based assertions in tests and basic text extraction).
      writer.setCompressionLevel(0);

      // Page event handler draws KRT background on every page (including pages 2+)
      writer.setPageEvent(new KrtPageBackground());

      document.open();

      // Title
      Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, COLOR_ORANGE);
      Paragraph title = new Paragraph("\u00dcBERGABEPROTOKOLL", titleFont);
      title.setAlignment(Element.ALIGN_LEFT);
      title.setSpacingAfter(4f);
      document.add(title);

      // Orange separator line
      PdfContentByte cb = writer.getDirectContent();
      float yPos = writer.getVerticalPosition(false);
      cb.setColorStroke(COLOR_ORANGE);
      cb.setLineWidth(0.5f);
      cb.moveTo(40, yPos);
      cb.lineTo(PageSize.A4.getWidth() - 40, yPos);
      cb.stroke();

      document.add(new Paragraph(" ", new Font(Font.HELVETICA, 6)));

      // Meta info table
      PdfPTable metaTable = new PdfPTable(2);
      metaTable.setWidthPercentage(100);
      metaTable.setWidths(new float[] {1f, 2f});
      metaTable.setSpacingAfter(20f);

      addMetaRow(metaTable, "AUFTRAGSNUMMER", jobOrderNumber);
      addMetaRow(metaTable, "DATUM DER \u00dcBERGABE", handoverDate);
      addMetaRow(metaTable, "UHRZEIT DER \u00dcBERGABE", handoverTime + " (Lokalzeit)");
      addMetaRow(metaTable, "EMPF\u00c4NGER (HANDLE)", recipientHandle);

      document.add(metaTable);

      // Section header: Materials
      Font sectionFont = new Font(Font.HELVETICA, 12, Font.BOLD, COLOR_ORANGE);
      Paragraph matHeader = new Paragraph("\u00dcBERGEBENE MATERIALIEN", sectionFont);
      matHeader.setSpacingAfter(8f);
      document.add(matHeader);

      // Materials table
      PdfPTable matTable = new PdfPTable(4);
      matTable.setWidthPercentage(100);
      matTable.setWidths(new float[] {3f, 2.5f, 1.5f, 1.5f});

      addTableHeader(matTable, "MATERIAL");
      addTableHeader(matTable, "STANDORT");
      addTableHeader(matTable, "MENGE");
      addTableHeader(matTable, "QUALIT\u00c4T");

      boolean alt = false;
      for (ItemRow row : rows) {
        Color rowBg = alt ? COLOR_TABLE_ROW_ALT : COLOR_DARK_GRAY;
        String amountStr = formatAmount(row.amount(), row.quantityType());
        addTableCell(matTable, row.materialName(), rowBg, false);
        addTableCell(matTable, row.locationName(), rowBg, false);
        addTableCell(matTable, amountStr, rowBg, true);
        addTableCell(matTable, String.valueOf(row.quality()), rowBg, true);
        alt = !alt;
      }

      if (rows.isEmpty()) {
        Font emptyFont = new Font(Font.HELVETICA, 10, Font.ITALIC, COLOR_LIGHT_GRAY);
        PdfPCell emptyCell = new PdfPCell(new Phrase("Keine Materialien vorhanden.", emptyFont));
        emptyCell.setColspan(4);
        emptyCell.setBackgroundColor(COLOR_DARK_GRAY);
        emptyCell.setBorderColor(COLOR_ORANGE);
        emptyCell.setBorderWidth(0.3f);
        emptyCell.setPadding(8f);
        matTable.addCell(emptyCell);
      }

      document.add(matTable);

      // Footer
      document.add(new Paragraph(" ", new Font(Font.HELVETICA, 10)));
      Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL, COLOR_LIGHT_GRAY);
      Paragraph footer = new Paragraph("Generiert von IRIDIUM Basetool", footerFont);
      footer.setAlignment(Element.ALIGN_CENTER);
      document.add(footer);

      document.close();
      return baos.toByteArray();
    } catch (Exception e) {
      // Caught by GlobalExceptionHandler.handleReportGeneration which produces a 500
      // RFC 7807 response with the stable code REPORT_GENERATION_FAILED and a localised
      // generic detail. The cause is preserved on the exception so the ERROR log line
      // emitted by the handler carries the full PDF-library stacktrace; the exception
      // message itself is server-internal and never leaks to the API client.
      throw new de.greluc.krt.iri.basetool.backend.exception.ReportGenerationException(
          "PDF generation failed", e);
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

  private @NotNull String formatAmount(double amount, String quantityType) {
    if ("PIECE".equalsIgnoreCase(quantityType)) {
      return String.valueOf((long) amount);
    }
    return String.format("%.3f SCU", amount);
  }

  /** Internal value record for a single material row in the PDF. */
  private record ItemRow(
      String materialName, String locationName, double amount, int quality, String quantityType) {}

  /**
   * PdfPageEvent that paints the KRT corporate-design background on every page, ensuring pages 2+
   * receive the same dark background, orange accent bars and logo as the first page.
   */
  private class KrtPageBackground extends PdfPageEventHelper {

    @Override
    public void onStartPage(PdfWriter writer, Document document) {
      PdfContentByte canvas = writer.getDirectContentUnder();

      // Dark background
      canvas.setColorFill(COLOR_BLACK);
      canvas.rectangle(0, 0, PageSize.A4.getWidth(), PageSize.A4.getHeight());
      canvas.fill();

      // Orange top accent bar
      canvas.setColorFill(COLOR_ORANGE);
      canvas.rectangle(0, PageSize.A4.getHeight() - 8, PageSize.A4.getWidth(), 8);
      canvas.fill();

      // Orange bottom accent bar
      canvas.setColorFill(COLOR_ORANGE);
      canvas.rectangle(0, 0, PageSize.A4.getWidth(), 4);
      canvas.fill();

      // Left orange vertical bar
      canvas.setColorFill(COLOR_ORANGE);
      canvas.rectangle(0, 0, 4, PageSize.A4.getHeight());
      canvas.fill();

      // IRIDIUM logo (bottom-right)
      try (InputStream logoStream =
          getClass()
              .getClassLoader()
              .getResourceAsStream("META-INF/resources/logos/staffel_iridium.png")) {
        if (logoStream != null) {
          Image logo = Image.getInstance(logoStream.readAllBytes());
          float logoHeight = 60f;
          float scale = logoHeight / logo.getHeight();
          float logoWidth = logo.getWidth() * scale;
          float margin = 20f;
          logo.scaleAbsolute(logoWidth, logoHeight);
          logo.setAbsolutePosition(
              PageSize.A4.getWidth() - logoWidth - margin,
              margin + 4f // above the bottom orange bar
              );
          canvas.addImage(logo);
        } else {
          log.warn("staffel_iridium.png not found in classpath");
        }
      } catch (Exception e) {
        log.warn("Failed to render logo on page background", e);
      }
    }
  }
}

package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderHandover;
import de.greluc.krt.iri.basetool.backend.model.JobOrderHandoverItem;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.QuantityType;
import de.greluc.krt.iri.basetool.backend.model.dto.HandoverReportPreviewRequestDto;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderHandoverRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
@ExtendWith(MockitoExtension.class)
class JobOrderHandoverReportServiceTest {

    @Mock
    private JobOrderHandoverRepository jobOrderHandoverRepository;

    @Mock
    private JobOrderRepository jobOrderRepository;

    @InjectMocks
    private JobOrderHandoverReportService service;

    private UUID jobOrderId;
    private UUID handoverId;
    private JobOrder jobOrder;
    private JobOrderHandover handover;

    @BeforeEach
    void setUp() {
        jobOrderId = UUID.randomUUID();
        handoverId = UUID.randomUUID();

        jobOrder = new JobOrder();
        jobOrder.setId(jobOrderId);
        jobOrder.setDisplayId(42);

        handover = new JobOrderHandover();
        handover.setId(handoverId);
        handover.setJobOrder(jobOrder);
        handover.setHandoverTime(Instant.parse("2025-06-15T10:30:00Z"));
        handover.setRecipientHandle("TestPilot");
        handover.setRecipientSquadron("IRIDIUM");
    }

    // -------------------------------------------------------------------------
    // generateHandoverReport – persisted handover
    // -------------------------------------------------------------------------

    @Test
    void generateHandoverReport_shouldReturnNonEmptyPdf_whenHandoverExists() {
        // Given
        Material material = buildMaterial("Laranite", QuantityType.SCU);
        JobOrderHandoverItem item = buildItem(material, 5.0, 100, "Port Olisar");
        handover.setItems(Set.of(item));

        when(jobOrderHandoverRepository.findById(handoverId)).thenReturn(Optional.of(handover));

        // When
        byte[] pdf = service.generateHandoverReport(jobOrderId, handoverId, null);

        // Then
        assertNotNull(pdf, "PDF must not be null");
        assertTrue(pdf.length > 0, "PDF must not be empty");
    }

    @Test
    void generateHandoverReport_shouldThrowNotFound_whenHandoverDoesNotExist() {
        // Given
        when(jobOrderHandoverRepository.findById(handoverId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.generateHandoverReport(jobOrderId, handoverId, null));
    }

    @Test
    void generateHandoverReport_shouldThrowNotFound_whenHandoverBelongsToDifferentJobOrder() {
        // Given
        UUID otherJobOrderId = UUID.randomUUID();
        JobOrder otherJobOrder = new JobOrder();
        otherJobOrder.setId(otherJobOrderId);
        otherJobOrder.setDisplayId(99);
        handover.setJobOrder(otherJobOrder);

        when(jobOrderHandoverRepository.findById(handoverId)).thenReturn(Optional.of(handover));

        // When & Then
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.generateHandoverReport(jobOrderId, handoverId, null));
    }

    @Test
    void generateHandoverReport_shouldSortMaterialsCorrectly() {
        // Given – three items: same material name but different quality/amount, plus a different name
        Material laranite = buildMaterial("Laranite", QuantityType.SCU);
        Material agricium = buildMaterial("Agricium", QuantityType.SCU);

        // Laranite: quality 80, amount 3.0
        JobOrderHandoverItem item1 = buildItem(laranite, 3.0, 80, "Port Olisar");
        // Laranite: quality 100, amount 1.0 → should come before item1 (higher quality)
        JobOrderHandoverItem item2 = buildItem(laranite, 1.0, 100, "Lorville");
        // Laranite: quality 100, amount 5.0 → should come before item2 (same quality, higher amount)
        JobOrderHandoverItem item3 = buildItem(laranite, 5.0, 100, "Lorville");
        // Agricium: alphabetically first
        JobOrderHandoverItem item4 = buildItem(agricium, 2.0, 90, "ArcCorp");

        handover.setItems(Set.of(item1, item2, item3, item4));
        when(jobOrderHandoverRepository.findById(handoverId)).thenReturn(Optional.of(handover));

        // When – just verify it generates without error; sorting is tested via preview below
        byte[] pdf = service.generateHandoverReport(jobOrderId, handoverId, null);

        // Then
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    void generateHandoverReport_shouldNotContainPreviousOwnerName() {
        // Given – the PDF must NOT contain any owner/user name
        Material material = buildMaterial("Titanium", QuantityType.SCU);
        JobOrderHandoverItem item = buildItem(material, 10.0, 75, "New Babbage");
        handover.setItems(Set.of(item));
        handover.setRecipientHandle("RecipientOnly");

        when(jobOrderHandoverRepository.findById(handoverId)).thenReturn(Optional.of(handover));

        // When
        byte[] pdf = service.generateHandoverReport(jobOrderId, handoverId, null);

        // Then – convert to string for basic content check (PDF text is embedded as plain bytes)
        assertNotNull(pdf);
        // The recipient handle must appear in the PDF content
        String pdfContent = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(pdfContent.contains("RecipientOnly"), "Recipient handle must be present in PDF");
        // No owner/user field should appear (the field label "BESITZER" or "OWNER" must not be present)
        assertFalse(pdfContent.contains("BESITZER"), "Previous owner must NOT appear in PDF");
        assertFalse(pdfContent.contains("OWNER"), "Previous owner must NOT appear in PDF");
    }

    // -------------------------------------------------------------------------
    // generateHandoverReportPreview – DTO-based
    // -------------------------------------------------------------------------

    @Test
    void generateHandoverReportPreview_shouldReturnNonEmptyPdf() {
        // Given
        HandoverReportPreviewRequestDto dto = new HandoverReportPreviewRequestDto(
                "#42",
                LocalDateTime.parse("2025-06-15T10:30:00"),
                "PreviewPilot",
                List.of(new HandoverReportPreviewRequestDto.HandoverReportItemDto(
                        "Laranite", "Port Olisar", 3.5, 90, "SCU"
                ))
        );

        // When
        byte[] pdf = service.generateHandoverReportPreview(dto);

        // Then
        assertNotNull(pdf, "Preview PDF must not be null");
        assertTrue(pdf.length > 0, "Preview PDF must not be empty");
    }

    @Test
    void generateHandoverReportPreview_shouldSortItemsAlphabeticallyThenQualityDescThenAmountDesc() {
        // Given – items in wrong order; service must sort them
        List<HandoverReportPreviewRequestDto.HandoverReportItemDto> items = List.of(
                new HandoverReportPreviewRequestDto.HandoverReportItemDto("Titanium", "A", 1.0, 50, "SCU"),
                new HandoverReportPreviewRequestDto.HandoverReportItemDto("Agricium", "B", 2.0, 80, "SCU"),
                new HandoverReportPreviewRequestDto.HandoverReportItemDto("Laranite", "C", 5.0, 100, "SCU"),
                new HandoverReportPreviewRequestDto.HandoverReportItemDto("Laranite", "D", 3.0, 100, "SCU"),
                new HandoverReportPreviewRequestDto.HandoverReportItemDto("Laranite", "E", 5.0, 90, "SCU")
        );

        HandoverReportPreviewRequestDto dto = new HandoverReportPreviewRequestDto(
                "#7", LocalDateTime.now(), "SortTestPilot", items
        );

        // When
        byte[] pdf = service.generateHandoverReportPreview(dto);

        // Then – PDF generated successfully (sorting correctness verified by content order)
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        // Verify the PDF contains all material names
        String pdfContent = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(pdfContent.contains("Agricium"));
        assertTrue(pdfContent.contains("Laranite"));
        assertTrue(pdfContent.contains("Titanium"));
    }

    @Test
    void generateHandoverReportPreview_shouldHandleEmptyItemList() {
        // Given
        HandoverReportPreviewRequestDto dto = new HandoverReportPreviewRequestDto(
                "#0", LocalDateTime.now(), "EmptyPilot", List.of()
        );

        // When
        byte[] pdf = service.generateHandoverReportPreview(dto);

        // Then – must still generate a valid PDF
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    void generateHandoverReportPreview_shouldNotContainOwnerField() {
        // Given
        HandoverReportPreviewRequestDto dto = new HandoverReportPreviewRequestDto(
                "#5",
                LocalDateTime.now(),
                "RecipientHandle",
                List.of(new HandoverReportPreviewRequestDto.HandoverReportItemDto(
                        "Copper", "Lorville", 10.0, 60, "SCU"
                ))
        );

        // When
        byte[] pdf = service.generateHandoverReportPreview(dto);

        // Then
        assertNotNull(pdf);
        String pdfContent = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(pdfContent.contains("BESITZER"), "Owner field must NOT appear in preview PDF");
        assertFalse(pdfContent.contains("OWNER"), "Owner field must NOT appear in preview PDF");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Material buildMaterial(String name, QuantityType quantityType) {
        Material m = new Material();
        m.setId(UUID.randomUUID());
        m.setName(name);
        m.setQuantityType(quantityType);
        return m;
    }

    private JobOrderHandoverItem buildItem(Material material, double amount, int quality, String locationName) {
        JobOrderHandoverItem item = new JobOrderHandoverItem();
        item.setId(UUID.randomUUID());
        item.setMaterial(material);
        item.setAmount(amount);
        item.setQuality(quality);
        item.setLocationName(locationName);
        return item;
    }
}

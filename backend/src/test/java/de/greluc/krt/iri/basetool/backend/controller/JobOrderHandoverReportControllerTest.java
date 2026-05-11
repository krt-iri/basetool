package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.dto.HandoverReportPreviewRequestDto;
import de.greluc.krt.iri.basetool.backend.service.JobOrderHandoverReportService;
import de.greluc.krt.iri.basetool.backend.service.JobOrderHandoverService;
import de.greluc.krt.iri.basetool.backend.service.JobOrderService;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
@ExtendWith(MockitoExtension.class)
class JobOrderHandoverReportControllerTest {

    @Mock
    private JobOrderService jobOrderService;

    @Mock
    private JobOrderHandoverService jobOrderHandoverService;

    @Mock
    private JobOrderHandoverReportService jobOrderHandoverReportService;

    @Mock
    private UserService userService;

    @Mock
    private RoleHierarchy roleHierarchy;

    @InjectMocks
    private JobOrderController controller;

    // -------------------------------------------------------------------------
    // GET /{jobOrderId}/handovers/{handoverId}/report
    // -------------------------------------------------------------------------

    @Test
    void downloadHandoverReport_shouldReturnPdfResponse_whenHandoverExists() {
        // Given
        UUID jobOrderId = UUID.randomUUID();
        UUID handoverId = UUID.randomUUID();
        byte[] fakePdf = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF magic bytes

        when(jobOrderHandoverReportService.generateHandoverReport(jobOrderId, handoverId, null))
                .thenReturn(fakePdf);

        // When
        ResponseEntity<byte[]> response = controller.downloadHandoverReport(jobOrderId, handoverId, null);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(fakePdf.length, response.getBody().length);
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition);
        assertTrue(disposition.contains("attachment"), "Content-Disposition must be attachment");
        assertTrue(disposition.contains(".pdf"), "Filename must end with .pdf");
        verify(jobOrderHandoverReportService).generateHandoverReport(jobOrderId, handoverId, null);
    }

    @Test
    void downloadHandoverReport_shouldPropagateNotFound_whenHandoverDoesNotExist() {
        // Given
        UUID jobOrderId = UUID.randomUUID();
        UUID handoverId = UUID.randomUUID();

        when(jobOrderHandoverReportService.generateHandoverReport(jobOrderId, handoverId, null))
                .thenThrow(new NotFoundException("Handover not found"));

        // When & Then
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> controller.downloadHandoverReport(jobOrderId, handoverId, null));
    }

    // -------------------------------------------------------------------------
    // POST /{jobOrderId}/handovers/report/preview
    // -------------------------------------------------------------------------

    @Test
    void previewHandoverReport_shouldReturnPdfResponse_whenDtoIsValid() {
        // Given
        UUID jobOrderId = UUID.randomUUID();
        byte[] fakePdf = new byte[]{0x25, 0x50, 0x44, 0x46};

        HandoverReportPreviewRequestDto dto = new HandoverReportPreviewRequestDto(
                "#42",
                LocalDateTime.now(),
                "PreviewPilot",
                List.of(new HandoverReportPreviewRequestDto.HandoverReportItemDto(
                        "Laranite", "Port Olisar", 3.5, 90, "SCU"
                ))
        );

        when(jobOrderHandoverReportService.generateHandoverReportPreview(dto))
                .thenReturn(fakePdf);

        // When
        ResponseEntity<byte[]> response = controller.previewHandoverReport(jobOrderId, dto);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(fakePdf.length, response.getBody().length);
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition);
        assertTrue(disposition.contains("attachment"), "Content-Disposition must be attachment");
        assertTrue(disposition.contains(".pdf"), "Filename must end with .pdf");
        verify(jobOrderHandoverReportService).generateHandoverReportPreview(dto);
    }

    @Test
    void previewHandoverReport_shouldPropagateServiceException() {
        // Given
        UUID jobOrderId = UUID.randomUUID();
        HandoverReportPreviewRequestDto dto = new HandoverReportPreviewRequestDto(
                "#1", LocalDateTime.now(), "Pilot", List.of()
        );

        when(jobOrderHandoverReportService.generateHandoverReportPreview(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PDF generation failed"));

        // When & Then
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.previewHandoverReport(jobOrderId, dto));
        assertEquals(500, ex.getStatusCode().value());
    }
}

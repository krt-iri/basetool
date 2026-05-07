package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request DTO for generating a handover report PDF preview (before the handover is persisted).
 *
 * <p>{@code handoverTime} is intentionally a {@link LocalDateTime} (not {@link java.time.Instant}):
 * it represents exactly what the user typed into the modal in their local time zone, and the PDF
 * preview must show that same value back to the user without any time-zone round-trip. Using
 * {@link LocalDateTime} avoids the bug where a server-side {@code ZoneId.systemDefault()} would
 * shift the displayed time relative to the user's actual time zone.
 */
public record HandoverReportPreviewRequestDto(
        @NotBlank String jobOrderNumber,
        @NotNull LocalDateTime handoverTime,
        @NotBlank String recipientHandle,
        @NotNull @Valid List<HandoverReportItemDto> items
) {
    /**
     * Represents a single material item in the handover report preview.
     */
    public record HandoverReportItemDto(
            @NotBlank String materialName,
            String locationName,
            @NotNull Double amount,
            @NotNull Integer quality,
            String quantityType
    ) {
    }
}

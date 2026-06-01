package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Create payload for an item handover: the hand-over of one or more produced item quantities to a
 * recipient. Mirrors {@link JobOrderHandoverCreateDto} (the material counterpart) but itemises
 * delivered ordered-item lines instead of inventory items.
 *
 * @param handoverTime when the handover occurred (UTC)
 * @param recipientHandle the recipient's handle (≤ 255 chars)
 * @param entries the delivered item-line quantities (at least one)
 */
public record JobOrderItemHandoverCreateDto(
    @NotNull Instant handoverTime,
    @NotBlank @Size(max = 255) String recipientHandle,
    @NotEmpty @Valid List<JobOrderItemHandoverEntryCreateDto> entries) {}

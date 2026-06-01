package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.List;

/**
 * Frontend mirror of the backend {@code JobOrderItemHandoverCreateDto}: the create payload the
 * frontend sends when a logistician hands over produced items from an item order. Itemises the
 * delivered ordered-item lines (one {@link JobOrderItemHandoverEntryCreateDto} per line).
 *
 * @param handoverTime when the handover occurred (UTC)
 * @param recipientHandle the recipient's handle
 * @param entries the delivered item-line quantities (at least one)
 */
public record JobOrderItemHandoverCreateDto(
    Instant handoverTime,
    String recipientHandle,
    List<JobOrderItemHandoverEntryCreateDto> entries) {}

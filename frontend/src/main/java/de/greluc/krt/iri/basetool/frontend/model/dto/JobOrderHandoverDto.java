package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Data transfer record carrying Job Order Handover payload. */
public record JobOrderHandoverDto(
    UUID id,
    UUID jobOrderId,
    Instant handoverTime,
    String recipientHandle,
    String recipientSquadron,
    List<JobOrderHandoverItemDto> items,
    Long version) {}

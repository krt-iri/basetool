package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Job Order Handover Item payload. */
public record JobOrderHandoverItemDto(
    UUID id,
    UUID jobOrderHandoverId,
    MaterialDto material,
    Integer quality,
    Double amount,
    String locationName,
    Long version) {}

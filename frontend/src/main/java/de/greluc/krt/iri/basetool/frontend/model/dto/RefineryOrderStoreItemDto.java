package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Refinery Order Store Item payload. */
public record RefineryOrderStoreItemDto(
    UUID materialId,
    UUID locationId,
    Integer quality,
    Double amount,
    UUID userId,
    UUID jobOrderId,
    String note) {}

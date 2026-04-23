package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

public record RefineryOrderStoreItemDto(
    UUID materialId,
    UUID locationId,
    Integer quality,
    Double amount,
    UUID userId,
    UUID jobOrderId,
    String note
) {}

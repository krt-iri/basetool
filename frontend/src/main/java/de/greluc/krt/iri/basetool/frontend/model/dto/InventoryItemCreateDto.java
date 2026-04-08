package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

public record InventoryItemCreateDto(
    UUID userId,
    UUID materialId,
    UUID locationId,
    Integer quality,
    Double amount,
    Boolean personal,
    UUID missionId,
    UUID jobOrderId
) {}

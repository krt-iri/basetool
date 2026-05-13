package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Inventory Item Create payload. */
public record InventoryItemCreateDto(
    UUID userId,
    UUID materialId,
    UUID locationId,
    Integer quality,
    Double amount,
    Boolean personal,
    UUID missionId,
    UUID jobOrderId) {}

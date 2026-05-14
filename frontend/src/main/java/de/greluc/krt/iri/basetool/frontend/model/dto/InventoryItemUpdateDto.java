package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Inventory Item Update payload. */
public record InventoryItemUpdateDto(
    UUID materialId,
    UUID locationId,
    Integer quality,
    Double amount,
    Boolean personal,
    UUID jobOrderId,
    UUID missionId,
    Long version) {}

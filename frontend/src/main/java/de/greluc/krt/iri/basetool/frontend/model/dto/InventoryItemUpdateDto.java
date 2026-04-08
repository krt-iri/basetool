package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

public record InventoryItemUpdateDto(
    UUID materialId,
    UUID locationId,
    Integer quality,
    Double amount,
    Boolean personal,
    UUID jobOrderId,
    UUID missionId,
    Long version
) {}

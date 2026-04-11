package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

public record JobOrderHandoverItemDto(
        UUID id,
        UUID jobOrderHandoverId,
        InventoryItemDto inventoryItem,
        MaterialDto material,
        Integer quality,
        Double amount,
        Long version
) {
}

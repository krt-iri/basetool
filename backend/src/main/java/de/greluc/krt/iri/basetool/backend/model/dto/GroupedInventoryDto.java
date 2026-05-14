package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.List;

/** Data transfer record carrying Grouped Inventory payload. */
public record GroupedInventoryDto(
    MaterialReferenceDto material,
    Double totalAmount,
    Double averageQuality,
    Integer maxQuality,
    List<InventoryItemDto> items) {}

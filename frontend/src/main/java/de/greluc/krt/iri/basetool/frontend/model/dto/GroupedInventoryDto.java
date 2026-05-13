package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

public record GroupedInventoryDto(
    MaterialReferenceDto material,
    Double totalAmount,
    Double averageQuality,
    Integer maxQuality,
    List<InventoryItemDto> items) {}

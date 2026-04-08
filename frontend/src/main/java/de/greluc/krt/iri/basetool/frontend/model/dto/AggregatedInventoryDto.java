package de.greluc.krt.iri.basetool.frontend.model.dto;

public record AggregatedInventoryDto(
    MaterialDto material,
    Double quality,
    Double amount
) {}

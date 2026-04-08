package de.greluc.krt.iri.basetool.backend.model.dto;

public record AggregatedInventoryDto(
    MaterialDto material,
    Double quality,
    Double amount
) {}

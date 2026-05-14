package de.greluc.krt.iri.basetool.frontend.model.dto;

/** Data transfer record carrying Aggregated Inventory payload. */
public record AggregatedInventoryDto(MaterialDto material, Double quality, Double amount) {}

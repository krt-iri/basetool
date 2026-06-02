package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

/**
 * Internal projection of an active {@code blueprint} row reduced to its id and SC Wiki output name,
 * used by {@code BlueprintProductService} to resolve a normalized {@code product_key} back to a
 * representative recipe id (the recipe graph is then loaded by id). Not a controller-boundary DTO —
 * produced by a JPQL constructor expression and consumed only inside the service.
 *
 * @param id the blueprint primary key
 * @param outputName the SC Wiki output-item name (normalized, then grouped into a product)
 */
public record BlueprintIdNameRow(UUID id, String outputName) {}

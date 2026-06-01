package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

/**
 * Internal projection row for the blueprint product search (#327): one active {@code blueprint}
 * recipe reduced to just the scalars the {@code BlueprintProductService} needs to group recipes
 * into products. Not a controller-boundary DTO — it is produced by a JPQL constructor expression
 * and consumed only inside the service.
 *
 * @param outputName the SC Wiki output-item name (grouped, after normalization, into a product)
 * @param scwikiKey the recipe's Wiki key, surfaced as an example key for the product
 * @param manufacturerName the resolved output item's manufacturer name, or {@code null} if the
 *     output item / manufacturer is unresolved
 * @param outputItemId id of the resolved output {@code game_item}, or {@code null} if unresolved
 */
public record BlueprintProductRow(
    String outputName, String scwikiKey, String manufacturerName, UUID outputItemId) {}

package de.greluc.krt.iri.basetool.backend.model.dto;

/**
 * Boundary DTO for one searchable blueprint product (#327). A product is the de-duplicated unit of
 * ownership: all active blueprint recipes whose output name normalizes to the same {@link
 * #productKey} collapse into a single entry. Backs the Blueprints-page type-ahead.
 *
 * @param productKey normalized product identity (matches {@code personal_blueprint.product_key})
 * @param name display name of the product (original SC Wiki spelling)
 * @param variantCount number of active blueprint recipes that produce this product
 * @param manufacturerName manufacturer of the produced item, or {@code null} if unresolved
 * @param exampleKey one representative SC Wiki recipe key for the product, or {@code null}
 * @param ownedByCurrentUser whether the calling user already owns this product
 */
public record BlueprintProductDto(
    String productKey,
    String name,
    int variantCount,
    String manufacturerName,
    String exampleKey,
    boolean ownedByCurrentUser) {}

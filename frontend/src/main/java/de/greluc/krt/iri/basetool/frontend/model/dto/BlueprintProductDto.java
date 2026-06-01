package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Read DTO mirroring the backend {@code BlueprintProductDto} (#327). One searchable blueprint
 * product — the de-duplicated unit of ownership — surfaced by the type-ahead on the Blueprints
 * page.
 *
 * @param productKey normalized product identity (echoed back when adding to the owned set)
 * @param name display name of the product (original SC Wiki spelling)
 * @param variantCount number of active blueprint recipes that produce this product
 * @param manufacturerName manufacturer of the produced item, or {@code null} if unresolved
 * @param exampleKey one representative SC Wiki recipe key, or {@code null}
 * @param ownedByCurrentUser whether the calling user already owns this product
 */
public record BlueprintProductDto(
    String productKey,
    String name,
    int variantCount,
    String manufacturerName,
    String exampleKey,
    boolean ownedByCurrentUser) {}

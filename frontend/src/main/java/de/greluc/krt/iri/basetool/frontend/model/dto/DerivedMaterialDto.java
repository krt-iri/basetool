package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Frontend mirror of the backend {@code DerivedMaterialDto}: one resolved material requirement in
 * an item-order derivation preview, with the quantity for the previewed amount and the quality the
 * UI pre-selects.
 *
 * @param material the required material (carries {@code quantityType} for unit-aware display)
 * @param requiredQuantity quantity needed for the previewed amount
 * @param defaultQuality the pre-selected quality choice ({@code GOOD} or {@code NONE})
 */
public record DerivedMaterialDto(
    MaterialDto material, Double requiredQuantity, String defaultQuality) {}

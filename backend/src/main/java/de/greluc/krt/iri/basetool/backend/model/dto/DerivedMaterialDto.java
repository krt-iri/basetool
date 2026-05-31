package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.QualityRequirement;

/**
 * One resolved material requirement in an item-order derivation preview: the material, the quantity
 * needed for the previewed amount (unit from {@code material.quantityType}), and the quality the UI
 * should pre-select ({@code GOOD} when the blueprint ingredient's {@code minQuality} is 700+, else
 * {@code NONE}). The requester may override {@code defaultQuality} per material before submitting.
 *
 * @param material the required material (carries {@code quantityType} for unit-aware display)
 * @param requiredQuantity quantity needed for the previewed amount
 * @param defaultQuality the pre-selected quality choice derived from the ingredient
 */
public record DerivedMaterialDto(
    MaterialDto material, Double requiredQuantity, QualityRequirement defaultQuality) {}

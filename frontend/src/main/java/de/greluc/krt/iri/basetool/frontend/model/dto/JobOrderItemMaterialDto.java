package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend {@code JobOrderItemMaterialDto}: one snapshotted material
 * requirement of an ordered item line. {@code qualityRequirement} is the {@code GOOD}/{@code NONE}
 * name as a string.
 *
 * @param id the requirement row id
 * @param material the required material (carries {@code quantityType} for unit-aware display)
 * @param requiredQuantity the amount needed for the line
 * @param qualityRequirement the quality bucket name ({@code GOOD} or {@code NONE})
 * @param version optimistic-lock version
 */
public record JobOrderItemMaterialDto(
    UUID id,
    MaterialDto material,
    Double requiredQuantity,
    String qualityRequirement,
    Long version) {}

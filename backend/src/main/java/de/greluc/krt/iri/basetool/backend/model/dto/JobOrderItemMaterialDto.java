package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.QualityRequirement;
import java.util.UUID;

/**
 * One snapshotted material requirement of a single ordered item line. The {@code requiredQuantity}
 * unit is interpreted from {@code material.quantityType} (SCU fractional vs PIECE whole-number);
 * {@code qualityRequirement} is the requester's per-order Gut/Keine choice for this material.
 *
 * @param id the requirement row's primary key
 * @param material the required material, with its {@code quantityType} for unit-aware formatting
 * @param requiredQuantity the amount needed for the owning line (already scaled by the line's
 *     quantity)
 * @param qualityRequirement {@code GOOD} (700+) or {@code NONE} (no floor)
 * @param version optimistic-lock version
 */
public record JobOrderItemMaterialDto(
    UUID id,
    MaterialDto material,
    Double requiredQuantity,
    QualityRequirement qualityRequirement,
    Long version) {}

package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.QualityRequirement;
import java.util.List;

/**
 * One aggregation row of an item order's internal material view: the total quantity of a single
 * material needed across the whole order at one quality level. A material required in both
 * qualities yields two rows (one {@code GOOD}, one {@code NONE}); the display formats {@code
 * totalQuantity} per {@code material.quantityType} (SCU vs Stück).
 *
 * @param material the aggregated material, with its {@code quantityType} for unit-aware formatting
 * @param qualityRequirement the quality bucket this row sums ({@code GOOD} or {@code NONE})
 * @param totalQuantity the summed required quantity across all item lines for this material+quality
 * @param claims the per-squadron claims on this bucket; populated only for public SK orders (Phase
 *     5, #345), empty otherwise
 * @param openAmount {@code totalQuantity − Σ claims} for the bucket; {@code null} for non-SK
 *     orders, a non-null value (possibly 0) for SK orders
 */
public record AggregatedMaterialDto(
    MaterialDto material,
    QualityRequirement qualityRequirement,
    Double totalQuantity,
    List<ClaimDto> claims,
    Double openAmount) {}

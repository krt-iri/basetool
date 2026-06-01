package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/**
 * Frontend mirror of the backend {@code AggregatedMaterialDto}: one aggregation row of an item
 * order's internal material view (a material at one quality, summed across the order). {@code
 * qualityRequirement} is the {@code GOOD}/{@code NONE} name as a string. {@code claims}/{@code
 * openAmount} are populated only for public SK orders (Phase 5, #345); {@code openAmount} is {@code
 * null} for private orders, which is how the detail template decides whether to render the claim
 * columns.
 *
 * @param material the aggregated material (carries {@code quantityType} for unit-aware display)
 * @param qualityRequirement the quality bucket name ({@code GOOD} or {@code NONE})
 * @param totalQuantity the summed required quantity for this material+quality
 * @param claims the per-squadron claims on this bucket (empty for non-SK orders)
 * @param openAmount {@code totalQuantity − Σ claims}; {@code null} for non-SK orders
 */
public record AggregatedMaterialDto(
    MaterialDto material,
    String qualityRequirement,
    Double totalQuantity,
    List<ClaimDto> claims,
    Double openAmount) {}

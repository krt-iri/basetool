package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Frontend mirror of the backend {@code AggregatedMaterialDto}: one aggregation row of an item
 * order's internal material view (a material at one quality, summed across the order). {@code
 * qualityRequirement} is the {@code GOOD}/{@code NONE} name as a string.
 *
 * @param material the aggregated material (carries {@code quantityType} for unit-aware display)
 * @param qualityRequirement the quality bucket name ({@code GOOD} or {@code NONE})
 * @param totalQuantity the summed required quantity for this material+quality
 */
public record AggregatedMaterialDto(
    MaterialDto material, String qualityRequirement, Double totalQuantity) {}

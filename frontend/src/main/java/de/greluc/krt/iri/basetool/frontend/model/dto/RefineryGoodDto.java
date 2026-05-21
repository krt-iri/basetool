package de.greluc.krt.iri.basetool.frontend.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;

/**
 * Data transfer record carrying Refinery Good payload.
 *
 * <p>{@code yieldBonusPercent} mirrors the backend's read-only UEX-derived enrichment (positive =
 * bonus, negative = malus, {@code null} = no yield row known for the (location, material) pair).
 * The frontend renders it next to the input-quantity field and ignores it on form submit (backend
 * recomputes it on every response).
 */
public record RefineryGoodDto(
    UUID id,
    MaterialDto inputMaterial,
    @Min(1) Integer inputQuantity,
    MaterialDto outputMaterial,
    @Min(1) Integer outputQuantity,
    @Min(0) @Max(1000) Integer quality,
    Integer version,
    Integer yieldBonusPercent) {}

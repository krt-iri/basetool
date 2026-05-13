package de.greluc.krt.iri.basetool.frontend.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;

/** Data transfer record carrying Refinery Good payload. */
public record RefineryGoodDto(
    UUID id,
    MaterialDto inputMaterial,
    @Min(1) Integer inputQuantity,
    MaterialDto outputMaterial,
    @Min(1) Integer outputQuantity,
    @Min(0) @Max(1000) Integer quality,
    Integer version) {}

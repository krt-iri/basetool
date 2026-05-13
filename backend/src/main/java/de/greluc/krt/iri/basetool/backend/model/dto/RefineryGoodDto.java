package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RefineryGoodDto(
    UUID id,
    @NotNull MaterialDto inputMaterial,
    @NotNull @Min(1) Integer inputQuantity,
    MaterialDto outputMaterial,
    @Min(1) Integer outputQuantity,
    Integer quality) {}

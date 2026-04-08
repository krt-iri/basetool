package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateJobOrderMaterialDto(
        @NotNull UUID materialId,
        @NotNull @Min(0) Integer minQuality,
        @NotNull @Min(0) Double amount
) {}

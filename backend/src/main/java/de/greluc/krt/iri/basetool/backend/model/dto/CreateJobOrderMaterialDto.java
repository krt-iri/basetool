package de.greluc.krt.iri.basetool.backend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Data transfer record carrying Create Job Order Material payload. */
public record CreateJobOrderMaterialDto(
    @NotNull UUID materialId,
    @NotNull
        @Min(700)
        @Max(700)
        @Schema(
            description = "Minimale Qualität, wird serverseitig fest auf 700 gesetzt",
            example = "700")
        Integer minQuality,
    @NotNull @Min(0) Double amount) {}

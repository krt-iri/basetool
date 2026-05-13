package de.greluc.krt.iri.basetool.backend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Data transfer record carrying Create Job Order Material payload. */
public record CreateJobOrderMaterialDto(
    @NotNull UUID materialId,
    @NotNull @Min(750) @Max(750) @Schema(
            description = "Minimale Qualit\u00e4t, wird serverseitig fest auf 750 gesetzt",
            example = "750")
        Integer minQuality,
    @NotNull @Min(0) Double amount) {}

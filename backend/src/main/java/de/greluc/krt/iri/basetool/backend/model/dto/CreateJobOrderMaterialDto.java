package de.greluc.krt.iri.basetool.backend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Data transfer record carrying Create Job Order Material payload. */
public record CreateJobOrderMaterialDto(
    @NotNull UUID materialId,
    @org.jetbrains.annotations.Nullable
        @Min(700)
        @Max(700)
        @Schema(
            description =
                "Minimale Qualität: 700 (vorgegeben) oder null für \"Keine\" (keine"
                    + " Mindestqualität).",
            example = "700")
        Integer minQuality,
    // @Max caps the per-material amount at 100 000 units so an anonymous caller cannot push a
    // 1e308 value through the public create-order endpoint (audit finding H-2: ledger pollution
    // + downstream BigDecimal aggregation overflow). Tightening from "no upper bound" to
    // 100 000 covers any realistic legitimate Star Citizen cargo manifest by an order of
    // magnitude.
    @NotNull @Min(0) @Max(100_000) Double amount) {}

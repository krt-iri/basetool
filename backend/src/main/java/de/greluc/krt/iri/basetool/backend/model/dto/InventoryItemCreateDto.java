package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InventoryItemCreateDto(
    UUID userId,
    @NotNull UUID materialId,
    @NotNull UUID locationId,
    @NotNull @Min(0) @Max(1000) Integer quality,
    @NotNull @Min(0) Double amount,
    Boolean personal,
    UUID missionId,
    UUID jobOrderId
) {}

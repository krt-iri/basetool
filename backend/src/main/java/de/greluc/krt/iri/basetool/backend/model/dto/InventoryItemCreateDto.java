package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

import de.greluc.krt.iri.basetool.backend.validation.QuantityAware;
import de.greluc.krt.iri.basetool.backend.validation.ValidQuantityAmount;

@ValidQuantityAmount
public record InventoryItemCreateDto(
    UUID userId,
    @NotNull UUID materialId,
    @NotNull UUID locationId,
    @NotNull @Min(0) @Max(1000) Integer quality,
    @NotNull Double amount,
    Boolean personal,
    UUID missionId,
    UUID jobOrderId
) implements QuantityAware {}

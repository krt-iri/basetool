package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.validation.QuantityAware;
import de.greluc.krt.iri.basetool.backend.validation.ValidQuantityAmount;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Data transfer record carrying Inventory Item Update payload. */
@ValidQuantityAmount
public record InventoryItemUpdateDto(
    @NotNull UUID materialId,
    @NotNull UUID locationId,
    @NotNull @Min(0) @Max(1000) Integer quality,
    @NotNull Double amount,
    Boolean personal,
    UUID jobOrderId,
    UUID missionId,
    Long version)
    implements QuantityAware {}

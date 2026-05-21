package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Data transfer record carrying Refinery Good payload.
 *
 * <p>{@code yieldBonusPercent} is a UEX-derived, read-only enrichment: it carries the percentage
 * bonus or malus the chosen refinery applies to {@code inputMaterial} (positive = bonus, negative =
 * malus, {@code null} = no yield row known for this refinery/material pair, e.g. when the order has
 * no location resolvable to a UEX terminal yet). The backend populates the field on read; inbound
 * write payloads ignore it and the database persists nothing for it — see {@code
 * RefineryOrderMapper.toDto(RefineryOrder, Map)} and {@code
 * RefineryOrderService.getYieldBonusByMaterialForLocation(Location)}.
 */
public record RefineryGoodDto(
    UUID id,
    @NotNull MaterialDto inputMaterial,
    @NotNull @Min(1) Integer inputQuantity,
    MaterialDto outputMaterial,
    @Min(1) Integer outputQuantity,
    Integer quality,
    Integer yieldBonusPercent) {}

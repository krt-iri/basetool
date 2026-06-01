package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * One delivered line of an item-handover create payload: how many whole units of a specific ordered
 * item line ({@code jobOrderItemId}) changed hands. The amount must not exceed the line's
 * outstanding (ordered minus already-delivered) quantity, enforced server-side.
 *
 * @param jobOrderItemId the ordered item line being fulfilled
 * @param amount whole-unit count delivered for that line (≥ 1)
 */
public record JobOrderItemHandoverEntryCreateDto(
    @NotNull UUID jobOrderItemId, @NotNull @Min(1) Integer amount) {}

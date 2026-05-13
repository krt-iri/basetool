package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** Data transfer record carrying Job Order Handover Item Create payload. */
public record JobOrderHandoverItemCreateDto(
    @NotNull UUID inventoryItemId, @NotNull @Positive Double amount) {}

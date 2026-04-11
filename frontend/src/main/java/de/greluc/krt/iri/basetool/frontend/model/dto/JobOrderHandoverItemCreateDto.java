package de.greluc.krt.iri.basetool.frontend.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record JobOrderHandoverItemCreateDto(
        @NotNull
        UUID inventoryItemId,

        @NotNull
        @Positive
        Double amount
) {
}

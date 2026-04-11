package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobOrderHandoverCreateDto(
        @NotNull
        Instant handoverTime,

        @NotBlank
        String recipientHandle,

        String recipientSquadron,

        @NotEmpty
        List<JobOrderHandoverItemCreateDto> items
) {
}

package de.greluc.krt.iri.basetool.frontend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** Data transfer record carrying Job Order Handover Create payload. */
public record JobOrderHandoverCreateDto(
    @NotNull Instant handoverTime,
    @NotBlank String recipientHandle,
    String recipientSquadron,
    @NotEmpty List<JobOrderHandoverItemCreateDto> items) {}

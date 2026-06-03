package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.Valid;
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
    // @Valid is required for the @Positive on each item's amount to cascade into the list elements
    // (Bean Validation only descends into collection elements when the field carries @Valid). Audit
    // M-4: without it a negative amount slipped through and *increased* stock + open requirement.
    @NotEmpty @Valid List<JobOrderHandoverItemCreateDto> items) {}

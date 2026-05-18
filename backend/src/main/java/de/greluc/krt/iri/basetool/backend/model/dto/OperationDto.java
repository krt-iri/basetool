package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.OperationStatus;
import java.time.Instant;
import java.util.UUID;

/** Data transfer record carrying Operation payload. */
public record OperationDto(
    UUID id,
    String name,
    String description,
    OperationStatus status,
    SquadronReferenceDto owningSquadron,
    Long version,
    Instant createdAt,
    Instant updatedAt) {}

package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.OperationStatus;
import java.time.Instant;
import java.util.UUID;

public record OperationDto(
        UUID id,
        String name,
        String description,
        OperationStatus status,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}

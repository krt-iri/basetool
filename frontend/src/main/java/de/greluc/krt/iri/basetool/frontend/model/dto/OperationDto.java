package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.UUID;

public record OperationDto(
        UUID id,
        String name,
        String description,
        Instant startTime,
        Instant endTime,
        String status,
        Long version
) {
}

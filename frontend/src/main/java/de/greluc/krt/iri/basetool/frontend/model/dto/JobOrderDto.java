package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobOrderDto(
        UUID id,
        Integer displayId,
        String squadron,
        String handle,
        Integer priority,
        String status,
        List<JobOrderMaterialDto> materials,
        List<UserDto> assignees,
        Instant createdAt,
        Long version
) {}

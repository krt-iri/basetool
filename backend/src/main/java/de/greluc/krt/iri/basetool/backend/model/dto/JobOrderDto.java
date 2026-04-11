package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobOrderDto(
        UUID id,
        Integer displayId,
        String squadron,
        String handle,
        Integer priority,
        JobOrderStatus status,
        List<JobOrderMaterialDto> materials,
        List<UserDto> assignees,
        List<JobOrderHandoverDto> handovers,
        Instant createdAt,
        Long version
) {}

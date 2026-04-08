package de.greluc.krt.iri.basetool.backend.model.dto;

import java.time.Instant;
import java.util.UUID;

public record MissionListDto(
        UUID id,
        String name,
        String description,
        String calendarLink,
        String status,
        Instant meetingTime,
        Instant plannedStartTime,
        Instant actualStartTime,
        Instant plannedEndTime,
        Instant actualEndTime,
        Boolean isInternal,
        OperationDto operation,
        Long version
) {
}

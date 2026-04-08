package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.Set;
import java.util.UUID;

public record MissionCrewDto(
        UUID id,
        UUID participantId,
        String participantName,
        Set<JobTypeDto> jobTypes,
        Long version
) {
}

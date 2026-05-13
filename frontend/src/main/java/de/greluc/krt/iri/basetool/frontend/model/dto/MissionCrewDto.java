package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.Set;
import java.util.UUID;

/** Data transfer record carrying Mission Crew payload. */
public record MissionCrewDto(
    UUID id, UUID participantId, String participantName, Set<JobTypeDto> jobTypes, Long version) {}

package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.UUID;

/** Data transfer record carrying Mission Reference payload. */
public record MissionReferenceDto(UUID id, String name, String status, Instant plannedStartTime) {}

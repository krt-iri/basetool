package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

/**
 * Slim id + name projection of an Operation, used to populate dropdowns and typeaheads without
 * pulling the full {@link OperationDto} payload (description, status, owningSquadron, version).
 * Matches the {@code MissionReferenceDto} contract for the mission picker so both lookup endpoints
 * follow the same shape.
 */
public record OperationReferenceDto(UUID id, String name) {}

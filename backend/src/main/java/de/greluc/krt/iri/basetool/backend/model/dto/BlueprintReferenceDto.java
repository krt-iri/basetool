package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

/**
 * Slim reference projection of a {@code Blueprint} for item-order payloads and the blueprint
 * picker. Identifies the recipe chosen to produce an ordered item without exposing the full
 * ingredient graph.
 *
 * @param id the blueprint's primary key
 * @param outputName the human-readable name of the produced item as recorded on the blueprint
 * @param scwikiKey the SC-Wiki key of the blueprint (stable identifier for disambiguation)
 */
public record BlueprintReferenceDto(UUID id, String outputName, String scwikiKey) {}

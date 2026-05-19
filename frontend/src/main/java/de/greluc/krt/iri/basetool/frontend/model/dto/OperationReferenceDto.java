package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend's {@code OperationReferenceDto}. Slim id + name shape used by the
 * mission-detail page's operation-picker dropdown to avoid pulling the full {@link OperationDto}
 * payload for every option.
 */
public record OperationReferenceDto(UUID id, String name) {}

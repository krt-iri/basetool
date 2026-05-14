package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Operation payload. */
public record OperationDto(UUID id, String name, String description, String status, Long version) {}

package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Location payload. */
public record LocationDto(UUID id, String name, String description, boolean hidden, Long version) {}

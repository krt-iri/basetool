package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/** Data transfer record carrying User Reference payload. */
public record UserReferenceDto(
    UUID id, String username, String displayName, String effectiveName, Integer rank) {}

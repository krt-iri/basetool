package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

public record UserReferenceDto(
        UUID id,
        String username,
        String displayName,
        String effectiveName,
        Integer rank
) {}

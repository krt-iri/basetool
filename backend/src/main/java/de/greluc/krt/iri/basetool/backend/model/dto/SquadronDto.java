package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

public record SquadronDto(
        UUID id,
        String name,
        String shorthand,
        String description,
        Boolean active,
        Long version
) {}

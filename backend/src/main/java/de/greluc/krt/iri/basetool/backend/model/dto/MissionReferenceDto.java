package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

public record MissionReferenceDto(
        UUID id,
        String name,
        String status
) {}

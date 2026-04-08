package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

public record StarSystemDto(
        UUID id,
        Integer idSystem,
        String name,
        String description,
        Boolean isAvailableLive,
        String wiki,
        String jurisdictionName,
        String factionName,
        Long version
) {}

package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Star System payload. */
public record StarSystemDto(
    UUID id,
    Integer idSystem,
    String name,
    String description,
    Boolean isAvailableLive,
    String wiki,
    String jurisdictionName,
    String factionName,
    Long version) {}

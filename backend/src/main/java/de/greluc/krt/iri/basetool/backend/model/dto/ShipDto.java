package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Ship payload. */
public record ShipDto(
    UUID id,
    String name,
    ShipTypeDto shipType,
    String insurance,
    LocationDto location,
    boolean fitted,
    UserDto owner,
    SquadronReferenceDto owningSquadron,
    Long version) {}

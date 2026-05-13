package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

public record ShipDto(
    UUID id,
    String name,
    ShipTypeDto shipType,
    String insurance,
    LocationDto location,
    boolean fitted,
    UserDto owner,
    Long version) {}

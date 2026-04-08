package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

public record ShipDto(
        UUID id,
        String name,
        ShipTypeDto shipType,
        String insurance,
        LocationDto location,
        Boolean fitted,
        UserDto owner,
        Long version
) {
}

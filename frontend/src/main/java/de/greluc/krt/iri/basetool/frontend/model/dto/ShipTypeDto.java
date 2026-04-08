package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

public record ShipTypeDto(
        UUID id,
        String name,
        ManufacturerDto manufacturer,
        String description,
        Integer scu,
        boolean hidden
) {
}

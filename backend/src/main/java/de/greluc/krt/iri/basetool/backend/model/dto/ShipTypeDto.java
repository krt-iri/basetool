package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Ship Type payload. */
public record ShipTypeDto(
    UUID id,
    String name,
    ManufacturerDto manufacturer,
    String description,
    Integer scu,
    boolean hidden) {}

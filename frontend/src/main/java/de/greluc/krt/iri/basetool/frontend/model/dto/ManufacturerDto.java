package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Manufacturer payload. */
public record ManufacturerDto(
    UUID id,
    String name,
    String abbreviation,
    String nickname,
    String wiki,
    String description,
    boolean hidden) {}

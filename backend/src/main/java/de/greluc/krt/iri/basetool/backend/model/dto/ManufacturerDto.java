package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

public record ManufacturerDto(
    UUID id,
    String name,
    String abbreviation,
    String nickname,
    String wiki,
    String description,
    boolean hidden) {}

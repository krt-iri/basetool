package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Terminal payload. */
public record TerminalDto(
    UUID id,
    String name,
    String nickname,
    String starSystemName,
    String planetName,
    String cityName,
    String spaceStationName,
    boolean hidden) {}

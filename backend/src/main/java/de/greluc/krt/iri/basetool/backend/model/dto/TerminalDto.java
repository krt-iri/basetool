package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

public record TerminalDto(
        UUID id,
        String name,
        String nickname,
        String starSystemName,
        String planetName,
        String cityName,
        String spaceStationName,
        boolean hidden
) {
}

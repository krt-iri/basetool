package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.List;
import java.util.UUID;

public record MissionUnitDto(
    UUID id,
    String name,
    ShipTypeDto shipType,
    ShipDto ship,
    Double frequency,
    Boolean highValueUnit,
    List<MissionCrewDto> crew) {}

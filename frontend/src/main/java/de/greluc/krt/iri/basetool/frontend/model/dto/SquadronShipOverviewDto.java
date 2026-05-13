package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

public record SquadronShipOverviewDto(
    ShipTypeDto shipType, long count, long fittedCount, List<SquadronShipDetailDto> details) {}

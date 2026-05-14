package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/** Data transfer record carrying Squadron Ship Overview payload. */
public record SquadronShipOverviewDto(
    ShipTypeDto shipType, long count, long fittedCount, List<SquadronShipDetailDto> details) {}

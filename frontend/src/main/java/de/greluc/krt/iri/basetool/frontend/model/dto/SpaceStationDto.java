package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend-side projection of the {@code SpaceStation} payload returned by the backend's
 * UEX-overrides endpoint. Mirrors {@code
 * de.greluc.krt.iri.basetool.backend.model.dto.SpaceStationDto} exactly.
 *
 * @param id space station primary key
 * @param name canonical station name as supplied by UEX
 * @param starSystemName parent star system label
 * @param planetName parent planet label, or {@code null} for free-floating stations
 * @param hasLoadingDock current effective "has loading dock" value
 * @param hasLoadingDockOverridden whether {@code hasLoadingDock} is admin-pinned
 */
public record SpaceStationDto(
    UUID id,
    String name,
    String starSystemName,
    String planetName,
    Boolean hasLoadingDock,
    boolean hasLoadingDockOverridden) {}

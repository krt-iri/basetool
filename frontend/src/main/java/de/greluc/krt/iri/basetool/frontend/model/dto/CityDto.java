package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend-side projection of the {@code City} payload returned by the backend's UEX-overrides
 * endpoint. Mirrors {@code de.greluc.krt.iri.basetool.backend.model.dto.CityDto} exactly.
 *
 * @param id city primary key
 * @param name canonical city name as supplied by UEX
 * @param starSystemName parent star system label
 * @param planetName parent planet label
 * @param hasLoadingDock current effective "has loading dock" value
 * @param hasLoadingDockOverridden whether {@code hasLoadingDock} is admin-pinned
 */
public record CityDto(
    UUID id,
    String name,
    String starSystemName,
    String planetName,
    Boolean hasLoadingDock,
    boolean hasLoadingDockOverridden) {}

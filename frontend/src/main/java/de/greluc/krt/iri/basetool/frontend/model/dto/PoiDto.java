package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend-side projection of the {@code Poi} payload returned by the backend's UEX-overrides
 * endpoint. Mirrors {@code de.greluc.krt.iri.basetool.backend.model.dto.PoiDto} exactly.
 *
 * @param id POI primary key
 * @param name canonical POI name as supplied by UEX
 * @param starSystemName parent star system label
 * @param planetName parent planet label, or {@code null}
 * @param hasLoadingDock current effective "has loading dock" value
 * @param hasLoadingDockOverridden whether {@code hasLoadingDock} is admin-pinned
 */
public record PoiDto(
    UUID id,
    String name,
    String starSystemName,
    String planetName,
    Boolean hasLoadingDock,
    boolean hasLoadingDockOverridden) {}

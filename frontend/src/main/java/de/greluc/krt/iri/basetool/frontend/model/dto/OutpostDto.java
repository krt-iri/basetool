package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend-side projection of the {@code Outpost} payload returned by the backend's UEX-overrides
 * endpoint. Mirrors {@code de.greluc.krt.iri.basetool.backend.model.dto.OutpostDto} exactly.
 *
 * @param id outpost primary key
 * @param name canonical outpost name as supplied by UEX
 * @param starSystemName parent star system label
 * @param planetName parent planet label
 * @param hasLoadingDock current effective "has loading dock" value
 * @param hasLoadingDockOverridden whether {@code hasLoadingDock} is admin-pinned
 */
public record OutpostDto(
    UUID id,
    String name,
    String starSystemName,
    String planetName,
    Boolean hasLoadingDock,
    boolean hasLoadingDockOverridden) {}

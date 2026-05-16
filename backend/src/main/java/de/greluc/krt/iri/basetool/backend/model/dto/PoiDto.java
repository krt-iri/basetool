package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

/**
 * Outbound projection of a {@code Poi} aggregate as needed by the admin UEX-overrides page.
 *
 * <p>Only the columns the admin UI actually renders or acts on are exposed: identifier, name,
 * star-system / planet labels for context, the effective {@code hasLoadingDock} value and the
 * {@code hasLoadingDockOverridden} flag that tells the UI whether the value is admin-pinned or
 * UEX-managed.
 *
 * @param id POI primary key
 * @param name canonical POI name as supplied by UEX
 * @param starSystemName parent star system label (denormalised by UEX)
 * @param planetName parent planet label, or {@code null} for system-level POIs
 * @param hasLoadingDock current effective "has loading dock" value (UEX-sourced or admin-pinned)
 * @param hasLoadingDockOverridden {@code true} iff an admin has pinned {@code hasLoadingDock}
 */
public record PoiDto(
    UUID id,
    String name,
    String starSystemName,
    String planetName,
    Boolean hasLoadingDock,
    boolean hasLoadingDockOverridden) {}

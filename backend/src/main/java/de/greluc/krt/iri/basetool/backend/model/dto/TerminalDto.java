package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

/**
 * Outbound projection of a {@code Terminal} aggregate.
 *
 * <p>Mirrors the persisted entity plus the two admin-override flags {@code
 * hasLoadingDockOverridden} / {@code isAutoLoadOverridden}. The override booleans tell the admin UI
 * whether the corresponding value column is currently pinned by an officer (so the next UEX sweep
 * leaves it untouched) or is being managed by the upstream feed.
 *
 * @param id terminal primary key
 * @param name canonical terminal name as supplied by UEX
 * @param nickname short label shown in dropdowns / tables
 * @param starSystemName parent star system label (denormalised by UEX)
 * @param planetName parent planet label, or {@code null} for orbital / Lagrange terminals
 * @param cityName parent city label when the terminal lives in a city, otherwise {@code null}
 * @param spaceStationName parent station label when the terminal lives on a station, otherwise
 *     {@code null}
 * @param hasLoadingDock current effective "has loading dock" value (UEX-sourced or admin-pinned)
 * @param isAutoLoad current effective "is auto-load" value (UEX-sourced or admin-pinned)
 * @param hasLoadingDockOverridden {@code true} iff an admin has pinned {@code hasLoadingDock}; the
 *     UEX sync will skip writing the value column until this flag is cleared
 * @param isAutoLoadOverridden {@code true} iff an admin has pinned {@code isAutoLoad}; the UEX sync
 *     will skip writing the value column until this flag is cleared
 * @param hidden whether the terminal is hidden from regular dropdowns / lists
 */
public record TerminalDto(
    UUID id,
    String name,
    String nickname,
    String starSystemName,
    String planetName,
    String cityName,
    String spaceStationName,
    Boolean hasLoadingDock,
    Boolean isAutoLoad,
    boolean hasLoadingDockOverridden,
    boolean isAutoLoadOverridden,
    boolean hidden) {}

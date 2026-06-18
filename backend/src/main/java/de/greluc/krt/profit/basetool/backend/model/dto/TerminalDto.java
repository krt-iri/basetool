/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.backend.model.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound projection of a {@code Terminal} aggregate.
 *
 * <p>Mirrors the persisted entity plus the two admin-override flags {@code
 * hasLoadingDockOverridden} / {@code isAutoLoadOverridden} and the raw UEX-side mirror columns. The
 * override booleans tell the admin UI whether the corresponding value column is currently pinned by
 * an officer (so the next UEX sweep leaves it untouched) or is being managed by the upstream feed;
 * the {@code uex*} fields carry the last value UEX actually reported for the terminal, which the
 * admin UI displays alongside the override so officers can decide whether a pin still needs to be
 * in place.
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
 * @param uexHasLoadingDock raw {@code has_loading_dock} value from the most recent UEX sweep,
 *     written unconditionally by the sync — may differ from {@code hasLoadingDock} when an admin
 *     pin is active; {@code null} when the terminal has not been synced yet
 * @param uexIsAutoLoad raw {@code is_auto_load} value from the most recent UEX sweep; same caveats
 *     as {@code uexHasLoadingDock}
 * @param uexSyncedAt UTC instant of the most recent UEX sweep that touched the terminal, or {@code
 *     null} if it has never been synced
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
    Boolean uexHasLoadingDock,
    Boolean uexIsAutoLoad,
    Instant uexSyncedAt,
    boolean hidden) {}

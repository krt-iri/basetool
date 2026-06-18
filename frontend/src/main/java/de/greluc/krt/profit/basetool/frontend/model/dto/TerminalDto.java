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

package de.greluc.krt.profit.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Frontend-side projection of the {@code Terminal} payload returned by the backend.
 *
 * <p>Mirrors {@code de.greluc.krt.profit.basetool.backend.model.dto.TerminalDto} exactly so the
 * frontend's WebClient can deserialise the JSON without a translation step. The two boolean flags
 * {@code hasLoadingDockOverridden} / {@code isAutoLoadOverridden} drive the admin override UI: when
 * {@code true}, the corresponding value column is admin-pinned and the next UEX sweep leaves it
 * alone. The {@code uex*} fields carry the most recent raw UEX-reported state for the terminal so
 * the admin page can display what UEX currently claims even while a pin is active.
 *
 * @param id terminal primary key
 * @param name canonical terminal name as supplied by UEX
 * @param nickname short label shown in dropdowns / tables
 * @param starSystemName parent star system label
 * @param planetName parent planet label, or {@code null} for orbital terminals
 * @param cityName parent city label, or {@code null}
 * @param spaceStationName parent station label, or {@code null}
 * @param hasLoadingDock current effective "has loading dock" value
 * @param isAutoLoad current effective "is auto-load" value
 * @param hasLoadingDockOverridden whether {@code hasLoadingDock} is admin-pinned
 * @param isAutoLoadOverridden whether {@code isAutoLoad} is admin-pinned
 * @param uexHasLoadingDock raw {@code hasLoadingDock} value from the most recent UEX sweep, or
 *     {@code null} when the terminal has not been synced yet
 * @param uexIsAutoLoad raw {@code isAutoLoad} value from the most recent UEX sweep, or {@code null}
 *     when the terminal has not been synced yet
 * @param uexSyncedAt UTC instant of the last UEX sweep that touched the terminal, or {@code null}
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

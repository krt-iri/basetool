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

import java.util.List;

/**
 * The complete Profit-Bereich org chart as one nested read model: the Bereichsleitung on top, then
 * the profit-eligible Staffeln and Spezialkommandos below. Returned by {@code GET
 * /api/v1/org-chart} to every authenticated user; the inline admin editor mutates it one position
 * at a time through the position endpoints.
 *
 * @param areaLeadership the Bereichsleitung tier; never {@code null}.
 * @param squadrons the profit-eligible Staffeln, ordered by name; never {@code null}, possibly
 *     empty.
 * @param specialCommands the profit-eligible Spezialkommandos, ordered by name; never {@code null},
 *     possibly empty.
 */
public record OrgChartDto(
    AreaLeadershipDto areaLeadership,
    List<SquadronChartDto> squadrons,
    List<SpecialCommandChartDto> specialCommands) {}

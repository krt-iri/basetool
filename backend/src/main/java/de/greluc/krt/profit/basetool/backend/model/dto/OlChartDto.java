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
import java.util.UUID;

/**
 * The Organisationsleitung tier at the very top of the multi-Bereich org chart (epic #692,
 * REQ-ORG-018): the OL org unit's identity plus its OL_MEMBER nodes. Carried as its own record
 * (rather than a bare node list) so the chart can caption the tier with the OL's name and the
 * inline admin editor can stamp a new OL_MEMBER against {@code orgUnitId} even while the tier is
 * still empty.
 *
 * <p>The whole record is {@code null} on the parent {@link OrgChartDto} when no active OL exists,
 * so the chart simply omits the OL tier and degrades to the per-Bereich / legacy view.
 *
 * @param orgUnitId the OL org unit's id (the scope new OL_MEMBER positions are stamped against).
 * @param name the OL's display name (the tier caption).
 * @param shorthand the OL's short tag.
 * @param members the OL members (OL_MEMBER positions); never {@code null}, possibly empty.
 */
public record OlChartDto(
    UUID orgUnitId, String name, String shorthand, List<OrgChartNodeDto> members) {}

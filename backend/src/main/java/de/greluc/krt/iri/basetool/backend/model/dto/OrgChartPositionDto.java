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

package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.OrgChartPositionType;
import java.util.UUID;

/**
 * Flat wire shape of a single {@code org_chart_position}, returned by the create / update endpoints
 * so the caller gets the server-stamped id and the bumped {@code version} back. The nested read
 * model uses {@link OrgChartNodeDto} instead; this flat form keeps the write responses simple.
 *
 * @param id the position id (server-stamped on create).
 * @param positionType the functional rank held.
 * @param orgUnitId owning Staffel/SK id, or {@code null} for an area-leadership position.
 * @param userId id of the user holding the position, or {@code null} for a still-leaderless
 *     Kommando ({@code COMMAND_LEAD}).
 * @param userName the user's effective display name, or {@code null} for a leaderless Kommando.
 * @param name the Kommando's display name, or {@code null} (set only on a {@code COMMAND_LEAD}
 *     row).
 * @param parentId id of the parent position (deputy → Kommandoleiter, Ensign → its parent), or
 *     {@code null} for a root position.
 * @param sortIndex stable display order within the sibling group.
 * @param version optimistic-lock version after the write.
 */
public record OrgChartPositionDto(
    UUID id,
    OrgChartPositionType positionType,
    UUID orgUnitId,
    UUID userId,
    String userName,
    String name,
    UUID parentId,
    int sortIndex,
    Long version) {}

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

import de.greluc.krt.profit.basetool.backend.model.OrgChartPositionType;
import java.util.UUID;

/**
 * One filled person-node in the rendered org chart: the user who holds a single functional-rank
 * position. Carried inside the nested {@link OrgChartDto} tree (as the Bereichsleiter, a
 * Kommandoleiter, an Ensign, …). The {@code version} and {@code positionId} travel to the client so
 * the inline admin editor can reassign or remove the exact row without a re-fetch.
 *
 * @param positionId id of the underlying {@code org_chart_position} row; the handle every edit /
 *     remove / add-child action targets.
 * @param positionType the functional rank held in this node.
 * @param userId id of the user holding the position; preselects the user in the reassign picker.
 * @param userName the user's effective display name (display name, falling back to username).
 * @param sortIndex stable display order within the sibling group.
 * @param version optimistic-lock version of the row, echoed back on edit to detect concurrent
 *     changes.
 */
public record OrgChartNodeDto(
    UUID positionId,
    OrgChartPositionType positionType,
    UUID userId,
    String userName,
    int sortIndex,
    Long version) {}

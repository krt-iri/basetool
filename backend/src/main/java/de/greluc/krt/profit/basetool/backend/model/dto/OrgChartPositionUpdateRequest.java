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

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Inbound payload for {@code PUT /api/v1/org-chart/positions/{id}} — edits an existing position.
 * The functional rank, the scope (OrgUnit) and the parent are immutable after creation — moving a
 * position to a different parent is done by removing it and re-adding it — so only the holder, the
 * Kommando name and the display order may change. A {@code null} field on the wire means "leave
 * unchanged"; for {@code name}, an empty/blank string clears it back to the unnamed state.
 *
 * @param userId the new holder, or {@code null} to keep the current one. Assigning a holder to a
 *     leaderless Kommando is just a reassign of its {@code COMMAND_LEAD} row through this field.
 * @param name the new Kommando name, or {@code null} to keep the current one; only honoured for a
 *     {@code COMMAND_LEAD} row (rejected otherwise). A blank value clears the name.
 * @param sortIndex the new display order, or {@code null} to keep the current one.
 * @param version current optimistic-lock version held by the client; required so concurrent edits
 *     surface as a 409.
 */
public record OrgChartPositionUpdateRequest(
    UUID userId, @Size(max = 120) String name, Integer sortIndex, @NotNull Long version) {}

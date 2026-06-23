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

import java.util.UUID;

/**
 * Frontend mirror of a Kommandogruppe (epic #800, REQ-ROLE-003), decoded as part of the Leitung
 * view. Descriptive sub-structure of a Staffel; the rank-bearing authority sits on the membership
 * row, not here.
 *
 * @param id the group id.
 * @param squadronId the owning Staffel.
 * @param name the group name.
 * @param sortIndex the display order within the Staffel.
 * @param version the optimistic-lock version, echoed back on a rename / reorder.
 */
public record KommandoGroupDto(
    UUID id, UUID squadronId, String name, int sortIndex, Long version) {}

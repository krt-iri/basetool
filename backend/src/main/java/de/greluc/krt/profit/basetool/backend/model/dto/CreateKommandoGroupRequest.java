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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body to create a Kommandogruppe within a Staffel (epic #800, REQ-ROLE-003). The squadron
 * is taken from the path; at most four groups per squadron are allowed (enforced by the service
 * pre-check and the V185 DB trigger). The new group is appended at the end of the squadron's order.
 *
 * @param name the group's display name; required, 1–120 chars.
 */
public record CreateKommandoGroupRequest(@NotBlank @Size(max = 120) String name) {}

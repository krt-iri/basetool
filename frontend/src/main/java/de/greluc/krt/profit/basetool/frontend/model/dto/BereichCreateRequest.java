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
 * Create payload for a Bereich, posted by the admin org-structure page and relayed to the backend
 * {@code POST /api/v1/org-hierarchy/bereiche} (epic #692, REQ-ORG-014). Its JSON shape is a subset
 * of the backend {@code BereichDto} (server-stamped id/active/version omitted): the backend
 * validates {@code name}/{@code shorthand} as non-blank.
 *
 * @param name the Bereich's display name; required, unique across all org units.
 * @param shorthand the Bereich's short tag; required.
 * @param description free-form text; nullable.
 * @param parentOrgUnitId the owning Organisationsleitung's id, or {@code null} to leave unparented.
 * @param department the Kartell department / Bereichsfarbe name, or {@code null} to leave untinted.
 */
public record BereichCreateRequest(
    String name, String shorthand, String description, UUID parentOrgUnitId, String department) {}

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
import java.util.UUID;

/**
 * Request body for the set-parent endpoint (epic #692, REQ-ORG-014): assigns the org unit
 * identified by the path id to a new parent in the hierarchy — a Staffel/SK to a Bereich, or a
 * Bereich to the Organisationsleitung. The parent kind is validated server-side (and pinned by the
 * {@code validate_org_unit_parent} V164 trigger as the DB backstop).
 *
 * @param parentOrgUnitId the new parent's id, or {@code null} to detach the unit from its current
 *     parent (e.g. before moving it). When non-null, its kind must match the child's level.
 * @param version the <em>child</em> org unit's optimistic-lock version; required so a concurrent
 *     edit surfaces as a 409 instead of silently overwriting another admin's change.
 */
public record OrgUnitParentUpdateRequest(UUID parentOrgUnitId, @NotNull Long version) {}

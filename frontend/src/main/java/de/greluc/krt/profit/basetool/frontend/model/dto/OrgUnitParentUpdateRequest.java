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
 * Set-parent payload, sent by the admin org-structure page and relayed to the backend {@code PATCH
 * /api/v1/org-hierarchy/org-units/{id}/parent} (epic #692, REQ-ORG-014). The backend validates the
 * kind pairing (Staffel/SK → Bereich, Bereich → OL) and the optimistic-lock {@code version}.
 *
 * @param parentOrgUnitId the new parent's id, or {@code null} to detach the unit.
 * @param version the child unit's current optimistic-lock version.
 */
public record OrgUnitParentUpdateRequest(UUID parentOrgUnitId, Long version) {}

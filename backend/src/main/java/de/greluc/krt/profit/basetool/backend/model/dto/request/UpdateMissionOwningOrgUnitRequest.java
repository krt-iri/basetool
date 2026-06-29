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

package de.greluc.krt.profit.basetool.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Request DTO for reassigning the owning org unit of a mission (REQ-ORG-018, ADR-0050).
 *
 * <p>{@code owningOrgUnitId} is the id of the target {@code OrgUnit} (Staffel, Spezialkommando,
 * Bereich or Organisationsleitung) the mission should be re-homed to, or {@code null} to make the
 * mission an <em>ownerless leadership mission</em>. The target is validated server-side against the
 * caller's assignable-org-unit scope ({@code OwnerScopeService.resolveReassignTargetOrgUnit}): a
 * non-admin may only pick a unit they belong to or may edit, and may only choose {@code null} when
 * they hold no membership at all.
 *
 * <p>{@code version} must match the current {@code Mission.owningOrgUnitVersion} (NOT the global
 * {@code Mission.version}) to prevent lost updates on concurrent reassignments; a mismatch surfaces
 * as HTTP 409.
 *
 * @param owningOrgUnitId the target org-unit id, or {@code null} for an ownerless mission.
 * @param version the expected {@code Mission.owningOrgUnitVersion} echoed back from the rendered
 *     page.
 */
public record UpdateMissionOwningOrgUnitRequest(
    @Nullable UUID owningOrgUnitId, @NotNull Long version) {}

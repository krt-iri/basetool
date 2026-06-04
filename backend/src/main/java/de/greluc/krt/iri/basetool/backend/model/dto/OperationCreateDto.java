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

import de.greluc.krt.iri.basetool.backend.model.OperationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Data transfer record carrying Operation Create payload.
 *
 * <p>R5.d.e added the trailing {@link #owningOrgUnitId} picker output. When present, the service
 * layer routes the stamp through {@code OwnerScopeService.resolveSquadronForPickerOutput} so the
 * picked OrgUnit is validated against the caller's memberships (rejecting Spezialkommando
 * selections with 400 until the destructive cleanup release loosens NOT NULL on the legacy {@code
 * owning_squadron_id} column). When {@code null}, the service preserves the legacy "stamp from
 * {@code OwnerScopeService.currentSquadron()}" path that scope-resolves the active Staffel from the
 * caller's persistent home or the admin's {@code X-Active-Squadron-Id} header.
 */
public record OperationCreateDto(
    @NotBlank String name,
    String description,
    @NotNull OperationStatus status,
    @Nullable UUID owningOrgUnitId) {}

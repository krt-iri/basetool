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

package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Data transfer record carrying Refinery Order Store Item payload.
 *
 * <p>{@code owningOrgUnitId} is the per-item owning-OrgUnit picker output forwarded to the backend
 * store endpoint; {@code null} when the receiving member belongs to a single OrgUnit (the backend
 * auto-stamps) and otherwise the OrgUnit the user picked in the store dialog.
 */
public record RefineryOrderStoreItemDto(
    UUID materialId,
    UUID locationId,
    Integer quality,
    Double amount,
    UUID userId,
    UUID jobOrderId,
    String note,
    UUID owningOrgUnitId) {}

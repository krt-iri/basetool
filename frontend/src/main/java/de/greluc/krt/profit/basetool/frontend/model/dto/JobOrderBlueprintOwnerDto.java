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

import java.util.List;

/**
 * Frontend mirror of the backend {@code JobOrderBlueprintOwnerDto}: one member of the responsible
 * org unit who owns the blueprint for at least one of an item order's required products, with the
 * display names of exactly those required products they hold.
 *
 * @param ownerName the member's effective display name
 * @param ownedProductNames the required products this member owns the blueprint for
 * @param orgUnitMember {@code true} when this owner is a member of the order's responsible org
 *     unit; {@code false} when they appear only via global blueprint sharing (REQ-INV-018), which
 *     the template marks with a discreet "not a unit member" hint
 */
public record JobOrderBlueprintOwnerDto(
    String ownerName, List<String> ownedProductNames, boolean orgUnitMember) {}

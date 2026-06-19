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

import java.util.List;

/**
 * One member row of the item job-order blueprint-coverage view: a member of the responsible org
 * unit who owns the blueprint for at least one of the order's required items, together with the
 * display names of exactly those required products they own.
 *
 * <p>Carries the member's display name only — never the Keycloak {@code sub} or e-mail — mirroring
 * {@link BlueprintOverviewOwnerDto}, so the view cannot leak account identifiers. The owned-product
 * list is restricted to the order's required products: a member's other blueprints are not exposed.
 *
 * @param ownerName the member's effective display name (display name, or username fallback)
 * @param ownedProductNames the display names of the order's required products this member owns the
 *     blueprint for, sorted case-insensitively; never {@code null} and never empty (members owning
 *     none of the required blueprints are omitted from the view entirely)
 * @param orgUnitMember {@code true} when this owner is a member of the order's responsible org
 *     unit; {@code false} when they appear only because they opted into global blueprint sharing
 *     (REQ-INV-018), so the UI can mark them with a discreet "not a unit member" hint
 */
public record JobOrderBlueprintOwnerDto(
    String ownerName, List<String> ownedProductNames, boolean orgUnitMember) {}

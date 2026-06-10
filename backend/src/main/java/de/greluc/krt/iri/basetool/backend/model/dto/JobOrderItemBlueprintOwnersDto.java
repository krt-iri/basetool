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

import java.util.List;

/**
 * Blueprint-coverage view for an {@code ITEM} job order: who among the members of the order's
 * responsible (processing) squadron/SK owns the blueprints for the items the order requests, and
 * which of those required blueprints each member holds.
 *
 * <p>This payload is members-only — its endpoint is gated by {@code
 * @ownerScopeService.canSeeJobOrderBlueprintOwners}, which is stricter than the order's own
 * visibility (an SK-responsible order is publicly readable, but this coverage view is restricted to
 * SK members + admins). It is empty for {@code MATERIAL} orders, which request raw materials rather
 * than crafted items.
 *
 * @param requiredBlueprints one row per distinct required product, each with the count of
 *     responsible-org-unit members owning its blueprint (zero = coverage gap); sorted by product
 *     name. Never {@code null}.
 * @param owners the members owning the blueprint for at least one required product, each with the
 *     required products they hold; sorted by member name. Never {@code null}.
 */
public record JobOrderItemBlueprintOwnersDto(
    List<JobOrderRequiredBlueprintDto> requiredBlueprints,
    List<JobOrderBlueprintOwnerDto> owners) {}

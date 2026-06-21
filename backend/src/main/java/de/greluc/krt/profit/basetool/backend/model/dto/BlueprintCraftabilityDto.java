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
import java.util.UUID;

/**
 * Craftability of one of the caller's owned blueprints, computed from the caller's own stock
 * (#781): how many times it can be crafted, what output quality the user's material would deliver,
 * and what is missing. Keyed by the owned {@link
 * de.greluc.krt.profit.basetool.backend.model.PersonalBlueprint} id so the frontend can decorate
 * the matching master-list row and detail pane.
 *
 * <p>Every count is given twice — {@code craftable} from "My Inventory" stock alone and {@code
 * craftableWithRefinery} with the caller's {@code OPEN}/{@code IN_PROGRESS} refinery yield folded
 * in — so the refinery toggle switches client-side without a refetch. A row whose {@code
 * craftableWithRefinery} exceeds {@code craftable} is craftable only thanks to the refinery.
 *
 * <p>Only RESOURCE ingredients are evaluated; {@code hasItemIngredients} flags a recipe that also
 * needs discrete game items (marked "not evaluated" in the UI), and {@code hasResourceIngredients}
 * is {@code false} for an ITEM-only recipe that cannot be assessed at all. {@code recipeResolved}
 * is {@code false} when no active SC Wiki recipe backs the owned product.
 *
 * @param blueprintId the owned blueprint's id
 * @param recipeResolved whether an active recipe was found for the owned product
 * @param hasItemIngredients whether the recipe also needs ITEM (discrete game-item) ingredients
 * @param hasResourceIngredients whether the recipe has any RESOURCE ingredient to evaluate
 * @param craftable how many crafts the inventory stock alone allows
 * @param craftableWithRefinery how many crafts the inventory plus open refinery yield allows
 * @param limitingMaterialName the RESOURCE commodity capping the inventory-only count, or {@code
 *     null} when nothing limits it or no recipe resolved
 * @param limitingMaterialNameWithRefinery the commodity capping the refinery-included count, or
 *     {@code null}
 * @param groups per-requirement-group overlay, in recipe order (drives the slot quality sliders)
 * @param materials per-material breakdown (required / available / effective quality / missing)
 */
public record BlueprintCraftabilityDto(
    UUID blueprintId,
    boolean recipeResolved,
    boolean hasItemIngredients,
    boolean hasResourceIngredients,
    int craftable,
    int craftableWithRefinery,
    String limitingMaterialName,
    String limitingMaterialNameWithRefinery,
    List<CraftabilityGroupDto> groups,
    List<CraftabilityMaterialDto> materials) {}

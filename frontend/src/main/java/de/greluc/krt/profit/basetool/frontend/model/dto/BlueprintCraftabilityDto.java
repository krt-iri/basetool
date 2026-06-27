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
import java.util.UUID;

/**
 * Frontend mirror of the backend {@code BlueprintCraftabilityDto}: the craftability of one owned
 * blueprint computed from the caller's own stock (#781), keyed by the owned blueprint id so the
 * view can decorate the matching master-list row and detail pane. Counts are given both from
 * inventory alone and with the open refinery yield folded in.
 *
 * @param blueprintId the owned blueprint's id
 * @param recipeResolved whether an active recipe backs the owned product
 * @param hasItemIngredients whether the recipe still needs an ITEM ingredient that is not evaluated
 *     (a craftable sub-assembly or an unresolved item), marked "not evaluated" in the UI
 * @param hasResourceIngredients whether the recipe has any evaluable material requirement (a
 *     RESOURCE commodity or a PIECE-material-bridged ITEM)
 * @param craftable how many crafts the inventory stock alone allows
 * @param craftableWithRefinery how many crafts inventory plus open refinery yield allows
 * @param limitingMaterialName the commodity capping the inventory-only count, or {@code null}
 * @param limitingMaterialNameWithRefinery the commodity capping the refinery-included count, or
 *     {@code null}
 * @param groups per-requirement-group overlay, in recipe order
 * @param materials per-material breakdown
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

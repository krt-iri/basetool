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
 * Boundary DTO carrying the SC Wiki recipe graph of a single owned blueprint's product (#327) so
 * the Personal Inventory blueprint view can show its ingredients ("Zutaten") and the per-quality
 * stat contributions ("Stat-Beitrag nach Zutat-Qualität") — mirroring the admin {@code
 * /admin/blueprints} page but scoped to one product the caller owns.
 *
 * <p>Ownership is modelled per <em>product</em>, and a product can collapse several SC Wiki recipe
 * variants (see {@code BlueprintProductService}); the returned graph is that of a single
 * representative recipe, with {@link #variantCount} reporting how many variants share the product
 * so the UI can flag the shown recipe as an example when it is greater than one.
 *
 * @param productName canonical display name of the product (master spelling, else the owned-row
 *     name when the master no longer lists the product)
 * @param variantCount number of SC Wiki recipe variants collapsing into this product (0 when
 *     unresolved)
 * @param requirementGroups the representative recipe's build slots, each with its ingredients and
 *     per-quality stat modifiers; empty when the recipe has no grouped requirements
 * @param ingredients flat ingredient list of the representative recipe, used as the legacy fallback
 *     when {@link #requirementGroups} is empty
 */
public record PersonalBlueprintRecipeResponse(
    String productName,
    int variantCount,
    List<BlueprintRequirementGroupDto> requirementGroups,
    List<BlueprintRequirementIngredientDto> ingredients) {}

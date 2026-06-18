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

/**
 * One required-product coverage row of the item job-order blueprint-coverage view: a distinct
 * <em>variant family</em> the order requests an item line to be crafted into, with the count of
 * members of the responsible org unit who own the blueprint for any member of that family. A {@code
 * ownerCount} of zero marks a coverage gap — nobody in the processing squadron/SK currently holds a
 * matching blueprint for this required item or any of its variants.
 *
 * <p>The {@code productKey} is the variant family key (see {@code BlueprintVariantFamilyResolver}):
 * a base item and its cosmetic variants ({@code Fresnel Energy LMG} ↔ {@code Fresnel "Molten"
 * Energy LMG}) collapse onto one key, while magazines stay atomic. The {@code productName} is the
 * ordered item's display name (the variant the line requested, if any). {@code variantInclusive} is
 * {@code true} when the count folds in cosmetic variants of the ordered item (every non-magazine
 * row) — the template surfaces a "counts variants" hint for those rows.
 *
 * @param productKey the variant family key the order's item line resolves to
 * @param productName the display name of the ordered item (a variant name when a variant was
 *     ordered)
 * @param ownerCount the number of distinct responsible-org-unit members owning the blueprint for
 *     this item or any variant of it; zero indicates a coverage gap
 * @param variantInclusive whether the count includes owners of cosmetic variants (false for an
 *     atomic magazine row, which is matched exactly)
 */
public record JobOrderRequiredBlueprintDto(
    String productKey, String productName, int ownerCount, boolean variantInclusive) {}

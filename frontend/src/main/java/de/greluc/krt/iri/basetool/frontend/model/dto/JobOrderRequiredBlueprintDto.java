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

/**
 * Frontend mirror of the backend {@code JobOrderRequiredBlueprintDto}: one required variant family
 * of an item order with the count of responsible-org-unit members owning the blueprint for the
 * ordered item or any of its cosmetic variants. An {@code ownerCount} of zero marks a coverage gap
 * the template highlights; {@code variantInclusive} drives the "counts variants" hint.
 *
 * @param productKey the variant family key the order's item line resolves to
 * @param productName the display name of the ordered item (a variant name when a variant was
 *     ordered)
 * @param ownerCount the number of responsible-org-unit members owning the blueprint for this item
 *     or any variant of it
 * @param variantInclusive whether the count includes owners of cosmetic variants (false for an
 *     atomic magazine row)
 */
public record JobOrderRequiredBlueprintDto(
    String productKey, String productName, int ownerCount, boolean variantInclusive) {}

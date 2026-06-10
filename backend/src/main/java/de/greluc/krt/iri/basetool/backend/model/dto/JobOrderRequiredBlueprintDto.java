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

/**
 * One required-product coverage row of the item job-order blueprint-coverage view: a distinct
 * product the order requests an item line to be crafted into, with the count of members of the
 * responsible org unit who own its blueprint. A {@code ownerCount} of zero marks a coverage gap —
 * nobody in the processing squadron/SK currently holds the blueprint for this required item.
 *
 * <p>The {@code productKey} is the normalized blueprint product identity (see {@code
 * BlueprintNameNormalizer}) used to match against {@code PersonalBlueprint.productKey}; the {@code
 * productName} is the human-readable item name for display.
 *
 * @param productKey the normalized product key the order's item line resolves to
 * @param productName the display name of the required item
 * @param ownerCount the number of distinct responsible-org-unit members owning the blueprint for
 *     this product; zero indicates a coverage gap
 */
public record JobOrderRequiredBlueprintDto(String productKey, String productName, int ownerCount) {}

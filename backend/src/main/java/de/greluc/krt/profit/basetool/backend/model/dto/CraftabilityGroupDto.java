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

import java.util.UUID;

/**
 * Craftability overlay for one recipe requirement group (build slot), emitted in the same order as
 * {@link PersonalBlueprintRecipeResponse#requirementGroups()} so the frontend can zip the two by
 * index (#781). It carries the {@code effectiveQuality} that the slot's stat-modifier slider should
 * default to — the quality the user's own stock would actually deliver, rather than the band
 * maximum — given as inventory-only and refinery-included variants for the instant toggle.
 *
 * <p>{@code materialId} names the slot's limiting RESOURCE commodity (its first resolved RESOURCE
 * ingredient) so the frontend can join to the matching {@link CraftabilityMaterialDto}; it is
 * {@code null} for a slot with no resolved RESOURCE ingredient (e.g. an ITEM-only slot).
 *
 * @param materialId the slot's limiting RESOURCE commodity id, or {@code null} when none is
 *     resolved
 * @param effectiveQuality the effective quality from inventory alone, or {@code null} when no
 *     qualifying stock exists
 * @param effectiveQualityWithRefinery the effective quality including the open refinery yield, or
 *     {@code null}
 */
public record CraftabilityGroupDto(
    UUID materialId, Double effectiveQuality, Double effectiveQualityWithRefinery) {}

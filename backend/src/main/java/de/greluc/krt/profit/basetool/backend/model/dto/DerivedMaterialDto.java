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

import de.greluc.krt.profit.basetool.backend.model.QualityRequirement;

/**
 * One resolved material requirement in an item-order derivation preview: the material, the quantity
 * needed for the previewed amount (unit from {@code material.quantityType}), and the quality the UI
 * should pre-select ({@code GOOD} when the blueprint ingredient's {@code minQuality} is 700+, else
 * {@code NONE}). The requester may override {@code defaultQuality} per material before submitting.
 *
 * @param material the required material (carries {@code quantityType} for unit-aware display)
 * @param requiredQuantity quantity needed for the previewed amount
 * @param defaultQuality the pre-selected quality choice derived from the ingredient
 */
public record DerivedMaterialDto(
    MaterialDto material, Double requiredQuantity, QualityRequirement defaultQuality) {}

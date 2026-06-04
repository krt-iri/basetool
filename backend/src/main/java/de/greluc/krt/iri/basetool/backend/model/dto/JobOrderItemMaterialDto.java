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

import de.greluc.krt.iri.basetool.backend.model.QualityRequirement;
import java.util.UUID;

/**
 * One snapshotted material requirement of a single ordered item line. The {@code requiredQuantity}
 * unit is interpreted from {@code material.quantityType} (SCU fractional vs PIECE whole-number);
 * {@code qualityRequirement} is the requester's per-order Gut/Keine choice for this material.
 *
 * @param id the requirement row's primary key
 * @param material the required material, with its {@code quantityType} for unit-aware formatting
 * @param requiredQuantity the amount needed for the owning line (already scaled by the line's
 *     quantity)
 * @param qualityRequirement {@code GOOD} (700+) or {@code NONE} (no floor)
 * @param version optimistic-lock version
 */
public record JobOrderItemMaterialDto(
    UUID id,
    MaterialDto material,
    Double requiredQuantity,
    QualityRequirement qualityRequirement,
    Long version) {}

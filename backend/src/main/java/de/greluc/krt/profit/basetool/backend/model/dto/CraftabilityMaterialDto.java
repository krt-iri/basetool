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
 * Per-material craftability breakdown for one RESOURCE commodity a blueprint consumes (#781). The
 * recipe's RESOURCE ingredient lines are aggregated per {@code materialId}: {@code requiredScu} is
 * the total SCU one craft needs of this material, and every availability / quality figure is given
 * twice — once over the caller's "My Inventory" stock alone, and once with the caller's {@code
 * OPEN}/{@code IN_PROGRESS} refinery yield folded in — so the frontend refinery toggle can switch
 * instantly without a refetch.
 *
 * <p>Only stock at or above {@code qualityFloor} counts toward availability and the effective
 * quality: the floor is the stricter of the ingredient's own {@code min_quality} and the lowest
 * quality at which none of the slot's stat modifiers would worsen the output (REQ-INV-019). The
 * effective quality is the SCU-weighted average of the best-quality stock consumed first, over one
 * craft's requirement.
 *
 * @param materialId the commodity's id
 * @param materialName the commodity's display name
 * @param requiredScu the SCU one craft needs of this material (summed across the recipe's lines)
 * @param qualityFloor the lowest qualifying quality (max of {@code min_quality} and the
 *     no-degradation floor); {@code 0} when nothing constrains it
 * @param availableScu qualifying SCU in "My Inventory" stock alone
 * @param availableScuWithRefinery qualifying SCU including the open refinery yield
 * @param effectiveQuality SCU-weighted average quality of the stock consumed for one craft from
 *     inventory alone, or {@code null} when no qualifying stock exists
 * @param effectiveQualityWithRefinery the same including the open refinery yield, or {@code null}
 * @param missingScu SCU short of one craft from inventory alone ({@code 0} when sufficient)
 * @param missingScuWithRefinery SCU short of one craft including refinery yield ({@code 0} when
 *     sufficient)
 * @param craftable how many crafts this material alone allows from inventory ({@code
 *     floor(available / required)})
 * @param craftableWithRefinery the same including the open refinery yield
 */
public record CraftabilityMaterialDto(
    UUID materialId,
    String materialName,
    double requiredScu,
    int qualityFloor,
    double availableScu,
    double availableScuWithRefinery,
    Double effectiveQuality,
    Double effectiveQualityWithRefinery,
    double missingScu,
    double missingScuWithRefinery,
    int craftable,
    int craftableWithRefinery) {}

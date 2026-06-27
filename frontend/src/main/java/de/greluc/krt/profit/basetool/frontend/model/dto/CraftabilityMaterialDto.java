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

import java.util.UUID;

/**
 * Frontend mirror of the backend {@code CraftabilityMaterialDto}: the per-material craftability
 * breakdown for one material a blueprint consumes (#781) — a RESOURCE commodity or a
 * PIECE-material-bridged ITEM ingredient (a hand-mined gem, ADR-0046). Every availability/quality
 * figure is given twice (inventory alone and with the open refinery yield) so the refinery toggle
 * switches client-side.
 *
 * @param materialId the material's id
 * @param materialName the material's display name
 * @param requiredScu the SCU one craft needs of this material
 * @param qualityFloor the lowest qualifying quality applied
 * @param availableScu qualifying SCU in "My Inventory" stock alone
 * @param availableScuWithRefinery qualifying SCU including the open refinery yield
 * @param effectiveQuality SCU-weighted quality from inventory alone, or {@code null}
 * @param effectiveQualityWithRefinery the same including the open refinery yield, or {@code null}
 * @param missingScu amount short of one craft from inventory alone (in the {@code quantityType}
 *     unit)
 * @param missingScuWithRefinery amount short of one craft including refinery yield
 * @param craftable crafts this material alone allows from inventory
 * @param craftableWithRefinery crafts this material alone allows including refinery yield
 * @param quantityType the material's quantity unit ({@code "SCU"} / {@code "PIECE"}); the {@code
 *     *Scu} figures are in this unit, so the UI labels them "SCU" or "Stück" accordingly
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
    int craftableWithRefinery,
    String quantityType) {}

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

import java.util.List;

/**
 * One blueprint ITEM (sub-assembly) ingredient surfaced to the create UI as an adoptable line: the
 * sub-item to craft, the quantity needed for the previewed amount, and the blueprints that can
 * produce it (for the per-line blueprint pick when adopted). Adopting a suggestion adds a child
 * item line whose own RESOURCE ingredients then feed the order's material aggregation (issue #304
 * decision 1).
 *
 * @param gameItem the sub-assembly item to craft
 * @param quantity whole-unit count needed for the previewed amount
 * @param blueprints the blueprints that produce {@code gameItem}; empty when none are known
 */
public record SubAssemblySuggestionDto(
    GameItemReferenceDto gameItem, Integer quantity, List<BlueprintReferenceDto> blueprints) {}

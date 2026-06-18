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

package de.greluc.krt.profit.basetool.backend.model.scwiki;

/**
 * Discriminator for a {@link BlueprintIngredient}: whether the line consumes a bulk commodity or a
 * discrete game item (SC_WIKI_SYNC_PLAN.md §3.3 / §6.3.3).
 *
 * <p>The Wiki blueprint payload carries a {@code kind} field per ingredient: {@code "resource"}
 * references a commodity by {@code resource_type_uuid} (quantity in SCU); {@code "item"} references
 * a game item by {@code item_uuid} (quantity in whole units).
 */
public enum BlueprintIngredientKind {

  /** A bulk commodity ingredient — resolves to a {@code material}, quantity in SCU. */
  RESOURCE,

  /** A discrete game-item ingredient — resolves to a {@code game_item}, quantity in units. */
  ITEM
}

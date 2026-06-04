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

package de.greluc.krt.iri.basetool.backend.model;

/**
 * Quality a derived material requirement of an item order must be satisfied with. Chosen by the
 * requester per material at order-creation time (defaulting from the blueprint ingredient's {@code
 * minQuality}), so the same item can be ordered with different quality demands in different orders.
 * Deliberately binary — the item-order flow does not expose arbitrary quality floors, only the
 * refining-grade threshold versus "no floor".
 */
public enum QualityRequirement {

  /** Requires refining-grade quality (700+); only inventory at or above that tier satisfies it. */
  GOOD,

  /** No quality floor ("Keine"); inventory of any quality satisfies the requirement. */
  NONE
}

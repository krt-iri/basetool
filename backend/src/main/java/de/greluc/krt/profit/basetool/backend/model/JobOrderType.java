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

package de.greluc.krt.profit.basetool.backend.model;

/**
 * Discriminates the two kinds of {@link JobOrder}. A {@link #MATERIAL} order lists raw materials to
 * procure and deliver (the legacy behaviour); an {@link #ITEM} order lists finished items to
 * produce, from which the required materials are derived and aggregated via blueprint data.
 * Existing rows are backfilled to {@link #MATERIAL} by migration V123, so the discriminator never
 * widens the behaviour of historical orders.
 */
public enum JobOrderType {

  /** Order requesting raw materials directly (commodity + quantity + minimum quality). */
  MATERIAL,

  /** Order requesting finished items; needed materials are derived from each item's blueprint. */
  ITEM
}

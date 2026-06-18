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
 * Tracks which external catalogues have written to a {@link GameItem} or {@link ShipType} row.
 *
 * <p>R2 only emits {@link #UEX_ONLY} (the UEX item sync is the first writer). R3+ flips {@code
 * UEX_ONLY → BOTH} when the Wiki commodity / item sync finds a match by {@code external_uuid}; the
 * Wiki sync also creates fresh {@link #WIKI_ONLY} rows for items UEX does not carry (variant skins,
 * paints UEX skipped). See SC_WIKI_SYNC_PLAN.md §6.3.1 / §6.5 for the full transition table.
 *
 * <p>Distinct from {@link MaterialSourceSystem}: that enum carries an additional {@link
 * MaterialSourceSystem#MANUAL} value for admin-created commodity rows, which has no analogue in the
 * item / vehicle domain (those rows are external-catalogue-only).
 */
public enum GameItemSourceSystem {

  /** Only the UEX sync has written to this row. R2 default. */
  UEX_ONLY,

  /** Only the SC Wiki sync has written to this row. Reached in R3+ for Wiki-only variants. */
  WIKI_ONLY,

  /** Both syncs have written to this row; the canonical fields use the §6.3.3 tie-breaker. */
  BOTH,

  /**
   * The KRT P4K Reader catalog import has touched this row. Unlike {@link #UEX_ONLY} / {@link
   * #WIKI_ONLY} / {@link #BOTH}, P4K participation is normally signalled by a non-null {@code
   * p4k_synced_at} rather than by flipping {@code source_systems} — the importer enriches existing
   * rows in place and does not rewrite their owning source. This value exists so the CHECK
   * constraint accepts it and a future flow may set it explicitly if the policy changes.
   */
  P4K
}

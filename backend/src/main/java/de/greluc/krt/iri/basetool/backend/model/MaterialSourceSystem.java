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
 * Tracks which external catalogues a {@link Material} row has been seen in. Written by the UEX and
 * (R3+) SC Wiki sync services; admin-created rows carry {@link #MANUAL} so the UI can badge them.
 *
 * <p>Transitions during sync (see SC_WIKI_SYNC_PLAN.md §6.1):
 *
 * <ul>
 *   <li>{@link #UEX_ONLY} → {@link #BOTH} when the R3 Wiki commodity sync finds a match for an
 *       existing UEX row.
 *   <li>{@link #WIKI_ONLY} → {@link #BOTH} when a subsequent UEX commodity sync picks up a row Wiki
 *       imported first.
 *   <li>{@link #MANUAL} is sticky: an admin-created row keeps the badge even after a sync run
 *       writes UEX or Wiki columns on top of it (see {@code UexCommodityService}'s manual-entry
 *       handover).
 * </ul>
 *
 * <p>R1 only writes {@link #UEX_ONLY} (every existing row at migration time). The other values
 * become reachable in R3 (Wiki commodity sync) and R8 (post-soak V116 backfill of {@code
 * is_manual_entry → MANUAL}).
 */
public enum MaterialSourceSystem {

  /** The row has only been seen in UEX's commodity catalogue. Default for every pre-R3 row. */
  UEX_ONLY,

  /**
   * The row has only been seen in the SC Wiki commodity catalogue. Wiki-only rows are inserted with
   * {@code is_visible = false} so they don't appear in trading flows until an admin reviews them
   * (SC_WIKI_SYNC_PLAN.md §4.3).
   */
  WIKI_ONLY,

  /** Both UEX and SC Wiki carry the row; merged via UUID, alias, or canonical-name match. */
  BOTH,

  /**
   * Admin-created row that has not (yet) been linked to either UEX or SC Wiki. The post-R8 V116
   * backfill flips every legacy {@code is_manual_entry=true} row into this value.
   */
  MANUAL
}

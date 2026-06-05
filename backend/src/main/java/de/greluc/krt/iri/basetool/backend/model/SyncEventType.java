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
 * Catalogue of findings a sync run can record into {@link ExternalSyncReport}, per
 * SC_WIKI_SYNC_PLAN.md §8.8.
 *
 * <p>Each value is emitted by a specific sync (noted per-constant); the {@code event_type} column
 * stores the enum name. Optimistic-lock conflicts between a sync write and a concurrent admin edit
 * are intentionally <b>not</b> recorded here: the per-row handler logs and skips the row, which
 * re-syncs on the next cycle. The {@code §5.4} "retry-once" budget was never needed — each
 * scheduler runs single-threaded on its own {@code @Async} executor, so an intra-run race on the
 * same row cannot occur.
 */
public enum SyncEventType {

  /** Wiki commodity sync: a row was dropped by the §8.9 hard-junk name filter. */
  SKIP_JUNK,

  /**
   * Wiki commodity sync: no UEX match found; a new {@code WIKI_ONLY} row was inserted invisible.
   */
  CREATED_WIKI_ONLY,

  /** UEX item sync (R2+): an item had no Wiki cross-reference. */
  CREATED_UEX_ONLY,

  /**
   * Wiki commodity sync: the row's name matches the §4.3 "items masquerading as commodities" set
   * (Ace Interceptor Helmet, MedGel, …). Inserted invisible so an admin can decide.
   */
  LOOKS_LIKE_ITEM,

  /** Wiki commodity sync: the row was linked to a local material via the alias table. */
  LINKED_VIA_ALIAS,

  /** Wiki item sync (R4): joined an existing UEX row by shared {@code external_uuid}. */
  LINKED_VIA_UUID,

  /** Wiki commodity sync: the canonical name hit more than one UEX row; no link made. */
  MULTI_MATCH_AMBIGUOUS,

  /** Wiki blueprint sync (R4): an ingredient resource / item could not be resolved. */
  UNRESOLVED_INGREDIENT,

  /** Both sides (R4+): UEX and Wiki disagree on the manufacturer for the same UUID. */
  MANUFACTURER_MISMATCH,

  /**
   * Wiki manufacturer reconciliation (R6): a Wiki manufacturer was linked to an existing UEX
   * manufacturer row for the first time — {@code scwiki_uuid} / {@code scwiki_code} were stamped.
   */
  MANUFACTURER_LINKED,

  /** Wiki item sync (R4): a UUID present in UEX is absent on the Wiki. */
  WIKI_MISSING,

  /** Vehicle backfill: a {@code ship_type.name} matched more than one UEX vehicle. */
  BACKFILL_AMBIGUOUS,

  /**
   * Wiki item full backfill (Weg 2): a Wiki item with no existing UUID match was merged into a
   * uuid-less {@code UEX_ONLY} row found by exact {@code uex_slug} / name, backfilling that row's
   * {@code external_uuid} and flipping {@code UEX_ONLY → BOTH} instead of inserting a duplicate
   * {@code WIKI_ONLY} row.
   */
  LINKED_VIA_NAME,

  /**
   * Any sync (UEX or SC Wiki): a once-per-run summary line whose {@code detail} carries the run's
   * tallies (rows visited / created / updated / soft-deleted). Emitted unconditionally so a run is
   * always visible on the admin sync-report page even when it produced no other findings — the UEX
   * item sync is the first emitter.
   */
  SYNC_RUN_SUMMARY
}

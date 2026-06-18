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
 * Summary of a P4K catalog import — the result of both the preview ({@code dryRun = true}, no
 * writes) and the apply ({@code dryRun = false}) paths of {@code P4kImportService}. The two paths
 * compute the same counts; preview simply never persists.
 *
 * <p>Each per-type {@link Counts} block partitions that type's processed records: every inbound
 * record with a usable join key lands in exactly one of {@link Counts#matched}, {@link
 * Counts#created} or {@link Counts#unmatched}. {@link Counts#uuidBackfilled}, {@link
 * Counts#uuidConflicts} and {@link Counts#enriched} are independent sub-tallies <em>over the
 * matched subset</em> (a single matched row can contribute to several of them). {@code blueprints}
 * additionally drives {@link #ingredientsResolved}.
 *
 * <p><b>Seeding.</b> {@link #seedingEnabled} reports whether this run created brand-new rows for
 * genuinely-new game data (records the catalog carries that match no local row and pass the
 * real-record filter). A preview always analyses seeding and reports the <em>potential</em> {@link
 * Counts#created}; an apply only inserts when the admin opted in (else those records fall through
 * to {@link Counts#unmatched} and {@code created} is zero).
 *
 * @param dryRun {@code true} for a preview (nothing was written), {@code false} for an applied
 *     import
 * @param seedingEnabled {@code true} when this run seeds new rows — always {@code true} for a
 *     preview (it shows the potential), and equal to the admin's opt-in on an apply
 * @param manufacturers manufacturer reconciliation counts
 * @param items item reconciliation counts
 * @param ships ship reconciliation counts
 * @param commodities commodity reconciliation counts
 * @param blueprints blueprint reconciliation counts
 * @param ingredientsResolved number of previously-unresolved existing {@code blueprint_ingredient}
 *     rows whose FK this import resolved via the stored Wiki UUID
 * @param runId the sync-report run id stamped on this import's audit events; {@code null} for a
 *     preview (which emits no audit rows)
 */
public record P4kImportResultDto(
    boolean dryRun,
    boolean seedingEnabled,
    Counts manufacturers,
    Counts items,
    Counts ships,
    Counts commodities,
    Counts blueprints,
    int ingredientsResolved,
    UUID runId) {

  /**
   * Per-type reconciliation tally. {@link #matched} + {@link #created} + {@link #unmatched} equals
   * the number of inbound records of that type with a usable join key; {@link #uuidBackfilled},
   * {@link #uuidConflicts} and {@link #enriched} are independent sub-tallies over the matched rows.
   *
   * @param matched inbound records resolved to exactly one existing local row
   * @param uuidBackfilled matched rows whose null canonical UUID ({@code external_uuid} / {@code
   *     scwiki_uuid}) this import filled from the P4K GUID (a {@code LINKED_VIA_NAME} event was
   *     logged — the row was reached by the name/slug fallback, so the UUID was missing)
   * @param uuidConflicts matched rows whose existing canonical UUID was non-null and differed from
   *     the P4K GUID (kept, not overwritten; a {@code BACKFILL_AMBIGUOUS} event was logged)
   * @param enriched matched rows on which at least one fill-if-null field (description, mass,
   *     manufacturer, …) was written
   * @param created inbound records that matched no local row but passed the real-record filter and
   *     were inserted as a new {@code source = P4K} row (a would-be insert in a preview; an actual
   *     insert only on an apply with seeding opted in)
   * @param unmatched inbound records that resolved to no row (or to more than one, i.e. ambiguous)
   *     and were neither enriched nor seeded
   */
  public record Counts(
      int matched,
      int uuidBackfilled,
      int uuidConflicts,
      int enriched,
      int created,
      int unmatched) {}
}

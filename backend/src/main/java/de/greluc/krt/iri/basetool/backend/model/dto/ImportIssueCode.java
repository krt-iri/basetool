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

package de.greluc.krt.iri.basetool.backend.model.dto;

/**
 * Machine-readable reason attached to an {@link ImportIssueDto} of a refinery screenshot import
 * draft (#434, plan §7.5). The set is part of the cross-module contract: the frontend translates
 * each code via its own {@code refineryImport.issue.*} message keys, so renaming a constant is a
 * breaking API change.
 */
public enum ImportIssueCode {

  /**
   * No master-data material matched the raw screen name through any stage (canonical, alias,
   * suffix, fuzzy). The draft row is kept with a {@code null} material; ranked {@code suggestions}
   * accompany the issue so the review UI can offer a pick list.
   */
  UNMATCHED_MATERIAL,

  /**
   * A material was matched only by the fuzzy stage (score at or above the accept threshold but not
   * an exact/alias/suffix hit). Never silently accepted — the issue carries the match score as
   * {@code confidence} plus the ranked alternatives so the user verifies the pick.
   */
  LOW_CONFIDENCE_MATERIAL,

  /**
   * The matched input material has no admin-curated {@code refinedMaterial} link, so {@code
   * outputMaterial} stays empty. Informational: the existing create path falls back to the input
   * material itself, and gaps are expected (the link is neither UEX- nor Wiki-synced).
   */
  NO_REFINED_MATERIAL,

  /**
   * The row's quality lies outside the savable 0..1000 range (likely a VLM misread). The value is
   * kept un-clamped in the draft so the user sees what was read; the review form forces a
   * correction before the order can be saved.
   */
  OUT_OF_RANGE_QUALITY,

  /**
   * {@code rawLocationName} was null (normal for pre-cropped panel input — the location sits in the
   * terminal header outside the panel) or did not resolve to a refinery-equipped location.
   */
  UNRESOLVED_LOCATION,

  /** {@code rawMethodName} did not resolve to a known refining method (case-insensitive). */
  UNRESOLVED_METHOD,

  /**
   * The row's REFINE toggle was OFF (typically the INERT MATERIALS aggregate) — intentionally not
   * added to the draft, because the create path requires a positive output quantity.
   */
  SKIPPED_REFINE_OFF,

  /**
   * The row carried a zero input or (quoted) zero output quantity and was not added to the draft —
   * distinct from {@link #UNQUOTED_ROW}, whose fix is re-capturing after GET QUOTE.
   */
  SKIPPED_ZERO_QTY,

  /**
   * The row's YIELD cell was un-quoted ({@code "--"}, {@code outputQuantity == null}) — the
   * screenshot was taken before pressing GET QUOTE. The row cannot be drafted; re-capture fixes it.
   */
  UNQUOTED_ROW,

  /**
   * Every row of the order (or the order itself per the producer's {@code quoted} flag) is in the
   * pre-GET-QUOTE state — nothing can be pre-filled. Blocking: the user must press GET QUOTE in
   * game and re-capture.
   */
  UNQUOTED_ORDER,

  /**
   * A panel-header total ({@code IN MANIFEST} vs the sum of all row quantities, or {@code TO
   * REFINE} vs the sum of refine-ON row quantities) did not reconcile — a scrolled screenshot may
   * be missing from the capture set.
   */
  SUM_MISMATCH,

  /**
   * The extract carried more than one order; v1 processes only {@code orders[0]} and ignores the
   * rest. Informational.
   */
  MULTIPLE_ORDERS_TRUNCATED,

  /**
   * Reserved for content-level panel-type problems in future schema versions. In v1 an unsupported
   * {@code panelType} on {@code orders[0]} is an envelope-level 400, so this code is not emitted
   * yet — it stays in the contract for forward compatibility.
   */
  UNSUPPORTED_PANEL_TYPE
}

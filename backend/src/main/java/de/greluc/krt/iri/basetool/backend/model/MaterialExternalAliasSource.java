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
 * Identifies which external catalogue a {@link MaterialExternalAlias} row maps onto. The same local
 * {@link Material} can carry one UEX alias and one SC Wiki alias side-by-side; the unique
 * constraint on {@code (source_system, external_name)} keeps the namespace per-source.
 */
public enum MaterialExternalAliasSource {

  /** UEX (uexcorp.space) commodity name. Used by the R6+ UEX commodity sync as a fallback match. */
  UEX,

  /**
   * SC Wiki (api.star-citizen.wiki) commodity name. The R3 Wiki commodity sync consults this set
   * after a direct UUID match fails — see SC_WIKI_SYNC_PLAN.md §8.1.1 step 2.
   */
  SCWIKI,

  /**
   * Material name as the Star Citizen refinement-terminal SETUP screen renders it (verbatim,
   * including {@code "(ORE)"} suffixes or game-UI truncation). Consulted by the refinery screenshot
   * import (#434) as matching stage 2, after the canonical-name fold fails — admins curate these
   * rows at {@code /admin/material-aliases}. The V146 migration widens the V108 CHECK constraint
   * for this value.
   */
  REFINERY_SCREEN
}

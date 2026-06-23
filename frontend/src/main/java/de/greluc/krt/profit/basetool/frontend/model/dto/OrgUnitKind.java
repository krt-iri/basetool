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

package de.greluc.krt.profit.basetool.frontend.model.dto;

/**
 * Frontend mirror of the backend {@code OrgUnitKind} enum — discriminator for the four tenant
 * subtypes carried by the {@code org_unit} table (Squadron, Spezialkommando, Bereich and
 * Organisationsleitung). The frontend uses the enum to branch on the membership rendering (Staffel
 * vs SK chip variant) and to filter client-side without a round-trip back to the backend.
 *
 * <p>The string values must match the backend enum's {@code name()} output verbatim so JSON
 * deserialisation of {@code OrgUnitMembershipDto} and {@code LeitungUnitDto} resolves the {@code
 * kind} field cleanly. This mirror MUST carry every value the backend can emit: the org-hierarchy
 * kinds {@link #BEREICH} and {@link #ORGANISATIONSLEITUNG} (epic #692) are surfaced by the Leitung
 * view, and a missing constant fails deserialisation of the whole response.
 */
public enum OrgUnitKind {
  /** Staffel — the original tenant kind that has driven the multi-tenancy work since Phase 1. */
  SQUADRON,

  /** Spezialkommando — the second tenant kind introduced by the Spezialkommando R2.a slice. */
  SPECIAL_COMMAND,

  /** Bereich — the area tier one level above Staffeln and Spezialkommandos (epic #692). */
  BEREICH,

  /** Organisationsleitung — the top tier above every Bereich (epic #692). */
  ORGANISATIONSLEITUNG
}

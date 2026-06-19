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

/**
 * The role a member holds within a Bereichsleitung (epic #692, REQ-ORG-017). Each value maps to
 * exactly one of the {@code is_bereichsleiter} / {@code is_bereichskoordinator} / {@code
 * is_bereichsoperator} flags on the member's {@code org_unit_membership} row; a Bereichsleitung
 * member carries exactly one of these roles. All three confer the same cascading,
 * officer-equivalent reach over the Bereich's Staffeln/SKs (REQ-ORG-015) — the distinction is
 * organisational, not a permission tier.
 */
public enum BereichLeadershipRole {

  /** Bereichsleiter — the head of the Bereich ({@code is_bereichsleiter}). */
  LEITER,

  /** Bereichskoordinator — an area coordinator ({@code is_bereichskoordinator}). */
  KOORDINATOR,

  /** Bereichsoperator — an area operator ({@code is_bereichsoperator}). */
  OPERATOR
}

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
 * Discriminator for {@link OrgUnit}: which concrete kind of tenant a row in the {@code org_unit}
 * table represents.
 *
 * <p>The enum values match the string literals that Flyway migration V94 (CHECK constraint), V95
 * (denormalised {@code kind} column on {@code org_unit_membership} kept in sync by the {@code
 * sync_org_unit_membership_kind} trigger), and the Hibernate {@code @DiscriminatorValue} on the
 * {@link OrgUnit} hierarchy all rely on. Changing or reordering the names here without coordinating
 * a Flyway migration would silently mis-map existing rows — keep this enum and the database CHECK /
 * trigger / discriminator strings synchronised.
 *
 * <p>Rationale for the two kinds:
 *
 * <ul>
 *   <li>{@link #SQUADRON} — the original "Staffel" tenant boundary that has driven the
 *       multi-tenancy work since Phase 1 (see {@code MULTI_SQUADRON_PLAN.md}). A user belongs to at
 *       most one Squadron. Squadrons may run the promotion subsystem.
 *   <li>{@link #SPECIAL_COMMAND} — added by the Spezialkommando extension (R2.a, see {@code
 *       SPEZIALKOMMANDO_PLAN.md}). A user may belong to any number of Special Commands in addition
 *       to (or instead of) a Squadron. Special Commands never carry the promotion subsystem; this
 *       invariant is enforced at the database layer via the {@code
 *       chk_org_unit_promotion_only_squadron} CHECK constraint and additionally in the {@link
 *       SpecialCommand} entity defaults.
 * </ul>
 */
public enum OrgUnitKind {

  /**
   * The classic squadron tenant — Staffel in the German domain language. Mapped to {@code
   * org_unit.kind = 'SQUADRON'} via the {@code @DiscriminatorValue} on the eventual {@code
   * Squadron} subclass (R2.b); until then, {@code SQUADRON}-discriminated rows live in the table
   * but no Java subclass owns them at the JPA layer (only the pre-existing {@link Squadron} entity
   * mapped to the legacy {@code squadron} table reads/writes that data).
   */
  SQUADRON,

  /**
   * The Spezialkommando tenant — a cross-cutting unit that members may join on top of their
   * Squadron membership, or instead of one. Mapped to {@code org_unit.kind = 'SPECIAL_COMMAND'} via
   * {@code @DiscriminatorValue} on {@link SpecialCommand}. Permanently barred from the promotion
   * subsystem by the database CHECK constraint introduced in V94.
   */
  SPECIAL_COMMAND
}

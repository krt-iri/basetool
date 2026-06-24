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
 * The kind of audience a {@link BankAccountViewGrant} opens balance + read-only-detail access to
 * (REQ-BANK-035). Persisted as {@code VARCHAR(16)} via {@code @Enumerated(STRING)}; the V189 {@code
 * chk_bank_view_grant_kind} / {@code chk_bank_view_grant_payload} CHECKs mirror this set and which
 * columns each kind populates.
 */
public enum BankAccountViewGranteeKind {

  /**
   * Every member of the owning org unit who holds the {@link BankAccountViewGrant#getRoleCode()}
   * {@link MembershipRole} on that unit (e.g. {@code ENSIGN} of the owning Staffel). Used for
   * org-unit accounts (Staffel/SK/Bereich), which have an owning unit and thus sub-ranks.
   */
  MEMBERSHIP_ROLE,

  /**
   * Every holder of the global role named by {@link BankAccountViewGrant#getRoleCode()} (e.g.
   * {@code OFFICER}). Used for {@link BankAccountType#SPECIAL} accounts, which have no owning org
   * unit and therefore no membership sub-ranks.
   */
  GLOBAL_ROLE,

  /** Exactly one named user, referenced by {@link BankAccountViewGrant#getGranteeUserId()}. */
  USER,

  /**
   * All members of the owning org unit (org-unit accounts) or all KRT members ({@link
   * BankAccountType#SPECIAL} accounts) — evaluated by account type. Carries no role/user.
   */
  ALL_MEMBERS
}

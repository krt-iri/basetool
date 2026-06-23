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
 * The functional leadership rank a user holds on a single {@link OrgUnitMembership} row (epic #800,
 * REQ-ROLE-001). This is the unified successor to the five mutually-exclusive boolean leadership
 * flags (<code>is_lead</code>, <code>is_bereichsleiter</code>, <code>is_bereichskoordinator</code>,
 * <code>is_bereichsoperator</code>, <code>is_ol_member</code>): exactly one rank per seat, kind-
 * scoped by the {@code chk_org_unit_membership_role_kind} CHECK so a squadron rank can only sit on
 * a {@code SQUADRON} membership, an area rank on a {@code BEREICH} membership, and so on.
 *
 * <p>The rank is the <em>source of truth</em> for what a user may do in an org unit; the org chart
 * (<code>org_chart_position</code>) only mirrors it descriptively and grants nothing
 * (REQ-ROLE-006). The orthogonal capability flags {@link OrgUnitMembership#isLogistician()} /
 * {@link OrgUnitMembership#isMissionManager()} are deliberately NOT folded into this enum — a
 * member can be a logistician without holding any rank.
 *
 * <p>During the additive soak (epic #800 Phase 1) the five boolean columns still exist and remain
 * the authoritative read path; this column is written in lockstep (dual-write) and consumed only
 * from Phase 2 onward. The boolean columns drop in the Phase-5 destructive cleanup.
 */
public enum MembershipRole {

  /**
   * No leadership rank — an ordinary member of the org unit (or a flag-less Bereich/OL seat). Valid
   * on any {@link OrgUnitKind}. Grants nothing beyond plain membership; the default for every row.
   */
  MEMBER,

  /**
   * Staffelleiter — overall lead of a single Staffel. Valid only on a {@code SQUADRON} membership;
   * at most one per squadron. Confers officer-equivalent reach over its own squadron
   * (REQ-ROLE-002).
   */
  STAFFELLEITER,

  /**
   * Kommandoleiter — leads one {@link KommandoGroup} within a Staffel. Valid only on a {@code
   * SQUADRON} membership and must reference a {@link OrgUnitMembership#getKommandoGroup() command
   * group}; up to four per squadron. Confers officer-equivalent reach over its own squadron.
   */
  KOMMANDOLEITER,

  /**
   * Stellvertretender Kommandoleiter — deputy of a {@link KommandoGroup}'s {@link #KOMMANDOLEITER}.
   * Valid only on a {@code SQUADRON} membership and must reference a command group; at most one per
   * group. Confers officer-equivalent reach over its own squadron.
   */
  STELLV_KOMMANDOLEITER,

  /**
   * Ensign — junior squadron-leadership rank below the deputy. Valid only on a {@code SQUADRON}
   * membership; may reference a {@link KommandoGroup} or be {@code null} ("allgemein der
   * Staffelleitung"); up to four per squadron. Confers officer-equivalent reach over its own
   * squadron.
   */
  ENSIGN,

  /**
   * Bereichsleiter — overall lead of a Bereich. Valid only on a {@code BEREICH} membership; at most
   * one per Bereich. Confers cascading, officer-equivalent reach over the Bereich and its children
   * (REQ-ORG-015). Is also organisationally part of the Organisationsleitung, but that membership
   * is organisational only and does NOT widen reach org-wide (REQ-ROLE-005).
   */
  BEREICHSLEITER,

  /**
   * Bereichskoordinator — coordinator within a Bereich. Valid only on a {@code BEREICH} membership.
   * Same cascading, officer-equivalent reach over the Bereich's children as {@link #BEREICHSLEITER}
   * (per-rank differentiation is deferred to later, per-feature work).
   */
  BEREICHSKOORDINATOR,

  /**
   * Bereichsoperator — operator within a Bereich. Valid only on a {@code BEREICH} membership. Same
   * cascading, officer-equivalent reach over the Bereich's children as {@link #BEREICHSLEITER} for
   * now.
   */
  BEREICHSOPERATOR,

  /**
   * Member of the Organisationsleitung. Valid only on an {@code ORGANISATIONSLEITUNG} membership.
   * Confers cascading, officer-equivalent reach over <em>every</em> org unit (REQ-ORG-015) — never
   * admin rights.
   */
  OL_MEMBER,

  /**
   * SK-Leiter — lead of a Spezialkommando (the rank formerly modelled by {@code is_lead}). Valid
   * only on a {@code SPECIAL_COMMAND} membership. Confers logistician + mission-manager reach over
   * its own SK and the ability to manage that SK's members; does NOT cascade (SK-only).
   */
  SK_LEAD;

  /**
   * Whether this is one of the four in-squadron leadership ranks (Staffelleiter / Kommandoleiter /
   * stellv. Kommandoleiter / Ensign). These ranks are held BY squadron members and are exempt from
   * the "a leader holds no Staffel" rule (REQ-ORG-017); they confer own-squadron reach only.
   *
   * @return {@code true} for {@link #STAFFELLEITER}, {@link #KOMMANDOLEITER}, {@link
   *     #STELLV_KOMMANDOLEITER} and {@link #ENSIGN}; {@code false} otherwise.
   */
  public boolean isSquadronRank() {
    return this == STAFFELLEITER
        || this == KOMMANDOLEITER
        || this == STELLV_KOMMANDOLEITER
        || this == ENSIGN;
  }

  /**
   * Whether this is one of the three Bereich ranks (Bereichsleiter / -koordinator / -operator).
   *
   * @return {@code true} for {@link #BEREICHSLEITER}, {@link #BEREICHSKOORDINATOR} and {@link
   *     #BEREICHSOPERATOR}; {@code false} otherwise.
   */
  public boolean isAreaRank() {
    return this == BEREICHSLEITER || this == BEREICHSKOORDINATOR || this == BEREICHSOPERATOR;
  }

  /**
   * Whether this rank is an area rank or OL membership — the set used for the cartel-wide special-
   * account view and (from Phase 2) the {@code isAreaOrOlSeat} classifier. Excludes squadron ranks
   * and {@link #SK_LEAD}.
   *
   * @return {@code true} for the three area ranks and {@link #OL_MEMBER}; {@code false} otherwise.
   */
  public boolean isAreaOrOl() {
    return isAreaRank() || this == OL_MEMBER;
  }

  /**
   * Whether holding this rank makes the membership an oversight seat over its own org unit (any
   * rank except {@link #MEMBER}). From Phase 2 this is the home of the former {@code
   * isOversightSeat} classifier in {@code OwnerScopeService}.
   *
   * @return {@code true} for every rank except {@link #MEMBER}.
   */
  public boolean confersOwnLevelOversight() {
    return this != MEMBER;
  }

  /**
   * Whether this rank cascades reach downward to subordinate org units. Only area ranks (Bereich →
   * its Staffeln/SKs) and {@link #OL_MEMBER} (→ every org unit) cascade; squadron ranks and {@link
   * #SK_LEAD} stay own-unit only (REQ-ROLE-002, REQ-ORG-015/017).
   *
   * @return {@code true} for area ranks and {@link #OL_MEMBER}; {@code false} otherwise.
   */
  public boolean cascadesDownward() {
    return isAreaRank() || this == OL_MEMBER;
  }
}

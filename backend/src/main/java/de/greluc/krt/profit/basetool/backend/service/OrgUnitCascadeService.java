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

package de.greluc.krt.profit.basetool.backend.service;

import de.greluc.krt.profit.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.profit.basetool.backend.repository.OrgUnitRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the cascading-scope expansion of the org hierarchy (epic #692, REQ-ORG-015) in one
 * place, so the two consumers that need it — the request-scope resolver ({@link OwnerScopeService})
 * and the JWT authority converter ({@link
 * de.greluc.krt.profit.basetool.backend.config.CustomJwtGrantedAuthoritiesConverter}) — share a
 * single, independently-tested definition of "who reaches which org units".
 *
 * <p><b>The cascade rule.</b> A leadership membership confers officer-equivalent reach over the
 * units <em>below</em> it, mirroring the {@code ADMIN > OFFICER > LOGISTICIAN/MISSION_MANAGER}
 * chain one level higher in the tree:
 *
 * <ul>
 *   <li>A <b>Bereichsleitung</b> membership (any of {@code is_bereichsleiter} / {@code
 *       is_bereichskoordinator} / {@code is_bereichsoperator}) reaches the Bereich itself plus
 *       every direct child (its Staffeln + SKs). The three-level hierarchy (OL &gt; Bereich &gt;
 *       Staffel/SK) means the direct children are the whole subtree below a Bereich.
 *   <li>An <b>Organisationsleitung</b> membership ({@code is_ol_member}) reaches <em>every</em> org
 *       unit — OL is the only level that crosses Bereiche.
 *   <li>Every other membership (a plain Staffel/SK membership, a per-membership Logistician /
 *       MissionManager / SK-Lead flag, or a flag-less Bereich/OL seat) confers reach over its own
 *       org unit only — exactly today's behaviour. A flag-less Bereich seat is the organisational,
 *       chart-only Bereichsleitung membership an SK-Leiter would hold; it must NOT widen reach
 *       (REQ-ORG-017, owner decision Q1: SK-Leiter = SK-only reach).
 * </ul>
 *
 * <p><b>HARD INVARIANT (REQ-ORG-015).</b> The expansion is always a concrete, materialised set of
 * org-unit ids — even for an OL member, whose reach is the literal union of every org-unit id, not
 * an admin-all marker. OL/Bereich leadership therefore never inherits the admin carve-outs
 * (SK-lifecycle / promotion / ownerless-row access / admin-pin semantics keyed on {@code
 * adminAllScope} / {@code isAdmin()}). The cascade widens membership scope; it never grants admin
 * rights. The methods here are pure functions of their {@code memberships} argument plus the
 * persisted hierarchy, with no dependency on the security context, so they behave identically
 * whether invoked at authentication time (converter) or request-handling time (scope resolver).
 *
 * <p>Class-level {@code @Transactional(readOnly = true)} matches the other scope-resolution beans:
 * every repository call here is a read.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrgUnitCascadeService {

  private final OrgUnitRepository orgUnitRepository;

  /**
   * Resolves the full set of org-unit ids the holder of {@code memberships} has effective reach
   * over: every direct membership id, unioned with the {@linkplain
   * #cascadedOfficerReach(Collection) cascaded leadership reach}. This is the set the request-scope
   * resolver feeds into {@link ScopePredicate#memberOrgUnitIds()} so that list queries, per-row
   * {@code canSee*}/{@code canEdit*} gates and the Job-Order profit gate all cascade uniformly.
   *
   * <p>For a caller with no leadership flag this returns exactly the direct membership ids —
   * byte-for-byte today's behaviour — because {@link #cascadedOfficerReach(Collection)} is empty.
   *
   * @param memberships the caller's membership rows (Staffel / SK / Bereich / OL); never {@code
   *     null}, may be empty (anonymous / memberless caller → empty result).
   * @return the union of direct-membership ids and cascaded descendant ids; never {@code null},
   *     insertion-ordered for determinism.
   */
  @NotNull
  public Set<UUID> expandWithDescendants(@NotNull Collection<OrgUnitMembership> memberships) {
    Set<UUID> reach = new LinkedHashSet<>();
    for (OrgUnitMembership m : memberships) {
      reach.add(m.getId().getOrgUnitId());
    }
    reach.addAll(cascadedOfficerReach(memberships));
    return reach;
  }

  /**
   * Resolves only the org units the caller reaches <em>through a Bereich/OL leadership flag</em> —
   * excluding plain Staffel/SK memberships, which confer reach over their own org unit but no
   * cascade. This is the set the JWT converter mints contextual {@code LOGISTICIAN@<id>} / {@code
   * MISSION_MANAGER@<id>} authorities for, so a Bereichsleitung / OL member acts with
   * officer-equivalent authority in every subordinate unit (and in the Bereich itself, for the
   * Bereich-owned aggregates introduced in a later phase).
   *
   * <ul>
   *   <li>An OL membership short-circuits to <em>every</em> org-unit id (OL crosses Bereiche).
   *   <li>Otherwise each Bereich-leadership membership contributes the Bereich id plus its direct
   *       children (Staffeln + SKs).
   *   <li>A caller with no leadership flag yields the empty set.
   * </ul>
   *
   * @param memberships the caller's membership rows; never {@code null}.
   * @return the cascaded officer-equivalent reach; never {@code null}, possibly empty,
   *     insertion-ordered for determinism.
   */
  @NotNull
  public Set<UUID> cascadedOfficerReach(@NotNull Collection<OrgUnitMembership> memberships) {
    boolean olReach = memberships.stream().anyMatch(OrgUnitMembership::isOlMember);
    if (olReach) {
      // OL reach is the literal union of every org unit — materialised, never an admin-all marker.
      return new LinkedHashSet<>(orgUnitRepository.findAllOrgUnitIds());
    }
    Set<UUID> reach = new LinkedHashSet<>();
    for (OrgUnitMembership m : memberships) {
      if (m.isBereichsleiter() || m.isBereichskoordinator() || m.isBereichsoperator()) {
        UUID bereichId = m.getId().getOrgUnitId();
        reach.add(bereichId);
        reach.addAll(orgUnitRepository.findChildOrgUnitIds(bereichId));
      }
    }
    return reach;
  }
}

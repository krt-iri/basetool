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

package de.greluc.krt.profit.basetool.backend.support;

import de.greluc.krt.profit.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.profit.basetool.backend.model.Squadron;
import de.greluc.krt.profit.basetool.backend.repository.SquadronRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

/**
 * Single owner of the "name-sorted primary Staffel" definition introduced with REQ-ORG-017 (a
 * member may belong to up to two Staffeln). Given a user's {@code SQUADRON}-kind {@link
 * OrgUnitMembership} rows it resolves each to its {@link Squadron} and orders the result
 * case-insensitively by squadron name, so the <em>first</em> element is the deterministic
 * <b>primary</b> Staffel. Three call sites previously re-implemented this independently and had to
 * be kept byte-for-byte in agreement:
 *
 * <ul>
 *   <li>{@code OrgUnitMembershipService.findStaffelMembershipOrgUnitIds} (and its single-value
 *       {@code findStaffelMembershipOrgUnitId} / order-aligned {@code findExecutingStaffelForOrder}
 *       derivatives) — the authorization-gate accessors;
 *   <li>{@code UserMapper.resolveSquadrons} / {@code resolveSquadron} — the {@code
 *       UserDto.squadrons} / {@code UserDto.squadron} projections;
 *   <li>{@code OwnerScopeService} — the name-sorted primary fallback of its pin-aware
 *       current-Staffel resolution.
 * </ul>
 *
 * <p>Centralising the rule here means the primary definition lives in exactly one place; the call
 * sites keep only their own concerns (the {@code UserMapper} request-scoped membership memo, the
 * {@code OwnerScopeService} active-pin override and request cache).
 *
 * <p><b>Package placement.</b> This collaborator lives in the dependency-leaf {@code support}
 * package — depending only on {@code model} + {@code repository} — precisely so the {@code mapper}
 * and {@code service} layers can both reuse it without a {@code mapper} → {@code service} back-edge
 * (the {@code service} layer already depends on {@code mapper}, so that edge would close a package
 * cycle). The leaf placement is gate-enforced by {@code ArchitectureTest} ({@code
 * supportPackageMustStayADependencyLeaf} and {@code backendPackagesShouldBeFreeOfDependencyCycles},
 * ADR-0047).
 *
 * <p>A row whose squadron no longer resolves (a dangling membership) is skipped via {@link
 * SquadronRepository#findAllById(Iterable)}, so the resolver never throws on a bad-data edge case.
 * It carries no {@code @Transactional} of its own on purpose: it is a pure read helper that
 * participates in whichever transaction (if any) the caller already holds, matching how the three
 * call sites read the squadron table inline before the extraction.
 */
@Service
@RequiredArgsConstructor
public class StaffelMembershipResolver {

  private final SquadronRepository squadronRepository;

  /**
   * Resolves the given {@code SQUADRON}-kind membership rows to their owning {@link Squadron}
   * entities, sorted case-insensitively by squadron name so the first element is the deterministic
   * primary Staffel. Used where the caller needs the full squadron entities (e.g. to build the
   * {@code UserDto.squadrons} reference DTOs that carry name + shorthand). Dangling rows whose
   * squadron no longer exists are dropped by the batch load.
   *
   * @param squadronRows the user's {@code SQUADRON}-kind membership rows; never {@code null},
   *     possibly empty.
   * @return the owning squadrons, name-sorted (primary first); never {@code null}, possibly empty.
   */
  @NotNull
  public List<Squadron> resolveNameSortedStaffeln(@NotNull List<OrgUnitMembership> squadronRows) {
    if (squadronRows.isEmpty()) {
      return List.of();
    }
    List<UUID> staffelIds = squadronRows.stream().map(r -> r.getId().getOrgUnitId()).toList();
    return squadronRepository.findAllById(staffelIds).stream()
        .sorted(Comparator.comparing(Squadron::getName, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  /**
   * Resolves the given {@code SQUADRON}-kind membership rows to their owning Staffel ids,
   * name-sorted (primary first) exactly as {@link #resolveNameSortedStaffeln(List)}. Used where the
   * caller only needs the ids (authorization gates, the single-valued primary accessors). The
   * common single-Staffel case is short-circuited <em>without</em> a squadron load — one row is
   * already its own primary and needs no name to sort — preserving the per-call-site fast path the
   * extraction inherited.
   *
   * @param squadronRows the user's {@code SQUADRON}-kind membership rows; never {@code null},
   *     possibly empty.
   * @return the owning Staffel ids, name-sorted (primary first); never {@code null}, possibly
   *     empty.
   */
  @NotNull
  public List<UUID> resolveNameSortedStaffelIds(@NotNull List<OrgUnitMembership> squadronRows) {
    if (squadronRows.isEmpty()) {
      return List.of();
    }
    if (squadronRows.size() == 1) {
      // The common single-Staffel case needs no name sort and no squadron load.
      return List.of(squadronRows.get(0).getId().getOrgUnitId());
    }
    return resolveNameSortedStaffeln(squadronRows).stream().map(Squadron::getId).toList();
  }
}

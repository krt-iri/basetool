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

package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembershipId;
import de.greluc.krt.iri.basetool.backend.model.SpecialCommand;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.MembershipFlagsPatchRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.MembershipLeadToggleRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.OrgUnitMembershipOptionDto;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.SpecialCommandRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Membership-management service for Spezialkommandos — adds / removes / patches members of an SK
 * through the endpoints under {@code /api/v1/special-commands/{id}/members}. The service is
 * intentionally scoped to {@link OrgUnitKind#SPECIAL_COMMAND} memberships: every entry point loads
 * the parent SK through {@link SpecialCommandService#getSpecialCommandById(UUID)} first, which
 * already filters via the JPA discriminator. A Squadron UUID accidentally routed through the SK
 * endpoints therefore lands as a clean 404 before any membership row is touched, never as a
 * corrupted Staffel membership.
 *
 * <p>Staffel membership flag flips ({@code is_logistician} / {@code is_mission_manager} on the
 * Staffel membership row) flow through {@link #applyStaffelMembershipFlagDelta(UUID, Boolean,
 * Boolean)} and {@link #patchSquadronMemberFlags(UUID, UUID, MembershipFlagsPatchRequest)}. The
 * legacy {@code app_user.is_logistician} / {@code app_user.is_mission_manager} columns were dropped
 * in V101 (R9 Step 5) — the membership row is the single source of truth.
 *
 * <p>Concurrency: every write method checks the inbound {@code version} against the membership
 * row's {@code @Version} field, throwing {@link ObjectOptimisticLockingFailureException} → 409 on
 * mismatch so two concurrent admin edits do not silently lose either flag flip.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrgUnitMembershipService {

  private final OrgUnitMembershipRepository membershipRepository;
  private final SpecialCommandService specialCommandService;
  private final UserRepository userRepository;
  private final SquadronRepository squadronRepository;
  private final SpecialCommandRepository specialCommandRepository;
  private final InventoryOrgUnitReconciler inventoryReconciler;

  /**
   * Lists every active org unit (Staffel + Spezialkommando) as picker options, irrespective of
   * caller / target-user memberships. Backs the {@code GET /api/v1/org-units/active} endpoint that
   * the R5.d.c Job Order create form consumes — Job Orders are cross-staffel workspaces, so the
   * picker for {@code requestingOrgUnitId} is sourced from the full active-org-unit list rather
   * than the order owner's memberships.
   *
   * <p>The result is sorted Staffel-first then Spezialkommandos alphabetical, mirroring {@link
   * #listOptionsForUser}'s order so the two endpoints render identically in the picker.
   *
   * @return active Squadron + SpecialCommand options; never {@code null}, possibly empty when the
   *     system has zero active org units.
   */
  public List<OrgUnitMembershipOptionDto> listAllActiveOptions() {
    List<OrgUnitMembershipOptionDto> options = new ArrayList<>();
    for (Squadron s : squadronRepository.findAllByActiveTrue()) {
      options.add(
          new OrgUnitMembershipOptionDto(
              s.getId(), s.getName(), s.getShorthand(), OrgUnitKind.SQUADRON));
    }
    for (SpecialCommand sc : specialCommandRepository.findAllByActiveTrue()) {
      options.add(
          new OrgUnitMembershipOptionDto(
              sc.getId(), sc.getName(), sc.getShorthand(), OrgUnitKind.SPECIAL_COMMAND));
    }
    options.sort(
        Comparator.<OrgUnitMembershipOptionDto, Integer>comparing(
                o -> o.kind() == OrgUnitKind.SQUADRON ? 0 : 1)
            .thenComparing(
                o -> o.orgUnitName() == null ? "" : o.orgUnitName(),
                String.CASE_INSENSITIVE_ORDER));
    return options;
  }

  /**
   * Lists every org unit the given user is a member of, materialised as the picker-optimised {@link
   * OrgUnitMembershipOptionDto} wire shape. Backs the {@code GET
   * /api/v1/users/{userId}/memberships} endpoint that the R5.d owner-picker fragment consumes.
   *
   * <p>The result is sorted Staffel-first (because a user has at most one Staffel membership, and
   * keeping it at the top of the dropdown is the highest-frequency choice), then Spezialkommandos
   * alphabetical by name. Returns an empty list when the user has no memberships at all (typical
   * for admin / guest users that exist in {@code app_user} without a Staffel join), and also when
   * the user id itself is unknown — the picker treats both cases the same.
   *
   * <p>Inheritance look-up: the membership row carries an opaque {@code org_unit_id} plus the
   * denormalised {@code kind} discriminator, so a polymorphic load through a single {@code
   * OrgUnitRepository} would require introducing one (still deferred per R2.a's repository
   * decision). Branching on {@code kind} and dispatching to the existing {@link SquadronRepository}
   * / {@link SpecialCommandRepository} avoids that dependency and stays consistent with how the
   * rest of the service already treats the two kinds (the {@code addMember} / {@code removeMember}
   * paths already only touch the SK side).
   *
   * @param userId the user whose memberships to enumerate; never {@code null}.
   * @return picker-friendly DTOs for each membership; never {@code null}, possibly empty.
   */
  public List<OrgUnitMembershipOptionDto> listOptionsForUser(@NotNull UUID userId) {
    List<OrgUnitMembership> rows = membershipRepository.findAllByIdUserId(userId);
    if (rows.isEmpty()) {
      return List.of();
    }
    List<OrgUnitMembershipOptionDto> options = new ArrayList<>(rows.size());
    for (OrgUnitMembership row : rows) {
      UUID orgUnitId = row.getId().getOrgUnitId();
      if (row.getKind() == OrgUnitKind.SQUADRON) {
        Optional<Squadron> sq = squadronRepository.findById(orgUnitId);
        sq.ifPresent(
            s ->
                options.add(
                    new OrgUnitMembershipOptionDto(
                        s.getId(), s.getName(), s.getShorthand(), OrgUnitKind.SQUADRON)));
      } else if (row.getKind() == OrgUnitKind.SPECIAL_COMMAND) {
        Optional<SpecialCommand> sc = specialCommandRepository.findById(orgUnitId);
        sc.ifPresent(
            s ->
                options.add(
                    new OrgUnitMembershipOptionDto(
                        s.getId(), s.getName(), s.getShorthand(), OrgUnitKind.SPECIAL_COMMAND)));
      }
    }
    options.sort(
        Comparator.<OrgUnitMembershipOptionDto, Integer>comparing(
                o -> o.kind() == OrgUnitKind.SQUADRON ? 0 : 1)
            .thenComparing(
                o -> o.orgUnitName() == null ? "" : o.orgUnitName(),
                String.CASE_INSENSITIVE_ORDER));
    return options;
  }

  /**
   * Lists every membership of the given Spezialkommando. Used by the admin roster page to render
   * the member chip list. The SK existence is validated via {@link SpecialCommandService} so a
   * stale id surfaces as 404 instead of an empty list (which would mask a wrong URL).
   *
   * @param specialCommandId the Spezialkommando id; never {@code null}.
   * @return the (possibly empty) list of memberships in repository insertion order.
   * @throws NotFoundException if no SK matches the given id.
   */
  public List<OrgUnitMembership> listMembers(@NotNull UUID specialCommandId) {
    SpecialCommand sc = specialCommandService.getSpecialCommandById(specialCommandId);
    return membershipRepository.findAllByIdOrgUnitId(sc.getId());
  }

  /**
   * Adds the given user as a member of the given Spezialkommando. Returns the persisted membership
   * row with the V95 trigger-derived {@code kind} value pre-populated on the in-memory entity (the
   * actual DB column is written by the BEFORE-INSERT trigger; we mirror the value on the entity so
   * the immediate DTO mapping reads the right discriminator without an extra refresh).
   *
   * <p>Idempotency: an attempt to add a user who is already a member raises {@link
   * DuplicateEntityException} → 409 rather than silently no-op. The admin UI is expected to use a
   * dedicated "already member" detection instead of leaning on add as a re-attach.
   *
   * @param specialCommandId the SK to add the user to; never {@code null}.
   * @param userId the user to add; never {@code null}.
   * @return the persisted membership row.
   * @throws NotFoundException if no SK matches the given id, or no user matches the given id.
   * @throws DuplicateEntityException if the user is already a member of this SK.
   */
  @Transactional
  public OrgUnitMembership addMember(@NotNull UUID specialCommandId, @NotNull UUID userId) {
    SpecialCommand sc = specialCommandService.getSpecialCommandById(specialCommandId);
    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    if (membershipRepository.existsByIdUserIdAndIdOrgUnitId(userId, sc.getId())) {
      throw new DuplicateEntityException("User is already a member of this Spezialkommando");
    }

    final boolean wasMembershipless = membershipRepository.countByIdUserId(userId) == 0;

    OrgUnitMembership membership = new OrgUnitMembership();
    membership.setId(new OrgUnitMembershipId(userId, sc.getId()));
    membership.setUser(user);
    // The kind column is managed by the V95 sync_org_unit_membership_kind trigger
    // (insertable=false on the @Column mapping). We mirror the value on the in-memory entity so
    // the immediate DTO mapping reads the right discriminator without re-fetching the row.
    membership.setKind(OrgUnitKind.SPECIAL_COMMAND);
    membership.setJoinedAt(Instant.now());
    OrgUnitMembership saved = membershipRepository.save(membership);

    // First org-unit membership for this user → their ownerless-personal inventory adopts this SK
    // (the auto-promote lifecycle policy). No-op when the user already had memberships or owns no
    // ownerless inventory.
    if (wasMembershipless) {
      inventoryReconciler.onUserGainedFirstOrgUnit(userId, sc);
    }
    return saved;
  }

  /**
   * Removes the given user from the given Spezialkommando. Existence checks both sides so a stale
   * URL surfaces as 404 rather than as a silent no-op.
   *
   * @param specialCommandId the SK to remove the user from; never {@code null}.
   * @param userId the user to remove; never {@code null}.
   * @throws NotFoundException if no SK matches the given id, or the user is not a member of this
   *     SK.
   */
  @Transactional
  public void removeMember(@NotNull UUID specialCommandId, @NotNull UUID userId) {
    SpecialCommand sc = specialCommandService.getSpecialCommandById(specialCommandId);
    OrgUnitMembershipId id = new OrgUnitMembershipId(userId, sc.getId());
    if (!membershipRepository.existsById(id)) {
      throw new NotFoundException("Membership not found");
    }
    membershipRepository.deleteById(id);

    // Last org-unit membership removed → the user's org-stamped inventory falls back to
    // ownerless-personal (the auto-demote lifecycle policy). The count query auto-flushes the
    // delete first, so it reflects the just-removed row.
    if (membershipRepository.countByIdUserId(userId) == 0) {
      inventoryReconciler.onUserLostLastOrgUnit(userId);
    }
  }

  /**
   * Flips the per-membership Logistician / Mission Manager flags on the membership row. Either flag
   * may be {@code null} in the request — that means "leave the current value alone". The inbound
   * {@code version} is checked against the row's {@code @Version} to surface concurrent admin edits
   * as 409 instead of silently losing one of them.
   *
   * @param specialCommandId the SK whose membership to patch; never {@code null}.
   * @param userId the user whose membership to patch; never {@code null}.
   * @param request patch payload; never {@code null}.
   * @return the persisted membership row with the bumped {@code @Version}.
   * @throws NotFoundException if no SK matches the given id, or the user is not a member.
   * @throws ObjectOptimisticLockingFailureException if the inbound version is stale.
   */
  @Transactional
  public OrgUnitMembership patchFlags(
      @NotNull UUID specialCommandId,
      @NotNull UUID userId,
      @NotNull MembershipFlagsPatchRequest request) {
    OrgUnitMembership m = loadMembership(specialCommandId, userId);
    assertVersionMatches(m, request.version());
    if (request.isLogistician() != null) {
      m.setLogistician(request.isLogistician());
    }
    if (request.isMissionManager() != null) {
      m.setMissionManager(request.isMissionManager());
    }
    return membershipRepository.save(m);
  }

  /**
   * R6.e — Squadron-side counterpart of {@link #patchFlags(UUID, UUID,
   * MembershipFlagsPatchRequest)}. Same payload contract (boxed {@code Boolean} flags, mandatory
   * {@code version}) and same optimistic-lock semantics; only the existence check up front is
   * different — Squadrons live in the {@link SquadronRepository}, not the {@link
   * SpecialCommandRepository}. ADMIN-gated at the controller layer per plan §5.6 ({@code PATCH
   * /api/v1/squadrons/{id}/members/{userId}}). Used to migrate the legacy {@code
   * UserController.updateLogisticianStatus} / {@code updateMissionManagerStatus} writes from the
   * {@code app_user.is_logistician} / {@code is_mission_manager} columns onto the per-membership
   * row (R6.e write-side completion of plan D3).
   *
   * @param squadronId the Squadron whose membership to patch; never {@code null}.
   * @param userId the user whose membership to patch; never {@code null}.
   * @param request patch payload; never {@code null}.
   * @return the persisted membership row with the bumped {@code @Version}.
   * @throws NotFoundException if no Squadron matches the given id, or the user is not a member.
   * @throws ObjectOptimisticLockingFailureException if the inbound version is stale.
   */
  @Transactional
  public OrgUnitMembership patchSquadronMemberFlags(
      @NotNull UUID squadronId,
      @NotNull UUID userId,
      @NotNull MembershipFlagsPatchRequest request) {
    Squadron squadron =
        squadronRepository
            .findById(squadronId)
            .orElseThrow(() -> new NotFoundException("Squadron not found"));
    OrgUnitMembership m =
        membershipRepository
            .findById(new OrgUnitMembershipId(userId, squadron.getId()))
            .orElseThrow(() -> new NotFoundException("Membership not found"));
    assertVersionMatches(m, request.version());
    if (request.isLogistician() != null) {
      m.setLogistician(request.isLogistician());
    }
    if (request.isMissionManager() != null) {
      m.setMissionManager(request.isMissionManager());
    }
    return membershipRepository.save(m);
  }

  /**
   * R6.e helper — applies the supplied flag delta to the user's existing Staffel membership row
   * without optimistic-lock checking. Used by the legacy {@code UserController}-shaped flag-toggle
   * endpoints that still accept a bare {@code ?isLogistician=...} query parameter (no version
   * round-trip) so a flag toggle from the existing admin UI propagates to the membership row.
   * Idempotent — passing the same flag value the row already has is a no-op write that bumps the
   * {@code @Version} via Hibernate dirty checking.
   *
   * <p>Post-R9 D3 (V101): the user's home Staffel is read from {@code org_unit_membership} directly
   * — the legacy {@code app_user.squadron_id} column was dropped. If the user has no Staffel
   * membership row at all (admin / guest), the call is a no-op: there is no membership to flip the
   * flag on.
   *
   * @param userId the user whose Staffel membership to update; never {@code null}.
   * @param isLogistician new flag value, or {@code null} to leave the existing value untouched.
   * @param isMissionManager new flag value, or {@code null} to leave the existing value untouched.
   * @throws NotFoundException if the user does not exist.
   */
  @Transactional
  public void applyStaffelMembershipFlagDelta(
      @NotNull UUID userId,
      @org.jetbrains.annotations.Nullable Boolean isLogistician,
      @org.jetbrains.annotations.Nullable Boolean isMissionManager) {
    userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    List<OrgUnitMembership> staffelRows =
        membershipRepository.findAllByIdUserIdAndKind(userId, OrgUnitKind.SQUADRON);
    if (staffelRows.isEmpty()) {
      // No Staffel membership to flip the flag on (admin / guest user without a Staffel link).
      // Post-R9 D3 there is no legacy User-level column to fall back to either — the toggle is a
      // clean no-op.
      return;
    }
    OrgUnitMembership m = staffelRows.get(0);
    if (isLogistician != null) {
      m.setLogistician(isLogistician);
    }
    if (isMissionManager != null) {
      m.setMissionManager(isMissionManager);
    }
    membershipRepository.saveAndFlush(m);
  }

  /**
   * Synchronises the user's single Staffel membership row to the target Squadron. Called by {@link
   * UserService#updateUserSquadron(UUID, UUID, Long)} so the {@code org_unit_membership} row
   * reflects the admin's squadron-assignment write.
   *
   * <p>Logic:
   *
   * <ul>
   *   <li>{@code newSquadron == null} → every existing Staffel membership of the user is removed.
   *       The user falls back into "no Staffel" territory; their SK memberships (if any) stay.
   *   <li>{@code newSquadron} matches the user's existing Staffel membership → no-op.
   *   <li>{@code newSquadron} differs from the existing Staffel membership → the old row is deleted
   *       and a new one is created at the new Squadron. Per-membership flags reset to {@code false}
   *       on the new row — the legacy {@code User.isLogistician} / {@code User.isMissionManager}
   *       columns were dropped in V101 (R9 Step 5), so there is no carry-over source.
   *   <li>No existing Staffel membership → a fresh row is created at {@code newSquadron} with flags
   *       defaulting to {@code false}.
   * </ul>
   *
   * <p>The V95 partial unique index {@code uq_user_one_squadron_membership} guarantees at most one
   * Staffel membership per user, which this method preserves by deleting the old row before
   * inserting the new one.
   *
   * @param user the user whose Staffel membership to sync; never {@code null}.
   * @param newSquadron the target Staffel, or {@code null} to remove the Staffel membership.
   */
  @Transactional
  public void syncStaffelMembership(
      @NotNull User user, @org.jetbrains.annotations.Nullable Squadron newSquadron) {
    final long membershipsBefore = membershipRepository.countByIdUserId(user.getId());
    List<OrgUnitMembership> existing =
        membershipRepository.findAllByIdUserIdAndKind(user.getId(), OrgUnitKind.SQUADRON);

    if (newSquadron == null) {
      if (!existing.isEmpty()) {
        membershipRepository.deleteAll(existing);
        // Removing the Staffel may have been the user's last org-unit membership (no SK left) →
        // demote their org-stamped inventory to ownerless-personal.
        if (membershipRepository.countByIdUserId(user.getId()) == 0) {
          inventoryReconciler.onUserLostLastOrgUnit(user.getId());
        }
      }
      return;
    }

    boolean alreadyMember =
        existing.stream().anyMatch(m -> newSquadron.getId().equals(m.getId().getOrgUnitId()));
    if (alreadyMember && existing.size() == 1) {
      return;
    }

    // Delete any stale Staffel memberships at other Squadrons (V95 partial unique index allows
    // at most one Staffel membership per user; switching squadrons removes the old one).
    List<OrgUnitMembership> stale =
        existing.stream()
            .filter(m -> !newSquadron.getId().equals(m.getId().getOrgUnitId()))
            .toList();
    if (!stale.isEmpty()) {
      membershipRepository.deleteAll(stale);
      membershipRepository.flush();
    }

    if (!alreadyMember) {
      OrgUnitMembership fresh = new OrgUnitMembership();
      fresh.setId(new OrgUnitMembershipId(user.getId(), newSquadron.getId()));
      fresh.setUser(user);
      fresh.setJoinedAt(java.time.Instant.now());
      // Post-R9 D3: flags default to false on a freshly-created row — the legacy
      // app_user.is_logistician / app_user.is_mission_manager columns are gone. Admins re-grant
      // the flags via the membership-PATCH endpoint after the Staffel switch.
      membershipRepository.save(fresh);

      // If this Staffel was the user's first-ever org-unit membership, their ownerless-personal
      // inventory adopts it (the auto-promote lifecycle policy). A Staffel switch or a second
      // membership (membershipsBefore > 0) does not move existing stock.
      if (membershipsBefore == 0) {
        inventoryReconciler.onUserGainedFirstOrgUnit(user.getId(), newSquadron);
      }
    }
  }

  /**
   * Flips the {@code is_lead} flag on the membership row. ADMIN-only at the controller layer — a
   * member-managing Lead cannot promote themselves or someone else to Lead. Carries an
   * optimistic-lock version like {@link #patchFlags}.
   *
   * @param specialCommandId the SK whose membership to update; never {@code null}.
   * @param userId the user whose membership to update; never {@code null}.
   * @param request toggle payload; never {@code null}.
   * @return the persisted membership row.
   * @throws NotFoundException if no SK matches the given id, or the user is not a member.
   * @throws ObjectOptimisticLockingFailureException if the inbound version is stale.
   */
  @Transactional
  public OrgUnitMembership toggleLead(
      @NotNull UUID specialCommandId,
      @NotNull UUID userId,
      @NotNull MembershipLeadToggleRequest request) {
    OrgUnitMembership m = loadMembership(specialCommandId, userId);
    assertVersionMatches(m, request.version());
    m.setLead(request.isLead());
    return membershipRepository.save(m);
  }

  /**
   * Returns the OrgUnit id of the user's single Staffel membership row, if any. Convenience
   * accessor used by callers that previously read {@code User.getSquadron().getId()} — now that the
   * legacy column is gone, the equivalent lookup is "find the single SQUADRON-kind membership for
   * this user".
   *
   * @param userId the user whose Staffel membership to resolve; never {@code null}.
   * @return the Staffel's id when the user belongs to one, empty otherwise.
   */
  @NotNull
  public Optional<UUID> findStaffelMembershipOrgUnitId(@NotNull UUID userId) {
    List<OrgUnitMembership> rows =
        membershipRepository.findAllByIdUserIdAndKind(userId, OrgUnitKind.SQUADRON);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rows.get(0).getId().getOrgUnitId());
  }

  /**
   * SPEZIALKOMMANDO_PLAN.md §7.4 helper — returns every membership row of the given user (Staffel +
   * every SK) in the same order {@link #listOptionsForUser} sorts the picker options (Staffel
   * first, then SKs alphabetical). The delta-endpoint return path uses this to render the
   * post-write state without a follow-up GET on the frontend side.
   *
   * @param userId the user whose memberships to enumerate; never {@code null}.
   * @return the membership rows; never {@code null}, possibly empty.
   */
  public List<OrgUnitMembership> findAllMembershipsForUser(@NotNull UUID userId) {
    List<OrgUnitMembership> rows = membershipRepository.findAllByIdUserId(userId);
    if (rows.isEmpty()) {
      return List.of();
    }
    List<OrgUnitMembership> sorted = new ArrayList<>(rows);
    sorted.sort(
        Comparator.<OrgUnitMembership, Integer>comparing(
                m -> m.getKind() == OrgUnitKind.SQUADRON ? 0 : 1)
            .thenComparing(
                m -> {
                  UUID orgUnitId = m.getId().getOrgUnitId();
                  return m.getKind() == OrgUnitKind.SQUADRON
                      ? squadronRepository.findById(orgUnitId).map(Squadron::getName).orElse("")
                      : specialCommandRepository
                          .findById(orgUnitId)
                          .map(SpecialCommand::getName)
                          .orElse("");
                },
                String.CASE_INSENSITIVE_ORDER));
    return sorted;
  }

  /**
   * Loads the membership row for the given (SK, user) pair, validating SK existence first so a
   * stale SK id surfaces as 404 with a clear message before the membership lookup fires.
   *
   * @param specialCommandId the SK id.
   * @param userId the user id.
   * @return the membership row.
   * @throws NotFoundException if no SK matches the given id, or the user is not a member.
   */
  private OrgUnitMembership loadMembership(UUID specialCommandId, UUID userId) {
    SpecialCommand sc = specialCommandService.getSpecialCommandById(specialCommandId);
    return membershipRepository
        .findById(new OrgUnitMembershipId(userId, sc.getId()))
        .orElseThrow(() -> new NotFoundException("Membership not found"));
  }

  /**
   * Throws {@link ObjectOptimisticLockingFailureException} if the inbound client-held version does
   * not match the persisted row's {@code @Version}. Mirrors the pattern used in {@code
   * SpecialCommandService.updateSpecialCommand} so the optimistic-lock surface is uniform across
   * the SK administration endpoints.
   */
  private void assertVersionMatches(OrgUnitMembership m, Long version) {
    if (m.getVersion() != null && !m.getVersion().equals(version)) {
      throw new ObjectOptimisticLockingFailureException(OrgUnitMembership.class, null);
    }
  }
}

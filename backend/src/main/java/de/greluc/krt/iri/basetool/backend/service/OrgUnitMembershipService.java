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
 * <p>Staffel membership flag flips (the legacy {@code app_user.is_logistician} / {@code
 * is_mission_manager} surface) are intentionally not part of this service — they keep their
 * existing path through the {@code UserService} role-flag endpoints until a later release migrates
 * them to the {@code org_unit_membership} row.
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

    OrgUnitMembership membership = new OrgUnitMembership();
    membership.setId(new OrgUnitMembershipId(userId, sc.getId()));
    membership.setUser(user);
    // The kind column is managed by the V95 sync_org_unit_membership_kind trigger
    // (insertable=false on the @Column mapping). We mirror the value on the in-memory entity so
    // the immediate DTO mapping reads the right discriminator without re-fetching the row.
    membership.setKind(OrgUnitKind.SPECIAL_COMMAND);
    membership.setJoinedAt(Instant.now());
    return membershipRepository.save(membership);
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
   * round-trip) so a flag toggle from the existing admin UI propagates to the membership row even
   * before the frontend migrates to the version-aware PATCH. Idempotent — passing the same flag
   * value the row already has is a no-op write that bumps the {@code @Version} via Hibernate dirty
   * checking.
   *
   * <p>If the user has no Staffel membership row (V95 backfill gap for a post-R1 user whose
   * squadron was assigned without the parallel membership upsert), an absent row is created on the
   * fly using the user's current {@code User#getSquadron()} as the OrgUnit anchor. This closes the
   * legacy invariant gap during the R6.e soak — the proper fix (creating the membership row in
   * {@code UserService.updateUserSquadron}) lives in {@link UserService#updateUserSquadron(UUID,
   * UUID, Long)} so the rule fires uniformly across every Staffel-assignment code path.
   *
   * @param userId the user whose Staffel membership to update; never {@code null}.
   * @param isLogistician new flag value, or {@code null} to leave the existing value untouched.
   * @param isMissionManager new flag value, or {@code null} to leave the existing value untouched.
   * @throws NotFoundException if the user does not exist or has no assigned Squadron at all.
   */
  @Transactional
  public void applyStaffelMembershipFlagDelta(
      @NotNull UUID userId,
      @org.jetbrains.annotations.Nullable Boolean isLogistician,
      @org.jetbrains.annotations.Nullable Boolean isMissionManager) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    Squadron homeStaffel = user.getSquadron();
    if (homeStaffel == null) {
      // No Staffel to grant a per-membership flag on. Legacy endpoint shapes that toggle the
      // user-level column for a squadron-less admin / guest user just mirror to the User row
      // (the JWT converter's R6.d fallback branch still picks the User-level column up).
      return;
    }
    OrgUnitMembershipId pk = new OrgUnitMembershipId(userId, homeStaffel.getId());
    OrgUnitMembership m =
        membershipRepository
            .findById(pk)
            .orElseGet(
                () -> {
                  // V95-backfill safety net: the user has a Staffel link but no membership row.
                  // Synthesise the row so the legacy flag write has a place to land.
                  OrgUnitMembership fresh = new OrgUnitMembership();
                  fresh.setId(pk);
                  fresh.setUser(user);
                  fresh.setJoinedAt(java.time.Instant.now());
                  return fresh;
                });
    if (isLogistician != null) {
      m.setLogistician(isLogistician);
    }
    if (isMissionManager != null) {
      m.setMissionManager(isMissionManager);
    }
    try {
      membershipRepository.saveAndFlush(m);
    } catch (org.springframework.dao.DataIntegrityViolationException race) {
      // Concurrent legacy flag-toggles on the same V95-backfill-gap user: two threads both saw
      // findById miss, both synthesised a fresh row, the second flush trips the composite-PK
      // uniqueness constraint. Re-load the row the winning thread persisted, re-apply the delta on
      // top, and let dirty-checking flush the result through the optimistic-lock surface — the
      // race window is now closed because the row exists.
      OrgUnitMembership winning =
          membershipRepository
              .findById(pk)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Concurrent membership upsert lost — neither row resolves", race));
      if (isLogistician != null) {
        winning.setLogistician(isLogistician);
      }
      if (isMissionManager != null) {
        winning.setMissionManager(isMissionManager);
      }
      membershipRepository.saveAndFlush(winning);
    }
  }

  /**
   * R6.e — synchronises the Staffel membership row with the user's currently-assigned {@code
   * User.squadron}. Called by {@link UserService#updateUserSquadron(UUID, UUID, Long)} after the
   * squadron assignment so the {@code org_unit_membership} row stays in lockstep with the legacy
   * {@code app_user.squadron_id} column during the soak window. Closes the V95 backfill gap for
   * post-R1 users created via Keycloak SSO without an explicit membership insert.
   *
   * <p>Logic:
   *
   * <ul>
   *   <li>{@code newSquadron == null} → every existing Staffel membership of the user is removed.
   *       The user falls back into "no Staffel" territory; their SK memberships (if any) stay.
   *   <li>{@code newSquadron} matches the user's existing Staffel membership → no-op.
   *   <li>{@code newSquadron} differs from the existing Staffel membership → the old row is deleted
   *       and a new one is created at the new Squadron. Per-membership flags fall back to the
   *       legacy {@code User.isLogistician} / {@code User.isMissionManager} columns so the user's
   *       authorisation surface does not regress just because their Staffel changed.
   *   <li>No existing Staffel membership → a fresh row is created at {@code newSquadron} with the
   *       same legacy-flag carry-over.
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
    List<OrgUnitMembership> existing =
        membershipRepository.findAllByIdUserIdAndKind(user.getId(), OrgUnitKind.SQUADRON);

    if (newSquadron == null) {
      if (!existing.isEmpty()) {
        membershipRepository.deleteAll(existing);
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
      // Carry legacy User-level flags onto the new row so authority does not regress mid-deploy.
      fresh.setLogistician(user.isLogistician());
      fresh.setMissionManager(user.isMissionManager());
      membershipRepository.save(fresh);
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

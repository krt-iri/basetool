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

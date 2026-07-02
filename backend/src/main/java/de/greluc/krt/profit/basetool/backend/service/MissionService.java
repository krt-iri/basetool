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

import de.greluc.krt.profit.basetool.backend.exception.NotFoundException;
import de.greluc.krt.profit.basetool.backend.model.AuditEventType;
import de.greluc.krt.profit.basetool.backend.model.FrequencyType;
import de.greluc.krt.profit.basetool.backend.model.JobType;
import de.greluc.krt.profit.basetool.backend.model.JobTypeArchetype;
import de.greluc.krt.profit.basetool.backend.model.Mission;
import de.greluc.krt.profit.basetool.backend.model.MissionCrew;
import de.greluc.krt.profit.basetool.backend.model.MissionFrequency;
import de.greluc.krt.profit.basetool.backend.model.MissionObjective;
import de.greluc.krt.profit.basetool.backend.model.MissionObjectiveKind;
import de.greluc.krt.profit.basetool.backend.model.MissionOwnership;
import de.greluc.krt.profit.basetool.backend.model.MissionParticipant;
import de.greluc.krt.profit.basetool.backend.model.MissionStep;
import de.greluc.krt.profit.basetool.backend.model.MissionUnit;
import de.greluc.krt.profit.basetool.backend.model.Operation;
import de.greluc.krt.profit.basetool.backend.model.OrgUnit;
import de.greluc.krt.profit.basetool.backend.model.PayoutPreference;
import de.greluc.krt.profit.basetool.backend.model.Ship;
import de.greluc.krt.profit.basetool.backend.model.ShipType;
import de.greluc.krt.profit.basetool.backend.model.User;
import de.greluc.krt.profit.basetool.backend.model.dto.request.CreateMissionRequest;
import de.greluc.krt.profit.basetool.backend.model.dto.request.UpdateMissionRequest;
import de.greluc.krt.profit.basetool.backend.repository.FrequencyTypeRepository;
import de.greluc.krt.profit.basetool.backend.repository.JobTypeRepository;
import de.greluc.krt.profit.basetool.backend.repository.MissionCrewRepository;
import de.greluc.krt.profit.basetool.backend.repository.MissionFrequencyRepository;
import de.greluc.krt.profit.basetool.backend.repository.MissionObjectiveRepository;
import de.greluc.krt.profit.basetool.backend.repository.MissionOwnershipRepository;
import de.greluc.krt.profit.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.profit.basetool.backend.repository.MissionRepository;
import de.greluc.krt.profit.basetool.backend.repository.MissionStepRepository;
import de.greluc.krt.profit.basetool.backend.repository.MissionUnitRepository;
import de.greluc.krt.profit.basetool.backend.repository.OperationRepository;
import de.greluc.krt.profit.basetool.backend.repository.ShipRepository;
import de.greluc.krt.profit.basetool.backend.repository.ShipTypeRepository;
import de.greluc.krt.profit.basetool.backend.repository.UserRepository;
import de.greluc.krt.profit.basetool.backend.support.AuditDetails;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the {@code mission} aggregate — the central operational entity of the squadron.
 *
 * <p>A mission groups a roster (participants), a structure (units + crews + ship assignments), a
 * schedule (planned / actual start and end), and a set of frequencies (radio channels). The service
 * covers the entire surface used by the mission detail page: full updates, section-scoped partial
 * updates (core / schedule / flags), the manager + owner management, participant lifecycle (add as
 * user, as guest, remove, check-in / check-out, payout preference), unit + crew structure, and the
 * mission-frequency CRUD.
 *
 * <p>Concurrency-relevant: nearly every write here is paired with an optimistic-lock check against
 * the mission's {@code @Version}. Methods that touch participants / units / crews work on the
 * already-managed aggregate via dirty-checking — see CLAUDE.md's {@code …WithinTransaction} and
 * bulk-update-after-loop patterns. The owner-version is tracked separately in {@code
 * mission_ownership} so an admin's owner-change does not race with a concurrent participant edit
 * and force a 409 on the unrelated path.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionService {

  /** Hard cap on participants per mission — see {@link #addParticipant} for the rationale. */
  public static final int MAX_PARTICIPANTS_PER_MISSION = 500;

  private final MissionRepository missionRepository;
  private final UserRepository userRepository;
  private final ShipRepository shipRepository;
  private final ShipTypeRepository shipTypeRepository;
  private final JobTypeRepository jobTypeRepository;
  private final MissionParticipantRepository missionParticipantRepository;
  private final MissionUnitRepository missionUnitRepository;
  private final MissionCrewRepository missionCrewRepository;
  private final MissionStepRepository missionStepRepository;
  private final MissionObjectiveRepository missionObjectiveRepository;
  private final FrequencyTypeRepository frequencyTypeRepository;
  private final MissionFrequencyRepository missionFrequencyRepository;
  private final MissionOwnershipRepository missionOwnershipRepository;
  private final OperationRepository operationRepository;
  private final UserService userService;
  private final OwnerScopeService ownerScopeService;
  private final AuthHelperService authHelperService;
  private final OrgUnitMembershipService orgUnitMembershipService;
  private final de.greluc.krt.profit.basetool.backend.repository.OrgUnitRepository
      orgUnitRepository;
  private final GuestParticipantTokenService guestParticipantTokenService;
  private final AuditService auditService;

  /**
   * <strong>Do not call from new code.</strong> Kept only because the wider service test suite
   * references the method signature; every controller-facing list endpoint MUST go through {@link
   * #searchMissions(String, Instant, Instant, List, Boolean, UUID, Pageable)} so the
   * MULTI_SQUADRON_PLAN §1 visibility rule is applied. A direct {@code missionRepository.findAll}
   * leaks every squadron's internal missions cross-tenant. Audit finding M-5 (2026-05-20): this
   * method is unused on the controller path and is retained at {@code @Deprecated(forRemoval)} with
   * a hard {@link UnsupportedOperationException} body so a future caller fails immediately instead
   * of silently widening visibility.
   *
   * @param pageable unused
   * @return never; always throws
   * @throws UnsupportedOperationException always — use {@link #searchMissions} instead.
   * @deprecated use {@link #searchMissions} with appropriate filters.
   */
  @Deprecated(forRemoval = true)
  public Page<Mission> getAllMissions(@NotNull Pageable pageable) {
    throw new UnsupportedOperationException(
        "getAllMissions bypasses the multi-squadron visibility filter and must not be used; "
            + "call searchMissions with the appropriate isInternal / scopeSquadronId filters "
            + "instead (MULTI_SQUADRON_PLAN.md §1, audit finding M-5).");
  }

  /**
   * Returns the lightweight reference projection (id + display name + status + planned start) that
   * drives the mission-picker dropdowns in the warehouse (Lager) views — the per-item association
   * select and the list filter. Includes every {@code PLANNED} / {@code ACTIVE} mission visible to
   * the caller plus the {@code COMPLETED} / {@code CANCELLED} missions whose planned start falls
   * within the last three months, so a just-closed operation stays filterable while ancient ones
   * drop off the list. Squadron-scoped via {@link OwnerScopeService#currentScopePredicate()}: a
   * non-admin caller sees their own OrgUnits' missions PLUS any non-internal mission of any
   * OrgUnit, mirroring {@link #searchMissions(String, Instant, Instant, List, Boolean, UUID,
   * Pageable)}; admins in "all squadrons" mode get the unfiltered cross-staffel list. Audit finding
   * H-4: the previous implementation leaked the names of foreign squadrons' internal missions
   * through this dropdown.
   *
   * @return lightweight reference projection of the picker-visible missions for the caller
   */
  public List<de.greluc.krt.profit.basetool.backend.model.dto.MissionReferenceDto>
      findAllActiveReference() {
    ScopePredicate scope = ownerScopeService.currentScopePredicate();
    Instant terminalCutoff = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(3).toInstant();
    return missionRepository.findAllActiveReference(
        scope.adminAllScope(),
        scope.activeOrgUnitId(),
        scope.memberOrgUnitIds(),
        authHelperService.isMemberOrAbove(),
        terminalCutoff);
  }

  /**
   * Free-text search over mission name, description, location and operation name. Optional filters
   * narrow by status, time window and operation. Used by the mission list page.
   *
   * @return paged matching missions
   */
  public Page<Mission> searchMissions(
      String query,
      Instant start,
      Instant end,
      List<String> status,
      Boolean isInternal,
      UUID operationId,
      @NotNull Pageable pageable) {
    if (status == null || status.isEmpty()) {
      status = List.of("PLANNED", "ACTIVE", "COMPLETED", "CANCELLED");
    }
    // M-1: defence-in-depth. Anonymous callers may never see internal missions, even if a future
    // controller forgets to pass {@code isInternal=false}. Force the filter here so the data
    // layer is the authoritative gate — a regression in the controller cannot widen the leak.
    Boolean effectiveIsInternal = isInternal;
    if (!authHelperService.isAuthenticated()) {
      effectiveIsInternal = Boolean.FALSE;
    }
    ScopePredicate scope = ownerScopeService.currentScopePredicate();
    return missionRepository.searchMissions(
        query,
        start,
        end,
        status,
        effectiveIsInternal,
        operationId,
        scope.adminAllScope(),
        scope.activeOrgUnitId(),
        scope.memberOrgUnitIds(),
        authHelperService.isMemberOrAbove(),
        pageable);
  }

  /**
   * Returns the mission.
   *
   * @param id mission primary key
   * @return the mission
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when no match
   */
  public Mission getMissionById(@NotNull UUID id) {
    return missionRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Mission not found"));
  }

  /**
   * Statuses a mission may carry to qualify as the home-page "next mission". Only live operational
   * missions ({@code PLANNED} / {@code ACTIVE}) are eligible — a {@code COMPLETED} or {@code
   * CANCELLED} mission with a future planned start must never surface in the banner.
   */
  private static final List<String> NEXT_MISSION_STATUSES = List.of("PLANNED", "ACTIVE");

  /**
   * Returns the next upcoming mission by planned-start time. Drives the home-page "next mission"
   * banner. Only missions in status {@code PLANNED} or {@code ACTIVE} are considered. {@code
   * allowInternal=true} (for authenticated callers) includes internal missions; guests see only
   * public missions.
   *
   * <p>Org-unit scoping (REQ-MISSION-008): when the caller has an effective org-unit scope — a
   * plain member's own unit(s), or the cascade-expanded reach of a Bereich/OL leader (REQ-ORG-015)
   * — the banner is restricted to missions owned by those org units; foreign missions, including
   * other units' public ones, are excluded so the banner answers "what is <em>my</em> unit heading
   * towards". When the caller has <em>no</em> org-unit scope — an admin in "all squadrons" mode, an
   * anonymous guest, or an authenticated user who belongs to no org unit — the banner falls back to
   * the unchanged organisation-wide next mission. The scope vector is the same {@link
   * OwnerScopeService#currentScopePredicate()} the scoped mission lists use.
   *
   * @param allowInternal whether internal missions should be included
   * @return the next mission, or empty when none upcoming
   */
  public Optional<Mission> getNextMission(boolean allowInternal) {
    ScopePredicate scope = ownerScopeService.currentScopePredicate();
    Instant now = Instant.now();
    Optional<Mission> next;
    if (scope.adminAllScope()
        || (scope.activeOrgUnitId() == null && scope.memberOrgUnitIds().isEmpty())) {
      // No org-unit scope: admin all-scope, anonymous guest, or an authenticated user with no
      // membership. Unchanged behaviour — the soonest PLANNED/ACTIVE mission across the whole
      // organisation (internal ones only for members). REQ-MISSION-008.
      next = findNextMissionHead(now, allowInternal);
    } else {
      // The caller has an org-unit scope: restrict the banner to missions owned by those org units.
      // A Bereich/OL leader's scope already carries the cascaded descendants (REQ-ORG-015 via
      // OwnerScopeService.currentMemberOrgUnitIds); a plain member sees only their own units.
      next = findNextScopedMissionHead(now, allowInternal, scope);
    }
    // The limit-1 lookups above are intentionally not graphed — a collection fetch combined with
    // the
    // limit forces Hibernate into in-memory pagination (HHH90003004). Re-fetch the single hit by id
    // through the graphed findById so participants / assignedUnits are eagerly loaded for the
    // mapper
    // (and the home-page guest redaction) without paginating the whole upcoming-mission set.
    return next.map(Mission::getId).flatMap(missionRepository::findById);
  }

  /**
   * Resolves the ungraphed limit-1 head of the unscoped next-mission lookup, filtered to {@link
   * #NEXT_MISSION_STATUSES}. Split out from {@link #getNextMission(boolean)} only so the long
   * derived-query method names sit at a shallow enough indentation to stay within the line-length
   * limit; it carries no behaviour of its own beyond the {@code allowInternal} branch.
   *
   * @param now exclusive lower bound on {@code plannedStartTime}
   * @param allowInternal whether internal missions should be included
   * @return the next-mission head (id-only matters; caller re-fetches through the graphed findById)
   */
  private Optional<Mission> findNextMissionHead(Instant now, boolean allowInternal) {
    if (allowInternal) {
      return missionRepository
          .findFirstByPlannedStartTimeAfterAndStatusInOrderByPlannedStartTimeAsc(
              now, NEXT_MISSION_STATUSES);
    }
    return missionRepository
        .findFirstByPlannedStartTimeAfterAndIsInternalFalseAndStatusInOrderByPlannedStartTimeAsc(
            now, NEXT_MISSION_STATUSES);
  }

  /**
   * Resolves the ungraphed limit-1 head of the org-unit-scoped next-mission lookup
   * (REQ-MISSION-008) for a caller that has an effective scope. Delegates to {@link
   * MissionRepository#findNextScopedMission} with a {@code PageRequest.of(0, 1)} so only the
   * soonest matching mission is fetched, then returns its head. The scope's {@code activeOrgUnitId}
   * (when pinned) or {@code memberOrgUnitIds} (the cascade-expanded membership union) selects the
   * eligible owning org units; {@code allowInternal} mirrors the unscoped variant's
   * internal-visibility gate.
   *
   * @param now exclusive lower bound on {@code plannedStartTime}
   * @param allowInternal whether internal missions should be included
   * @param scope the caller's effective org-unit scope (never admin-all / never empty here)
   * @return the next-mission head (id-only matters; caller re-fetches through the graphed findById)
   */
  private Optional<Mission> findNextScopedMissionHead(
      Instant now, boolean allowInternal, ScopePredicate scope) {
    return missionRepository
        .findNextScopedMission(
            now,
            NEXT_MISSION_STATUSES,
            allowInternal,
            scope.activeOrgUnitId(),
            scope.memberOrgUnitIds(),
            PageRequest.of(0, 1))
        .stream()
        .findFirst();
  }

  /**
   * Persists a new mission from the request DTO. Every server-managed column ({@code id}, {@code
   * version}, the section counters, {@code owner}, {@code owningSquadron}, {@code parent}, the
   * sub-aggregate collections) is set inside this method — the request record carries only
   * caller-controllable fields, which makes the mass-assignment vector exploited in audit finding
   * C-3 structurally impossible.
   *
   * @param request create payload (already validated by Bean Validation at the controller boundary)
   * @return the persisted mission entity
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when {@code
   *     operationId} does not resolve
   */
  @Transactional
  public Mission createMission(@NotNull CreateMissionRequest request) {
    Mission mission = new Mission();
    applyCreatePayload(mission, request);

    // Fail-fast on the time-window validation BEFORE the userService / ownerScopeService
    // round-trips so a malformed payload does not waste a DB / security-context lookup.
    validateMissionTimes(mission);

    userService.getCurrentUser().ifPresent(mission::setOwner);

    if (mission.getOwner() != null) {
      // R5.d.d: owner present → route through the shared picker resolver, which honours an
      // explicit owningOrgUnitId from the form if the caller is a member of that org unit, and
      // falls back to the owner's home Staffel when the field is null. The *nullable* resolver is
      // used so a membershipless leadership user ("Bereichsleitung", who belongs to no Staffel/SK
      // but may plan org-wide missions) resolves to a null owner instead of a 400 — the resulting
      // ownerless mission is attributable through its owner and scoped by the mission visibility
      // rules (public unless internal). See OwnerScopeService.resolveOrgUnitForPickerOutputNullable
      // and V144.
      mission.setOwningOrgUnit(
          ownerScopeService.resolveOrgUnitForPickerOutputNullable(
              mission.getOwner(), request.owningOrgUnitId()));
    } else {
      // No authenticated owner (admin in "all squadrons" mode or anonymous fallback) — the
      // picker field, if supplied, cannot be membership-validated. Honour the historical
      // behaviour: stamp from the active org-unit scope.
      ownerScopeService.currentOrgUnit().ifPresent(mission::setOwningOrgUnit);
    }

    Mission saved = missionRepository.save(mission);
    auditService.record(
        AuditEventType.MISSION_CREATED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("status", mission.getStatus()));
    return saved;
  }

  /**
   * Copies the caller-controllable fields of {@link CreateMissionRequest} onto a fresh {@link
   * Mission} entity. Shared by {@link #createMission(CreateMissionRequest)} and {@link
   * #addSubMission(UUID, CreateMissionRequest)}; the latter additionally wires up {@code parent}
   * and inherits the owning squadron. Operation lookup runs here so both create paths fail the same
   * way for an unknown operation id.
   *
   * @param mission target entity (caller owns its identity and version)
   * @param request validated create payload
   */
  private void applyCreatePayload(Mission mission, CreateMissionRequest request) {
    mission.setName(request.name());
    mission.setDescription(request.description());
    mission.setMeetingPoint(request.meetingPoint());
    mission.setCalendarLink(request.calendarLink());
    mission.setStatus(request.status());
    mission.setMeetingTime(request.meetingTime());
    mission.setPlannedStartTime(request.plannedStartTime());
    mission.setPlannedEndTime(request.plannedEndTime());
    mission.setIsInternal(request.isInternal() != null ? request.isInternal() : Boolean.FALSE);

    if (request.operationId() != null) {
      Operation op =
          operationRepository
              .findById(request.operationId())
              .orElseThrow(() -> new NotFoundException("Operation not found"));
      mission.setOperation(op);
    } else {
      mission.setOperation(null);
    }
  }

  /**
   * Full update of a mission's metadata + structural references. Validates optimistic lock,
   * resolves shallow references (operation, location, frequencies). Participant / unit / crew lists
   * are NOT touched here — they have their own dedicated mutators (see {@link #addParticipant},
   * {@link #addUnitToMission}, etc.) so the per-row optimistic-lock checks on those flows do not
   * collide with a mission-level edit.
   *
   * <p>Bumps all three section counters ({@code coreVersion}, {@code scheduleVersion}, {@code
   * flagsVersion}) on success because a full-replace is semantically an overwrite of every section;
   * concurrent section-scoped patches in flight will get a 409 on their next save. Prefer the
   * dedicated section endpoints {@link #updateCoreSection}, {@link #updateScheduleSection}, {@link
   * #updateFlagsSection} for multi-user-friendly edits.
   *
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when the mission or
   *     any referenced id is unknown
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the supplied
   *     version is stale
   */
  @Transactional
  public Mission updateMission(@NotNull UUID missionId, @NotNull UpdateMissionRequest request) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    if (!mission.getVersion().equals(request.version())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          Mission.class, missionId);
    }

    // Decide the effective actualStartTime UP FRONT, before mutating any setter on the managed
    // entity — the auto-stamp branch reads the current (pre-mutation) status, so capturing it
    // here keeps the decision honest and avoids a far-away usage of the snapshot variable that
    // Checkstyle's VariableDeclarationUsageDistance would otherwise flag.
    Instant explicitActualStart = request.actualStartTime();
    boolean autoStampActualStart =
        "ACTIVE".equals(request.status())
            && !"ACTIVE".equals(mission.getStatus())
            && explicitActualStart == null;
    Instant effectiveActualStart = autoStampActualStart ? Instant.now() : explicitActualStart;

    mission.setName(request.name());
    mission.setDescription(request.description());
    mission.setMeetingPoint(request.meetingPoint());
    mission.setCalendarLink(request.calendarLink());
    mission.setStatus(request.status());
    mission.setMeetingTime(request.meetingTime());
    mission.setPlannedStartTime(request.plannedStartTime());
    mission.setPlannedEndTime(request.plannedEndTime());
    mission.setActualStartTime(effectiveActualStart);

    if (request.operationId() != null) {
      Operation op =
          operationRepository
              .findById(request.operationId())
              .orElseThrow(() -> new NotFoundException("Operation not found"));
      mission.setOperation(op);
    } else {
      mission.setOperation(null);
    }

    mission.setIsInternal(request.isInternal() != null ? request.isInternal() : Boolean.FALSE);

    Instant newEndTime = request.actualEndTime();
    mission.setActualEndTime(newEndTime);

    if (newEndTime != null) {
      for (MissionParticipant participant : mission.getParticipants()) {
        if (participant.getStartTime() != null) {
          if (participant.getEndTime() == null || participant.getEndTime().isAfter(newEndTime)) {
            participant.setEndTime(newEndTime);
          }
        }
      }
    }

    validateMissionTimes(mission);

    bumpSectionVersion(mission, MissionSection.CORE);
    bumpSectionVersion(mission, MissionSection.SCHEDULE);
    bumpSectionVersion(mission, MissionSection.FLAGS);

    Mission saved = missionRepository.save(mission);
    auditService.record(
        AuditEventType.MISSION_UPDATED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("section", "full").with("status", mission.getStatus()));
    return saved;
  }

  /**
   * Updates only the core (master-data) section of a mission. Validates the dedicated {@code
   * coreVersion} counter so concurrent edits on {@code schedule} or {@code flags} do not invalidate
   * this form. Sub-collections (participants, units, finance) are not touched and, thanks to
   * {@code @OptimisticLock(excluded = true)}, do not bump the parent version either.
   *
   * <p>When the status transitions from anything other than {@code ACTIVE} to {@code ACTIVE} and
   * {@link Mission#getActualStartTime()} is still {@code null}, this method ALSO bumps {@code
   * scheduleVersion} and stamps {@code actualStartTime = now()} via {@link
   * #bumpActualStartTimeOnActivationWithinTransaction(Mission)}. This is by design: the activation
   * crosses the core/schedule boundary, and the schedule counter must move so concurrent schedule
   * edits surface the change as a 409 instead of silently overwriting the auto-stamped value.
   *
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when any referenced
   *     id is unknown
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the supplied
   *     {@code expectedCoreVersion} is stale
   */
  @Transactional
  public Mission updateCoreSection(
      @NotNull UUID missionId,
      @NotNull String name,
      String description,
      String calendarLink,
      String status,
      UUID operationId,
      String meetingPoint,
      @NotNull Long expectedCoreVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertSectionVersion(mission, MissionSection.CORE, expectedCoreVersion, missionId);

    // Cross-section auto-stamp FIRST, before mutating mission.status — the condition reads the
    // OLD status, so it must run before the setter below. Setting actualStartTime here is safe
    // because it touches a different field; the later setters do not overwrite it.
    if ("ACTIVE".equals(status)
        && !"ACTIVE".equals(mission.getStatus())
        && mission.getActualStartTime() == null) {
      bumpActualStartTimeOnActivationWithinTransaction(mission);
    }

    mission.setName(name);
    mission.setDescription(description);
    mission.setMeetingPoint(meetingPoint);
    mission.setCalendarLink(calendarLink);
    if (status != null) {
      mission.setStatus(status);
    }

    if (operationId != null) {
      Operation op =
          operationRepository
              .findById(operationId)
              .orElseThrow(() -> new NotFoundException("Operation not found"));
      mission.setOperation(op);
    } else {
      mission.setOperation(null);
    }

    bumpSectionVersion(mission, MissionSection.CORE);
    Mission saved = missionRepository.save(mission);
    auditService.record(
        AuditEventType.MISSION_UPDATED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("section", "core").with("status", mission.getStatus()));
    return saved;
  }

  /**
   * Updates only the schedule section of a mission. All timestamps are processed and stored in UTC.
   * Validates the dedicated {@code scheduleVersion} counter; concurrent core or flags edits do not
   * invalidate this form.
   *
   * <p>Setting {@code actualEndTime} also closes any open participant end-times (dirty-checked on
   * the managed entities; the participants' own {@code @Version} fields will be bumped naturally by
   * Hibernate at flush time, mission section counters are not affected by that).
   *
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the supplied
   *     {@code expectedScheduleVersion} is stale
   */
  @Transactional
  public Mission updateScheduleSection(
      @NotNull UUID missionId,
      Instant meetingTime,
      Instant plannedStartTime,
      Instant plannedEndTime,
      Instant actualStartTime,
      Instant actualEndTime,
      @NotNull Long expectedScheduleVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertSectionVersion(mission, MissionSection.SCHEDULE, expectedScheduleVersion, missionId);
    mission.setMeetingTime(meetingTime);
    mission.setPlannedStartTime(plannedStartTime);
    mission.setPlannedEndTime(plannedEndTime);
    mission.setActualStartTime(actualStartTime);
    mission.setActualEndTime(actualEndTime);

    if (actualEndTime != null) {
      for (MissionParticipant participant : mission.getParticipants()) {
        if (participant.getStartTime() != null) {
          if (participant.getEndTime() == null || participant.getEndTime().isAfter(actualEndTime)) {
            participant.setEndTime(actualEndTime);
          }
        }
      }
    }

    validateMissionTimes(mission);
    bumpSectionVersion(mission, MissionSection.SCHEDULE);
    Mission saved = missionRepository.save(mission);
    auditService.record(
        AuditEventType.MISSION_UPDATED,
        mission.getId(),
        mission.getName(),
        null,
        "section=schedule");
    return saved;
  }

  /**
   * Updates only the flags section of a mission ({@code isInternal}). Validates the dedicated
   * {@code flagsVersion} counter.
   *
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the supplied
   *     {@code expectedFlagsVersion} is stale
   */
  @Transactional
  public Mission updateFlagsSection(
      @NotNull UUID missionId, @NotNull Boolean isInternal, @NotNull Long expectedFlagsVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertSectionVersion(mission, MissionSection.FLAGS, expectedFlagsVersion, missionId);
    mission.setIsInternal(isInternal);
    bumpSectionVersion(mission, MissionSection.FLAGS);
    Mission saved = missionRepository.save(mission);
    auditService.record(
        AuditEventType.MISSION_UPDATED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("section", "flags").with("isInternal", isInternal));
    return saved;
  }

  /**
   * Cross-section helper invoked from {@link #updateCoreSection} when a status transition to {@code
   * ACTIVE} requires auto-stamping {@code actualStartTime}. Operates on the already-managed entity
   * via dirty-checking (no {@code save()}/{@code flush()} of its own — see CLAUDE.md's {@code
   * …WithinTransaction} pattern) and bumps the schedule counter so other open schedule editors get
   * a 409 instead of silently overwriting the stamp.
   *
   * @param mission managed mission entity in the current transaction
   */
  private void bumpActualStartTimeOnActivationWithinTransaction(@NotNull Mission mission) {
    mission.setActualStartTime(Instant.now());
    bumpSectionVersion(mission, MissionSection.SCHEDULE);
  }

  /**
   * Checks the expected value of a mission's fine-grained {@link MissionSection} version counter
   * against its current value, raising a 409 on mismatch so two managers racing on the
   * <em>same</em> section surface a conflict instead of one silently overwriting the other, while a
   * concurrent edit to an <em>unrelated</em> section never collides (REQ-ORG-018). An absent
   * (never-bumped) counter reads as {@code 0L}, matching the value a fresh section renders and
   * echoes back.
   *
   * <p>These are the manual business-{@code Long} section counters described in CLAUDE.md: they are
   * deliberately <strong>not</strong> the row's JPA {@code @Version} and are <strong>not</strong>
   * routed through the {@code support.OptimisticLock} helper family — the null-{@code ->}-{@code
   * 0L} semantics are their own.
   *
   * @param mission the managed mission whose counter to check.
   * @param section the section the caller echoed a version back for.
   * @param expectedVersion the version the caller echoed back from the rendered page.
   * @param missionId the mission id, for the conflict exception identifier.
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the expected
   *     version is stale.
   */
  private void assertSectionVersion(
      @NotNull Mission mission,
      @NotNull MissionSection section,
      @NotNull Long expectedVersion,
      @NotNull UUID missionId) {
    if (!expectedVersion.equals(section.current(mission))) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          Mission.class, missionId);
    }
  }

  /**
   * Increments a mission's fine-grained {@link MissionSection} version counter after a successful
   * edit of that section, so the next echo from a now-stale form for the <em>same</em> section is
   * rejected while unrelated sections keep their counters. An absent (never-bumped) counter is
   * treated as {@code 0L} and becomes {@code 1L}.
   *
   * @param mission the managed mission whose counter to bump.
   * @param section the section whose counter to increment.
   */
  private void bumpSectionVersion(@NotNull Mission mission, @NotNull MissionSection section) {
    section.set(mission, section.current(mission) + 1L);
  }

  /**
   * The mission's independently-versioned edit sections. Each constant binds the getter/setter of
   * one manual {@code *Version} counter on {@link Mission}, letting {@link #assertSectionVersion}
   * and {@link #bumpSectionVersion} operate on any section without a per-section helper. These back
   * the fine-grained per-section optimistic locks (REQ-ORG-018): an edit to one section must not
   * 409 a concurrent edit to another, so each section carries its own counter rather than sharing
   * the row's Hibernate {@code @Version}.
   */
  private enum MissionSection {
    /** The mission core (name, description, status, owner-visible identity). */
    CORE(Mission::getCoreVersion, Mission::setCoreVersion),
    /** The mission schedule (planned/actual start and end times). */
    SCHEDULE(Mission::getScheduleVersion, Mission::setScheduleVersion),
    /** The mission flags (e.g. the internal/public visibility toggle). */
    FLAGS(Mission::getFlagsVersion, Mission::setFlagsVersion),
    /** The mission party-lead assignment. */
    PARTY_LEAD(Mission::getPartyLeadVersion, Mission::setPartyLeadVersion),
    /** The Ablauf steps timeline. */
    STEPS(Mission::getStepsVersion, Mission::setStepsVersion),
    /** The mission objectives (Ziele). */
    OBJECTIVES(Mission::getObjectivesVersion, Mission::setObjectivesVersion),
    /** The owning-org-unit assignment. */
    OWNING_ORG_UNIT(Mission::getOwningOrgUnitVersion, Mission::setOwningOrgUnitVersion);

    /** Reads the raw (nullable) counter value from a mission. */
    private final transient Function<Mission, Long> getter;

    /** Writes the counter value back onto a mission. */
    private final transient BiConsumer<Mission, Long> setter;

    /**
     * Binds a section constant to its {@code *Version} counter accessors on {@link Mission}.
     *
     * @param getter reads the raw (nullable) counter value.
     * @param setter writes the counter value back.
     */
    MissionSection(Function<Mission, Long> getter, BiConsumer<Mission, Long> setter) {
      this.getter = getter;
      this.setter = setter;
    }

    /**
     * Returns this section's current counter value for the given mission, coalescing an absent
     * (never-bumped) {@code null} counter to {@code 0L} — the exact value a fresh section renders
     * and echoes back, so the very first edit validates against {@code 0L}.
     *
     * @param mission the mission to read the counter from.
     * @return the current section version, or {@code 0L} when the counter is null.
     */
    private long current(@NotNull Mission mission) {
      Long value = getter.apply(mission);
      return value == null ? 0L : value;
    }

    /**
     * Writes a new value into this section's counter on the given mission.
     *
     * @param mission the mission to write the counter on.
     * @param value the new counter value.
     */
    private void set(@NotNull Mission mission, long value) {
      setter.accept(mission, value);
    }
  }

  /**
   * Deletes a mission. The cascade unlinks inventory items and refinery orders (rather than
   * deleting them) so individual member contributions survive the mission delete. Mission
   * participants, finance entries, units, crews and frequencies ARE deleted because they only exist
   * in the context of this mission.
   *
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when the mission is
   *     unknown
   */
  @Transactional
  public void deleteMission(@NotNull UUID missionId) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    // Snapshot the label/id BEFORE the delete so the audit row survives the removed aggregate.
    final UUID deletedMissionId = mission.getId();
    final String deletedMissionName = mission.getName();

    // Detach inventory items (so they are not deleted while still avoiding the FK violation)
    if (mission.getInventoryEntries() != null) {
      mission.getInventoryEntries().forEach(entry -> entry.setMission(null));
      mission.getInventoryEntries().clear();
    }

    // Detach refinery orders
    if (mission.getRefineryOrders() != null) {
      mission.getRefineryOrders().forEach(order -> order.setMission(null));
      mission.getRefineryOrders().clear();
    }

    // Detach sub-missions
    if (mission.getSubMissions() != null) {
      mission.getSubMissions().forEach(sub -> sub.setParent(null));
      mission.getSubMissions().clear();
    }

    missionRepository.delete(mission);
    auditService.record(
        AuditEventType.MISSION_DELETED, deletedMissionId, deletedMissionName, null, null);
  }

  private void validateMissionTimes(Mission mission) {
    if (mission.getMeetingTime() != null && mission.getPlannedStartTime() != null) {
      if (mission.getMeetingTime().isAfter(mission.getPlannedStartTime())) {
        throw new IllegalArgumentException("Meeting time cannot be later than planned start time");
      }
    }
    if (mission.getPlannedStartTime() != null && mission.getPlannedEndTime() != null) {
      if (mission.getPlannedStartTime().isAfter(mission.getPlannedEndTime())) {
        throw new IllegalArgumentException(
            "Planned start time cannot be later than planned end time");
      }
    }
  }

  /**
   * Adds an authenticated user as a participant on a mission. Convenience overload that delegates
   * to the full-form {@link #addParticipant(UUID, ParticipantForm)} with default values for the
   * optional fields.
   */
  @Transactional
  public Mission addParticipant(@NotNull UUID missionId, @NotNull UUID userId) {
    return addParticipant(missionId, userId, null, null, null, null, null);
  }

  /**
   * Mid-form participant add — accepts a user reference, optional guest name (when the user isn't
   * authenticated), an optional desired job type, and an optional comment. Convenience overload
   * that delegates to the full form with {@code orgUnitIds=null} and no explicit payout choice.
   */
  @Transactional
  public Mission addParticipant(
      @NotNull UUID missionId,
      UUID userId,
      String guestName,
      UUID desiredJobTypeId,
      String comment) {
    return addParticipant(missionId, userId, guestName, desiredJobTypeId, comment, null, null);
  }

  /**
   * Full-form participant add. Resolves the user reference from {@code userId} (or by
   * case-insensitive {@code guestName} match against existing users — promotes a guest entry to a
   * linked-user entry when the guest name turns out to be a real member).
   *
   * <p>Org-unit affiliations are stamped per kind of participant:
   *
   * <ul>
   *   <li><b>Registered user</b> — affiliations are auto-derived from the user's memberships (every
   *       Staffel and Spezialkommando they belong to); the submitted {@code orgUnitIds} are
   *       ignored. A user with no membership at all gets no affiliation (no more wrong IRIDIUM
   *       fallback).
   *   <li><b>Guest</b> — the caller-submitted {@code orgUnitIds} are honoured after the
   *       authorization filter in {@link #resolveGuestSubmittedOrgUnits(java.util.List)} (anonymous
   *       callers cannot label a guest at all; authenticated callers may label only org units they
   *       can edit).
   * </ul>
   *
   * <p>{@code payoutPreference} (nullable) fixes the per-mission payout choice at sign-up time —
   * the sign-up modal's "Auszahlungsart" select. A non-null value wins over the registered user's
   * profile default (REQ-MISSION-002); {@code null} keeps the existing default chain (profile
   * default for users, entity default {@code PAYOUT} for guests).
   *
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when any referenced
   *     id is unknown
   */
  @Transactional
  public Mission addParticipant(
      @NotNull UUID missionId,
      UUID userId,
      String guestName,
      UUID desiredJobTypeId,
      String comment,
      java.util.List<UUID> orgUnitIds,
      PayoutPreference payoutPreference) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    // Audit finding M-4: hard cap of {@value #MAX_PARTICIPANTS_PER_MISSION} per mission. Closes
    // the DoS vector where an anonymous caller scripts thousands of guest sign-ups until the
    // mission_participant table holds millions of rows for a single mission and {@code
    // mission.getParticipants()} (eager-fetched via the findById EntityGraph) starts scanning
    // hundreds of MB per request. 500 covers every realistic IRIDIUM-scale operation by a large
    // margin; override via a Squadron-level property is a follow-up if ever needed.
    if (mission.getParticipants() != null
        && mission.getParticipants().size() >= MAX_PARTICIPANTS_PER_MISSION) {
      throw new de.greluc.krt.profit.basetool.backend.exception.BusinessConflictException(
          "Mission participant cap reached ("
              + MAX_PARTICIPANTS_PER_MISSION
              + "). Remove inactive participants before adding more.");
    }

    UUID effectiveUserId = userId;
    String effectiveGuestName = guestName;

    if (effectiveUserId == null && effectiveGuestName != null && !effectiveGuestName.isBlank()) {
      Optional<User> matchedUser =
          userRepository.findByUsernameIgnoreCaseOrDisplayNameIgnoreCase(
              effectiveGuestName.trim(), effectiveGuestName.trim());
      if (matchedUser.isPresent()) {
        effectiveUserId = matchedUser.orElseThrow().getId();
        effectiveGuestName = null;
      }
    }

    if (effectiveUserId == null && (effectiveGuestName == null || effectiveGuestName.isBlank())) {
      throw new IllegalArgumentException("Either User ID or Guest Name must be provided.");
    }

    final UUID finalUserId = effectiveUserId;
    final String finalGuestName = effectiveGuestName;

    // Check for duplicates
    if (finalUserId != null) {
      boolean exists =
          mission.getParticipants().stream()
              .anyMatch(p -> p.getUser() != null && p.getUser().getId().equals(finalUserId));
      if (exists) {
        throw new de.greluc.krt.profit.basetool.backend.exception.DuplicateEntityException(
            "error.mission.participant.duplicate.user");
      }
    } else if (finalGuestName != null && !finalGuestName.isBlank()) {
      boolean exists =
          mission.getParticipants().stream()
              .anyMatch(p -> finalGuestName.equalsIgnoreCase(p.getGuestName()));
      if (exists) {
        throw new de.greluc.krt.profit.basetool.backend.exception.DuplicateEntityException(
            "error.mission.participant.duplicate.guest");
      }
    }

    MissionParticipant participant = new MissionParticipant();
    participant.setMission(mission);

    if (effectiveUserId != null) {
      User user =
          userRepository
              .findById(effectiveUserId)
              .orElseThrow(() -> new NotFoundException("User not found"));
      participant.setUser(user);
      // Registered users carry every org unit they belong to at participate-time (their Staffel
      // and/or any Spezialkommandos). Auto-derived from org_unit_membership — empty when the user
      // belongs to none (admins / brand-new accounts) so the roster shows no affiliation instead of
      // the old, wrong IRIDIUM fallback. The caller-submitted orgUnitIds are intentionally ignored
      // for registered participants; the picker is guest-only.
      participant.setOrgUnits(resolveMembershipOrgUnits(user.getId()));
      // REQ-MISSION-002: pre-fill the per-participant payout preference from the signing-up user's
      // personal default. A user who never chose one keeps the entity default (PAYOUT). This is a
      // one-time seed at sign-up — the per-mission value stays editable afterwards via
      // updateParticipantAttributes and is NOT rewritten when the user later changes their profile
      // default. Guests (the else branch) have no profile and keep PAYOUT.
      if (user.getDefaultPayoutPreference() != null) {
        participant.setPayoutPreference(user.getDefaultPayoutPreference());
      }
    } else {
      participant.setGuestName(effectiveGuestName);
      participant.setOrgUnits(resolveGuestSubmittedOrgUnits(orgUnitIds));
      // Security audit M1 / REQ-SEC-018: bind this anonymous guest sign-up to its creator with a
      // per-row capability token. Only the hash is persisted; the plaintext rides back on the
      // transient field so the create response can hand it to the caller exactly once. A later
      // guest
      // mutate/delete must present the token (or hold a mission-management role) — enforced by
      // MissionSecurityService.canAccessParticipant.
      String mintedGuestEditToken = guestParticipantTokenService.generateToken();
      participant.setGuestEditTokenHash(
          guestParticipantTokenService.hashToken(mintedGuestEditToken));
      participant.setGuestEditToken(mintedGuestEditToken);
    }

    if (desiredJobTypeId != null) {
      JobType job = jobTypeRepository.findById(desiredJobTypeId).orElse(null);
      participant.setDesiredMissionJobType(job);
    }

    // An explicit sign-up choice (the modal's Auszahlungsart select) wins over the profile-default
    // seeding above; null keeps the default chain untouched.
    if (payoutPreference != null) {
      participant.setPayoutPreference(payoutPreference);
    }

    participant.setComment(comment);

    mission.getParticipants().add(participant);
    missionParticipantRepository.save(participant);
    auditService.record(
        AuditEventType.MISSION_PARTICIPANT_ADDED,
        mission.getId(),
        mission.getName(),
        finalUserId,
        AuditDetails.of("participant", participant.getId())
            .with("type", finalUserId != null ? "user" : "guest"));
    // NOTE: no explicit missionRepository.save(mission) here.
    // The collection is @OptimisticLock(excluded = true) so Hibernate's dirty-check
    // on commit persists the new participant (via cascade) without bumping the parent
    // Mission.version. This is key for the multi-user concurrency design (Option A):
    // adding a participant must NOT invalidate other users' open forms on the same mission.
    //
    // No `flush()` here on purpose. The in-memory `anyMatch` check above is the
    // primary duplicate detector (returns a localized DuplicateEntityException → 409).
    // The Stufe-2 DB-level backstop is the partial unique index `uq_mission_participant_user`
    // (Flyway V96): a TOCTOU-raced double-signup (double-click, two tabs) slips past the
    // in-memory check, both inserts head for the same (mission, user) key, and PostgreSQL
    // rejects the second one at commit time as a unique-constraint violation. Spring
    // wraps that as DataIntegrityViolationException and the GlobalExceptionHandler maps
    // it to 409 — the same HTTP status the in-memory branch produces, so the frontend
    // toast logic (`MissionPageController#addParticipant`, status-code-based) shows the
    // user the same error. A service-side translation to DuplicateEntityException was
    // attempted but required `saveAndFlush`, which forces a session-wide flush and
    // breaks @Transactional tests that intentionally hold half-built sibling entities
    // in the persistence context until rollback.
    return mission;
  }

  /**
   * Look up a single participant on a mission. Verifies the participant actually belongs to the
   * named mission — a participant id that exists but is attached to a different mission throws 404,
   * matching what {@link MissionSecurityService#canAccessParticipant} expects.
   *
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when the participant
   *     does not exist on this mission
   */
  public MissionParticipant getParticipant(@NotNull UUID missionId, @NotNull UUID participantId) {
    return missionParticipantRepository
        .findById(participantId)
        .filter(p -> p.getMission().getId().equals(missionId))
        .orElseThrow(() -> new NotFoundException("Participant not found in mission"));
  }

  /**
   * Returns all participants of a mission that are not yet assigned to any unit (crew). Used to
   * filter the "Crew zuweisen" dropdown so only unassigned participants are selectable.
   *
   * @param missionId mission id
   * @return list of unassigned participants
   */
  public List<MissionParticipant> getUnassignedParticipants(@NotNull UUID missionId) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    Set<UUID> assignedParticipantIds =
        mission.getAssignedUnits().stream()
            .flatMap(unit -> unit.getCrew().stream())
            .map(crew -> crew.getParticipant().getId())
            .collect(java.util.stream.Collectors.toSet());
    return mission.getParticipants().stream()
        .filter(p -> !assignedParticipantIds.contains(p.getId()))
        .toList();
  }

  /**
   * Removes a participant from a mission and the participant's linked finance entries. The
   * participant row itself is deleted (not soft-deleted); finance entries with FK to the
   * participant cascade-delete to keep the FK constraint happy.
   */
  @Transactional
  public Mission removeParticipant(@NotNull UUID missionId, @NotNull UUID participantId) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    boolean removed = mission.getParticipants().removeIf(p -> p.getId().equals(participantId));

    if (!removed) {
      throw new NotFoundException("Participant not found in this mission");
    }

    // Also remove from any crews in this mission
    for (MissionUnit ship : mission.getAssignedUnits()) {
      ship.getCrew()
          .removeIf(
              crew ->
                  crew.getParticipant() != null
                      && crew.getParticipant().getId().equals(participantId));
    }

    // NOTE: no explicit missionRepository.save(mission). orphanRemoval + @OptimisticLock(excluded)
    // on participants/assignedUnits ensures dirty-flush on commit without bumping Mission.version.
    auditService.record(
        AuditEventType.MISSION_PARTICIPANT_REMOVED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("participant", participantId));
    return mission;
  }

  /**
   * Updates a participant's per-mission attributes (job type, ship, unit, crew, guest name, payout
   * preference). Per-participant optimistic lock via the participant's own {@code @Version} field —
   * the wider mission's version is NOT bumped, so concurrent participant edits don't collide with
   * each other or with mission-level edits.
   *
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when the participant
   *     or any referenced id is unknown
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when stale
   */
  @Transactional
  public Mission updateParticipantAttributes(
      @NotNull UUID missionId,
      @NotNull UUID participantId,
      UUID desiredMissionJobTypeId,
      UUID plannedMissionJobTypeId,
      String comment,
      Instant startTime,
      Instant endTime,
      java.util.List<UUID> orgUnitIds,
      PayoutPreference payoutPreference,
      String guestName,
      Long version) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    MissionParticipant participant =
        mission.getParticipants().stream()
            .filter(p -> p.getId().equals(participantId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Participant not found in this mission"));

    if (version != null && !version.equals(participant.getVersion())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          MissionParticipant.class, participant.getId());
    }

    if (payoutPreference != null) {
      participant.setPayoutPreference(payoutPreference);
      missionParticipantRepository.save(participant);
      missionParticipantRepository.flush();
    }

    if (desiredMissionJobTypeId != null) {
      JobType jt =
          jobTypeRepository
              .findById(desiredMissionJobTypeId)
              .orElseThrow(() -> new NotFoundException("Desired JobType not found"));
      if (jt.getArchetype() != JobTypeArchetype.MISSION) {
        throw new IllegalArgumentException(
            "Desired JobType " + jt.getName() + " is not of archetype MISSION");
      }
      participant.setDesiredMissionJobType(jt);
    } else {
      participant.setDesiredMissionJobType(null);
    }

    if (participant.getUser() != null) {
      // Registered users carry every org unit they belong to at participate-time. Re-derives from
      // org_unit_membership on every update so a freshly-assigned Staffel / SK propagates into the
      // participant row. The submitted orgUnitIds are ignored for registered participants — the
      // picker is guest-only, and the affiliation is the user's actual membership set.
      participant.setOrgUnits(resolveMembershipOrgUnits(participant.getUser().getId()));
    } else {
      // Audit finding M-3 (2026-05-20): logging the raw {@code guestName} leaks PII —
      // free-text names often contain real-life names of third parties that PiiMasker does not
      // catch (regex covers emails / JWTs / token keywords only). Log just the participant id;
      // the linked-vs-guest distinction is implicit because the linked-user branch above logged
      // nothing either.
      log.info("Updating guest participant: {}", participant.getId());
      if (guestName != null) {
        participant.setGuestName(guestName);
      }
      participant.setOrgUnits(resolveGuestSubmittedOrgUnits(orgUnitIds));
    }

    if (plannedMissionJobTypeId != null) {
      JobType jt =
          jobTypeRepository
              .findById(plannedMissionJobTypeId)
              .orElseThrow(() -> new NotFoundException("Planned JobType not found"));
      if (jt.getArchetype() != JobTypeArchetype.MISSION) {
        throw new IllegalArgumentException(
            "Planned JobType " + jt.getName() + " is not of archetype MISSION");
      }
      // A mission may have only one "Einsatzleiter" (the participant whose planned job type is the
      // designated mission-lead type, JobType.isMissionLead). Reject assigning it to a second
      // participant (REQ-MISSION-013) — the editor must first clear the existing one.
      if (jt.isMissionLead()) {
        boolean alreadyTaken =
            mission.getParticipants().stream()
                .anyMatch(
                    other ->
                        !other.getId().equals(participant.getId())
                            && other.getPlannedMissionJobType() != null
                            && other.getPlannedMissionJobType().isMissionLead());
        if (alreadyTaken) {
          throw new de.greluc.krt.profit.basetool.backend.exception.BusinessConflictException(
              "A mission can have only one Einsatzleiter (mission lead).");
        }
      }
      participant.setPlannedMissionJobType(jt);
    } else {
      participant.setPlannedMissionJobType(null);
    }

    participant.setComment(comment);

    if (startTime != null) {
      if (mission.getActualStartTime() == null) {
        throw new IllegalArgumentException(
            "Cannot set participant start time before mission actual start time is set");
      }
    }

    if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
      throw new IllegalArgumentException("Start time cannot be after end time");
    }

    participant.setStartTime(startTime);
    participant.setEndTime(endTime);

    // Persist the participant explicitly; avoid save(mission) to keep Mission.version stable.
    missionParticipantRepository.save(participant);
    auditService.record(
        AuditEventType.MISSION_PARTICIPANT_UPDATED,
        mission.getId(),
        mission.getName(),
        participant.getUser() != null ? participant.getUser().getId() : null,
        AuditDetails.of("participant", participantId));
    return mission;
  }

  /**
   * Marks a participant as checked in. Sets {@code startTime} to {@code now()} only if the
   * participant has not been checked in before — repeated check-in is a no-op so the original
   * arrival time is preserved.
   */
  @Transactional
  public Mission checkIn(UUID missionId, UUID participantId) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    MissionParticipant participant = getParticipant(missionId, participantId);
    if (mission.getActualStartTime() == null) {
      throw new IllegalArgumentException("Cannot check in before mission actual start time is set");
    }
    participant.setStartTime(Instant.now());
    missionParticipantRepository.save(participant);
    auditService.record(
        AuditEventType.MISSION_PARTICIPANT_CHECKED_IN,
        mission.getId(),
        mission.getName(),
        participant.getUser() != null ? participant.getUser().getId() : null,
        AuditDetails.of("participant", participantId));
    return mission;
  }

  /**
   * Marks a participant as checked out. Sets {@code endTime} to {@code now()}. Unlike check-in, a
   * repeated check-out overrides the previous timestamp — late check-out corrections.
   */
  @Transactional
  public Mission checkOut(UUID missionId, UUID participantId) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    MissionParticipant participant = getParticipant(missionId, participantId);
    if (mission.getActualEndTime() != null && Instant.now().isAfter(mission.getActualEndTime())) {
      if (participant.getStartTime() != null
          && mission.getActualEndTime().isBefore(participant.getStartTime())) {
        participant.setEndTime(participant.getStartTime());
      } else {
        participant.setEndTime(mission.getActualEndTime());
      }
    } else {
      participant.setEndTime(Instant.now());
    }
    missionParticipantRepository.save(participant);
    auditService.record(
        AuditEventType.MISSION_PARTICIPANT_CHECKED_OUT,
        mission.getId(),
        mission.getName(),
        participant.getUser() != null ? participant.getUser().getId() : null,
        AuditDetails.of("participant", participantId));
    return mission;
  }

  /**
   * Updates a participant's payout preference (PAYOUT or DONATE). Guests can change their own
   * preference even without authentication — the security gate on the participant row keeps other
   * users' preferences locked.
   */
  @Transactional
  public Mission updatePayoutPreference(
      UUID missionId, UUID participantId, PayoutPreference preference) {
    Mission mission = getMissionById(missionId);
    MissionParticipant participant =
        mission.getParticipants().stream()
            .filter(p -> p.getId().equals(participantId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Participant not found in mission"));

    if (preference != null) {
      participant.setPayoutPreference(preference);
      missionParticipantRepository.save(participant);
      missionParticipantRepository.flush();
      auditService.record(
          AuditEventType.MISSION_PARTICIPANT_UPDATED,
          mission.getId(),
          mission.getName(),
          participant.getUser() != null ? participant.getUser().getId() : null,
          AuditDetails.of("participant", participantId).with("field", "payoutPreference"));
    }
    return mission;
  }

  /**
   * Adds a unit (team grouping) to a mission. Units are the top level of the participant hierarchy:
   * each unit may contain several crews.
   *
   * <p>{@code name} is an optional display name: when blank, the stored name is derived from the
   * assigned ship respectively ship type (the unit-modal mock's "Anzeigename (optional)"); at least
   * one of name / ship / ship type must be present. {@code responsibleUserId} (nullable) pins an
   * explicit responsible person; {@code note} (nullable) is a free-text planning note.
   */
  @Transactional
  public Mission addUnitToMission(
      @NotNull UUID missionId,
      String name,
      UUID shipTypeId,
      UUID shipId,
      boolean highValueUnit,
      Double frequency,
      UUID responsibleUserId,
      String note) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    MissionUnit missionUnit = new MissionUnit();
    missionUnit.setMission(mission);

    if (shipTypeId != null) {
      ShipType shipType =
          shipTypeRepository
              .findById(shipTypeId)
              .orElseThrow(() -> new NotFoundException("ShipType not found"));
      missionUnit.setShipType(shipType);
    } else {
      missionUnit.setShipType(null);
    }

    if (shipId != null) {
      Ship ship =
          shipRepository
              .findById(shipId)
              .orElseThrow(() -> new NotFoundException("Ship not found"));
      if (shipTypeId != null && !ship.getShipType().getId().equals(shipTypeId)) {
        throw new IllegalArgumentException("Ship does not match the specified ShipType");
      }
      if (!isOwnerRegisteredParticipant(mission, ship)) {
        throw new IllegalArgumentException(
            "Ship owner is not a registered participant of the mission");
      }
      missionUnit.setShip(ship);
      if (shipTypeId == null) {
        missionUnit.setShipType(ship.getShipType());
      }
    }

    missionUnit.setName(resolveUnitName(name, missionUnit));
    missionUnit.setResponsibleUser(resolveResponsibleUser(responsibleUserId));
    missionUnit.setNote(normalizeUnitNote(note));
    missionUnit.setHighValueUnit(highValueUnit);

    if (frequency != null) {
      double roundedFrequency =
          BigDecimal.valueOf(frequency).setScale(2, RoundingMode.HALF_UP).doubleValue();

      if (roundedFrequency < 100.00 || roundedFrequency > 999.99) {
        throw new IllegalArgumentException("Frequency must be between 100.00 and 999.99");
      }
      missionUnit.setFrequency(roundedFrequency);
    }

    mission.getAssignedUnits().add(missionUnit);
    missionUnitRepository.save(missionUnit);
    auditService.record(
        AuditEventType.MISSION_UNIT_ADDED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("unit", missionUnit.getId()));
    return mission;
  }

  /**
   * Resolves the stored (NOT NULL) unit name from the optional display name: a non-blank caller
   * value wins; otherwise the name is derived from the already-resolved ship (its hangar name)
   * respectively ship type. Mirrors the unit-modal mock where the Anzeigename is optional because
   * ship / ship type carry the unit's identity.
   *
   * @param name the caller-submitted display name (nullable/blank)
   * @param unit the unit with {@code ship} / {@code shipType} already resolved
   * @return the effective non-blank name to store
   * @throws IllegalArgumentException when neither a name nor a ship / ship type is present
   */
  private static String resolveUnitName(String name, MissionUnit unit) {
    if (name != null && !name.isBlank()) {
      return name.trim();
    }
    if (unit.getShip() != null && unit.getShip().getName() != null) {
      return unit.getShip().getName();
    }
    if (unit.getShipType() != null) {
      return unit.getShipType().getName();
    }
    throw new IllegalArgumentException(
        "Unit needs a display name or a ship / ship type to derive one from");
  }

  /**
   * Resolves the optional explicit responsible person of a unit.
   *
   * @param responsibleUserId the user id, or {@code null} for "no explicit responsible" (the UI
   *     then falls back to the assigned ship's owner)
   * @return the resolved user or {@code null}
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when the id is
   *     unknown
   */
  private User resolveResponsibleUser(UUID responsibleUserId) {
    if (responsibleUserId == null) {
      return null;
    }
    return userRepository
        .findById(responsibleUserId)
        .orElseThrow(() -> new NotFoundException("Responsible user not found"));
  }

  /**
   * Normalises a unit note: trims surrounding whitespace and collapses blank input to {@code null}
   * so the column stays empty instead of storing whitespace-only strings.
   *
   * @param note the caller-submitted note (nullable)
   * @return the trimmed note or {@code null}
   */
  private static String normalizeUnitNote(String note) {
    return note == null || note.isBlank() ? null : note.trim();
  }

  /**
   * Updates a unit's name and the assigned ship. Per-unit optimistic lock — concurrent unit edits
   * across the mission don't collide.
   */
  @Transactional
  public Mission updateMissionUnit(
      @NotNull UUID missionId,
      @NotNull UUID unitId,
      String name,
      UUID shipTypeId,
      UUID shipId,
      boolean highValueUnit,
      Double frequency,
      UUID responsibleUserId,
      String note) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    MissionUnit missionUnit =
        mission.getAssignedUnits().stream()
            .filter(u -> u.getId().equals(unitId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("MissionUnit not found"));

    missionUnit.setHighValueUnit(highValueUnit);
    missionUnit.setResponsibleUser(resolveResponsibleUser(responsibleUserId));
    missionUnit.setNote(normalizeUnitNote(note));

    if (shipTypeId != null) {
      ShipType shipType =
          shipTypeRepository
              .findById(shipTypeId)
              .orElseThrow(() -> new NotFoundException("ShipType not found"));
      missionUnit.setShipType(shipType);
    } else {
      missionUnit.setShipType(null);
    }

    if (shipId != null) {
      Ship ship =
          shipRepository
              .findById(shipId)
              .orElseThrow(() -> new NotFoundException("Ship not found"));
      if (shipTypeId != null && !ship.getShipType().getId().equals(shipTypeId)) {
        throw new IllegalArgumentException("Ship does not match the specified ShipType");
      }
      // A ship already pinned to any unit of this mission is grandfathered: unrelated edits (name,
      // frequency, HVU) on a unit whose ship owner has since left the roster must not 400, and the
      // edit picker keeps offering every already-assigned ship so it round-trips. Only a ship new
      // to the mission must belong to a current participant.
      boolean alreadyAssignedInMission =
          mission.getAssignedUnits().stream()
              .map(MissionUnit::getShip)
              .filter(assigned -> assigned != null)
              .anyMatch(assigned -> assigned.getId().equals(shipId));
      if (!alreadyAssignedInMission && !isOwnerRegisteredParticipant(mission, ship)) {
        throw new IllegalArgumentException(
            "Ship owner is not a registered participant of the mission");
      }
      missionUnit.setShip(ship);
      if (shipTypeId == null) {
        missionUnit.setShipType(ship.getShipType());
      }
    } else {
      missionUnit.setShip(null);
    }

    // After ship / ship type resolution so a blank display name can derive from them (same rule
    // as addUnitToMission).
    missionUnit.setName(resolveUnitName(name, missionUnit));

    if (frequency != null) {
      double roundedFrequency =
          BigDecimal.valueOf(frequency).setScale(2, RoundingMode.HALF_UP).doubleValue();

      if (roundedFrequency < 100.00 || roundedFrequency > 999.99) {
        throw new IllegalArgumentException("Frequency must be between 100.00 and 999.99");
      }
      missionUnit.setFrequency(roundedFrequency);
    } else {
      missionUnit.setFrequency(null);
    }

    missionUnitRepository.save(missionUnit);
    auditService.record(
        AuditEventType.MISSION_UNIT_UPDATED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("unit", unitId));
    return mission;
  }

  /**
   * Tests whether the given ship's owner is signed up as a participant of the mission. Only
   * participants backed by a real {@link User} account count — guest participants have no account
   * and therefore never own hangar ships. Used by {@link #addUnitToMission} and {@link
   * #updateMissionUnit} to keep a unit's assigned ship constrained to ships brought by people
   * actually registered for the mission.
   *
   * @param mission the mission whose participant roster is searched, never {@code null}
   * @param ship the ship whose owner is checked for participation, never {@code null}
   * @return {@code true} if the ship's owner is a registered (account-backed) participant
   */
  private boolean isOwnerRegisteredParticipant(@NotNull Mission mission, @NotNull Ship ship) {
    UUID ownerId = ship.getOwner().getId();
    return mission.getParticipants().stream()
        .map(MissionParticipant::getUser)
        .filter(user -> user != null)
        .anyMatch(user -> user.getId().equals(ownerId));
  }

  /**
   * Collects the ships a unit of this mission may be crewed with: every ship owned by a registered
   * participant <em>plus</em> every ship already pinned to one of the mission's units. Participant
   * ships are intentionally NOT OrgUnit-scoped — a participant brings their own ship regardless of
   * which OrgUnit they belong to (so a cross-OrgUnit participant's ship becomes selectable), which
   * is why this reads {@link ShipRepository#findByOwnerIdIn} rather than the scoped hangar query.
   * Already-assigned ships are kept so editing a unit never silently drops a ship whose owner has
   * since left the roster. The result is deduplicated by ship id, participant ships first.
   *
   * @param missionId the mission whose selectable unit ships are collected, never {@code null}
   * @return the candidate ships for this mission's unit ship pickers
   * @throws NotFoundException when the mission id does not resolve
   */
  @Transactional(readOnly = true)
  public List<Ship> getSelectableUnitShips(@NotNull UUID missionId) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    java.util.Map<UUID, Ship> byId = new java.util.LinkedHashMap<>();

    Set<UUID> participantUserIds =
        mission.getParticipants().stream()
            .map(MissionParticipant::getUser)
            .filter(user -> user != null)
            .map(User::getId)
            .collect(java.util.stream.Collectors.toSet());
    if (!participantUserIds.isEmpty()) {
      shipRepository
          .findByOwnerIdIn(participantUserIds)
          .forEach(ship -> byId.put(ship.getId(), ship));
    }

    for (MissionUnit unit : mission.getAssignedUnits()) {
      Ship ship = unit.getShip();
      if (ship != null) {
        byId.putIfAbsent(ship.getId(), ship);
      }
    }

    return new java.util.ArrayList<>(byId.values());
  }

  /**
   * Removes a unit. Participants that were assigned to the unit fall back to the unassigned bucket
   * (their {@code unit}/{@code crew} references are cleared, the rows themselves stay).
   */
  @Transactional
  public Mission removeMissionUnit(@NotNull UUID missionId, @NotNull UUID unitId) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    boolean removed = mission.getAssignedUnits().removeIf(u -> u.getId().equals(unitId));

    if (!removed) {
      throw new NotFoundException("MissionUnit not found in this mission");
    }

    auditService.record(
        AuditEventType.MISSION_UNIT_REMOVED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("unit", unitId));
    return mission;
  }

  // --- Ablauf steps (procedure timeline) ---

  /**
   * Appends a step to the mission's Ablauf timeline. The new step lands at the end ({@code
   * orderIndex = max + 1}) and is initially not done. Validates and bumps the dedicated {@code
   * stepsVersion} section counter so a concurrent step edit surfaces as a 409 while never colliding
   * with a parallel core / schedule / flags edit.
   *
   * @param missionId the mission id
   * @param title the required step title
   * @param meta the optional free-text time/place hint
   * @param expectedStepsVersion the steps-section version the caller last saw
   * @return the managed mission
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when the mission is
   *     unknown
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when {@code
   *     expectedStepsVersion} is stale
   */
  @Transactional
  public Mission addStep(
      @NotNull UUID missionId, String title, String meta, @NotNull Long expectedStepsVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertSectionVersion(mission, MissionSection.STEPS, expectedStepsVersion, missionId);

    MissionStep step = new MissionStep();
    step.setTitle(title == null ? null : title.trim());
    step.setMeta(normalizeStepMeta(meta));
    step.setDone(false);
    step.setOrderIndex(nextStepOrderIndex(mission));
    mission.addStep(step);
    missionStepRepository.save(step);

    bumpSectionVersion(mission, MissionSection.STEPS);
    missionRepository.save(mission);
    auditService.record(
        AuditEventType.MISSION_STEP_ADDED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("step", step.getId()));
    return mission;
  }

  /**
   * Edits an existing Ablauf step's title and time/place hint. Mutates the managed child via
   * dirty-checking (no explicit child save) and bumps {@code stepsVersion}.
   *
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when {@code
   *     expectedStepsVersion} is stale
   */
  @Transactional
  public Mission updateStep(
      @NotNull UUID missionId,
      @NotNull UUID stepId,
      String title,
      String meta,
      @NotNull Long expectedStepsVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertSectionVersion(mission, MissionSection.STEPS, expectedStepsVersion, missionId);

    MissionStep step = findStep(mission, stepId);
    step.setTitle(title == null ? null : title.trim());
    step.setMeta(normalizeStepMeta(meta));

    bumpSectionVersion(mission, MissionSection.STEPS);
    missionRepository.save(mission);
    auditService.record(
        AuditEventType.MISSION_STEP_UPDATED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("step", stepId));
    return mission;
  }

  /**
   * Removes an Ablauf step and re-packs the remaining steps' {@code orderIndex} to 0..n-1 so the
   * timeline stays contiguous. Bumps {@code stepsVersion}.
   *
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when the step is not
   *     a child of the mission
   */
  @Transactional
  public Mission deleteStep(
      @NotNull UUID missionId, @NotNull UUID stepId, @NotNull Long expectedStepsVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertSectionVersion(mission, MissionSection.STEPS, expectedStepsVersion, missionId);

    boolean removed = mission.removeStep(stepId);
    if (!removed) {
      throw new NotFoundException("MissionStep not found in this mission");
    }
    repackStepOrder(mission);

    bumpSectionVersion(mission, MissionSection.STEPS);
    missionRepository.save(mission);
    auditService.record(
        AuditEventType.MISSION_STEP_REMOVED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("step", stepId));
    return mission;
  }

  /**
   * Reorders the mission's Ablauf steps. {@code orderedStepIds} must be exactly the mission's step
   * ids in the desired order; {@code orderIndex} is reassigned 0..n-1 by dirty-checking the managed
   * children (no per-child save, no bulk {@code clearAutomatically} query — so no detach/merge
   * double-version bump). The {@code stepsVersion} optimistic guard serialises concurrent reorders,
   * making a pessimistic lock unnecessary. Records a single reorder event (count only, no titles).
   *
   * @throws IllegalArgumentException when the id set does not match the mission's steps exactly
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when {@code
   *     expectedStepsVersion} is stale
   */
  @Transactional
  public Mission reorderSteps(
      @NotNull UUID missionId,
      @NotNull List<UUID> orderedStepIds,
      @NotNull Long expectedStepsVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertSectionVersion(mission, MissionSection.STEPS, expectedStepsVersion, missionId);

    Set<UUID> existingIds =
        mission.getSteps().stream().map(MissionStep::getId).collect(Collectors.toSet());
    if (orderedStepIds.size() != existingIds.size()
        || !existingIds.equals(new HashSet<>(orderedStepIds))) {
      throw new IllegalArgumentException("Reorder id set must match the mission's steps exactly");
    }

    Map<UUID, MissionStep> byId =
        mission.getSteps().stream().collect(Collectors.toMap(MissionStep::getId, s -> s));
    for (int i = 0; i < orderedStepIds.size(); i++) {
      byId.get(orderedStepIds.get(i)).setOrderIndex(i);
    }

    bumpSectionVersion(mission, MissionSection.STEPS);
    missionRepository.save(mission);
    auditService.record(
        AuditEventType.MISSION_STEP_REORDERED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("count", existingIds.size()));
    return mission;
  }

  /**
   * Toggles a step's shared {@code done} flag to the requested state and bumps {@code
   * stepsVersion}. The single "current phase" (first not-done step) is derived on read, never
   * stored.
   *
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when the step is not
   *     a child of the mission
   */
  @Transactional
  public Mission toggleStepDone(
      @NotNull UUID missionId,
      @NotNull UUID stepId,
      boolean done,
      @NotNull Long expectedStepsVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertSectionVersion(mission, MissionSection.STEPS, expectedStepsVersion, missionId);

    MissionStep step = findStep(mission, stepId);
    step.setDone(done);

    bumpSectionVersion(mission, MissionSection.STEPS);
    missionRepository.save(mission);
    auditService.record(
        AuditEventType.MISSION_STEP_DONE_CHANGED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("step", stepId).with("done", done));
    return mission;
  }

  /**
   * Finds a managed step by id within the mission, or throws.
   *
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when the step is not
   *     a child of the mission
   */
  private static MissionStep findStep(@NotNull Mission mission, @NotNull UUID stepId) {
    return mission.getSteps().stream()
        .filter(s -> s.getId() != null && s.getId().equals(stepId))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("MissionStep not found in this mission"));
  }

  /**
   * Normalises a step's optional time/place hint: trims surrounding whitespace and collapses blank
   * input to {@code null}.
   */
  private static String normalizeStepMeta(String meta) {
    return meta == null || meta.isBlank() ? null : meta.trim();
  }

  /** Returns the {@code orderIndex} to assign a newly appended step (max existing + 1, or 0). */
  private static int nextStepOrderIndex(@NotNull Mission mission) {
    int max = -1;
    for (MissionStep s : mission.getSteps()) {
      max = Math.max(max, s.getOrderIndex());
    }
    return max + 1;
  }

  /** Re-assigns the remaining steps' {@code orderIndex} to a contiguous 0..n-1 by current order. */
  private static void repackStepOrder(@NotNull Mission mission) {
    List<MissionStep> ordered = new ArrayList<>(mission.getSteps());
    ordered.sort(Comparator.comparingInt(MissionStep::getOrderIndex));
    for (int i = 0; i < ordered.size(); i++) {
      ordered.get(i).setOrderIndex(i);
    }
  }

  // --- Mission goals (Ziele) ---

  /**
   * Appends a goal (Ziel) to a mission at the end of the list (next {@code orderIndex}) and bumps
   * {@code objectivesVersion}. Guarded by the dedicated goals-section counter so editing the goals
   * never collides with a concurrent core / schedule / flags / Ablauf edit. Records an audit event
   * carrying the goal id and kind only — never the title (user free text).
   *
   * @param missionId the mission id
   * @param title the required goal text
   * @param kind the classification (primary / secondary / non-goal)
   * @param expectedObjectivesVersion the goals-section version the caller last saw
   * @return the managed mission
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when the mission is
   *     unknown
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when {@code
   *     expectedObjectivesVersion} is stale
   */
  @Transactional
  public Mission addObjective(
      @NotNull UUID missionId,
      String title,
      @NotNull MissionObjectiveKind kind,
      @NotNull Long expectedObjectivesVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertSectionVersion(mission, MissionSection.OBJECTIVES, expectedObjectivesVersion, missionId);

    MissionObjective objective = new MissionObjective();
    objective.setTitle(title == null ? null : title.trim());
    objective.setKind(kind);
    objective.setOrderIndex(nextObjectiveOrderIndex(mission));
    mission.addObjective(objective);
    missionObjectiveRepository.save(objective);

    bumpSectionVersion(mission, MissionSection.OBJECTIVES);
    missionRepository.save(mission);
    auditService.record(
        AuditEventType.MISSION_OBJECTIVE_ADDED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("objective", objective.getId()).with("kind", kind));
    return mission;
  }

  /**
   * Edits an existing goal's text and classification. Mutates the managed child via dirty-checking
   * (no explicit child save) and bumps {@code objectivesVersion}.
   *
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when {@code
   *     expectedObjectivesVersion} is stale
   */
  @Transactional
  public Mission updateObjective(
      @NotNull UUID missionId,
      @NotNull UUID objectiveId,
      String title,
      @NotNull MissionObjectiveKind kind,
      @NotNull Long expectedObjectivesVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertSectionVersion(mission, MissionSection.OBJECTIVES, expectedObjectivesVersion, missionId);

    MissionObjective objective = findObjective(mission, objectiveId);
    objective.setTitle(title == null ? null : title.trim());
    objective.setKind(kind);

    bumpSectionVersion(mission, MissionSection.OBJECTIVES);
    missionRepository.save(mission);
    auditService.record(
        AuditEventType.MISSION_OBJECTIVE_UPDATED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("objective", objectiveId).with("kind", kind));
    return mission;
  }

  /**
   * Removes a goal and re-packs the remaining goals' {@code orderIndex} to 0..n-1 so the list stays
   * contiguous. Bumps {@code objectivesVersion}.
   *
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when the goal is not
   *     a child of the mission
   */
  @Transactional
  public Mission deleteObjective(
      @NotNull UUID missionId, @NotNull UUID objectiveId, @NotNull Long expectedObjectivesVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertSectionVersion(mission, MissionSection.OBJECTIVES, expectedObjectivesVersion, missionId);

    boolean removed = mission.removeObjective(objectiveId);
    if (!removed) {
      throw new NotFoundException("MissionObjective not found in this mission");
    }
    repackObjectiveOrder(mission);

    bumpSectionVersion(mission, MissionSection.OBJECTIVES);
    missionRepository.save(mission);
    auditService.record(
        AuditEventType.MISSION_OBJECTIVE_REMOVED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("objective", objectiveId));
    return mission;
  }

  /**
   * Reorders the mission's goals. {@code orderedObjectiveIds} must be exactly the mission's goal
   * ids in the desired order; {@code orderIndex} is reassigned 0..n-1 by dirty-checking the managed
   * children (no per-child save, no bulk {@code clearAutomatically} query — so no detach/merge
   * double-version bump). The {@code objectivesVersion} optimistic guard serialises concurrent
   * reorders, making a pessimistic lock unnecessary. Records a single reorder event (count only).
   *
   * @throws IllegalArgumentException when the id set does not match the mission's goals exactly
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when {@code
   *     expectedObjectivesVersion} is stale
   */
  @Transactional
  public Mission reorderObjectives(
      @NotNull UUID missionId,
      @NotNull List<UUID> orderedObjectiveIds,
      @NotNull Long expectedObjectivesVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertSectionVersion(mission, MissionSection.OBJECTIVES, expectedObjectivesVersion, missionId);

    Set<UUID> existingIds =
        mission.getObjectives().stream().map(MissionObjective::getId).collect(Collectors.toSet());
    if (orderedObjectiveIds.size() != existingIds.size()
        || !existingIds.equals(new HashSet<>(orderedObjectiveIds))) {
      throw new IllegalArgumentException("Reorder id set must match the mission's goals exactly");
    }

    Map<UUID, MissionObjective> byId =
        mission.getObjectives().stream().collect(Collectors.toMap(MissionObjective::getId, o -> o));
    for (int i = 0; i < orderedObjectiveIds.size(); i++) {
      byId.get(orderedObjectiveIds.get(i)).setOrderIndex(i);
    }

    bumpSectionVersion(mission, MissionSection.OBJECTIVES);
    missionRepository.save(mission);
    auditService.record(
        AuditEventType.MISSION_OBJECTIVE_REORDERED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("count", existingIds.size()));
    return mission;
  }

  /**
   * Finds a managed goal by id within the mission, or throws.
   *
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when the goal is not
   *     a child of the mission
   */
  private static MissionObjective findObjective(
      @NotNull Mission mission, @NotNull UUID objectiveId) {
    return mission.getObjectives().stream()
        .filter(o -> o.getId() != null && o.getId().equals(objectiveId))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("MissionObjective not found in this mission"));
  }

  /** Returns the {@code orderIndex} to assign a newly appended goal (max existing + 1, or 0). */
  private static int nextObjectiveOrderIndex(@NotNull Mission mission) {
    int max = -1;
    for (MissionObjective o : mission.getObjectives()) {
      max = Math.max(max, o.getOrderIndex());
    }
    return max + 1;
  }

  /** Re-assigns the remaining goals' {@code orderIndex} to a contiguous 0..n-1 by current order. */
  private static void repackObjectiveOrder(@NotNull Mission mission) {
    List<MissionObjective> ordered = new ArrayList<>(mission.getObjectives());
    ordered.sort(Comparator.comparingInt(MissionObjective::getOrderIndex));
    for (int i = 0; i < ordered.size(); i++) {
      ordered.get(i).setOrderIndex(i);
    }
  }

  /**
   * Adds a crew (ship-level grouping) under a unit. Crews are the second level of the participant
   * hierarchy and pin participants to a specific ship's role.
   */
  @Transactional
  public Mission addCrewToShip(
      @NotNull UUID missionId,
      @NotNull UUID missionUnitId,
      @NotNull UUID participantId,
      @NotNull Set<UUID> jobTypeIds) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    MissionUnit missionShip =
        mission.getAssignedUnits().stream()
            .filter(ms -> ms != null && ms.getId() != null && ms.getId().equals(missionUnitId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("MissionUnit not found in this mission"));

    MissionParticipant participant =
        mission.getParticipants().stream()
            .filter(p -> p.getId().equals(participantId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Participant not found in this mission"));

    boolean isAlreadyAssigned =
        mission.getAssignedUnits().stream()
            .flatMap(u -> u.getCrew().stream())
            .anyMatch(c -> c.getParticipant().getId().equals(participantId));

    if (isAlreadyAssigned) {
      throw new de.greluc.krt.profit.basetool.backend.exception.DuplicateEntityException(
          "error.mission.crew.duplicate");
    }

    MissionCrew crew = new MissionCrew();
    crew.setMissionUnit(missionShip);
    crew.setParticipant(participant);

    // Fetch and validate JobTypes
    Set<JobType> jobTypes = validateAndFetchJobTypes(jobTypeIds);

    crew.setJobTypes(jobTypes);

    missionShip.getCrew().add(crew);
    missionCrewRepository.save(crew);
    auditService.record(
        AuditEventType.MISSION_CREW_ADDED,
        mission.getId(),
        mission.getName(),
        participant.getUser() != null ? participant.getUser().getId() : null,
        AuditDetails.of("unit", missionUnitId).with("crew", crew.getId()));
    return mission;
  }

  /** Updates a crew's name, role, and assigned ship. */
  @Transactional
  public Mission updateCrewInShip(
      @NotNull UUID missionId,
      @NotNull UUID missionUnitId,
      @NotNull UUID crewId,
      @NotNull Set<UUID> jobTypeIds) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    MissionUnit missionUnit =
        mission.getAssignedUnits().stream()
            .filter(u -> u.getId().equals(missionUnitId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("MissionUnit not found"));

    MissionCrew crew =
        missionUnit.getCrew().stream()
            .filter(c -> c.getId().equals(crewId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Crew member not found in this unit"));

    Set<JobType> jobTypes = validateAndFetchJobTypes(jobTypeIds);
    crew.setJobTypes(jobTypes);

    missionCrewRepository.save(crew);
    auditService.record(
        AuditEventType.MISSION_CREW_UPDATED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("unit", missionUnitId).with("crew", crewId));
    return mission;
  }

  /**
   * Removes a crew. Participants assigned to the crew fall back to the parent unit's unassigned
   * slot.
   */
  @Transactional
  public Mission removeCrewFromShip(
      @NotNull UUID missionId, @NotNull UUID missionUnitId, @NotNull UUID crewId) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    MissionUnit missionUnit =
        mission.getAssignedUnits().stream()
            .filter(u -> u.getId().equals(missionUnitId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("MissionUnit not found"));

    boolean removed = missionUnit.getCrew().removeIf(c -> c.getId().equals(crewId));

    if (!removed) {
      throw new NotFoundException("Crew member not found in this unit");
    }

    auditService.record(
        AuditEventType.MISSION_CREW_REMOVED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("unit", missionUnitId).with("crew", crewId));
    return mission;
  }

  private Set<JobType> validateAndFetchJobTypes(Set<UUID> jobTypeIds) {
    Set<JobType> jobTypes = new HashSet<>();
    if (jobTypeIds != null && !jobTypeIds.isEmpty()) {
      for (UUID jtId : jobTypeIds) {
        JobType jt =
            jobTypeRepository
                .findById(jtId)
                .orElseThrow(() -> new NotFoundException("JobType not found: " + jtId));

        if (jt.getArchetype() != JobTypeArchetype.CREW) {
          throw new IllegalArgumentException(
              "JobType " + jt.getName() + " is not of archetype CREW");
        }
        jobTypes.add(jt);
      }
    }
    return jobTypes;
  }

  /**
   * Creates a sub-mission under a parent mission. Sub-missions are independent missions that roll
   * up to their parent in the operation overview. {@code parent} and {@code owningSquadron} are
   * stamped server-side from the path-resolved parent — the request body has no say in either,
   * which is the mass-assignment fix from audit finding C-3.
   *
   * @param parentMissionId path-resolved parent id (authoritative)
   * @param request create payload (validated at the controller boundary)
   * @return the persisted sub-mission entity
   * @throws NotFoundException when {@code parentMissionId} or {@code operationId} does not resolve
   */
  @Transactional
  public Mission addSubMission(
      @NotNull UUID parentMissionId, @NotNull CreateMissionRequest request) {
    Mission parent =
        missionRepository
            .findById(parentMissionId)
            .orElseThrow(() -> new NotFoundException("Parent mission not found"));

    Mission subMission = new Mission();
    applyCreatePayload(subMission, request);
    subMission.setParent(parent);
    subMission.setOwningOrgUnit(parent.getOwningOrgUnit());

    validateMissionTimes(subMission);
    Mission saved = missionRepository.save(subMission);
    auditService.record(
        AuditEventType.MISSION_CREATED,
        subMission.getId(),
        subMission.getName(),
        null,
        AuditDetails.of("parent", parentMissionId));
    return saved;
  }

  /**
   * Creates or updates a radio frequency entry on a mission. Single endpoint for both because the
   * form's id field discriminates (null = create, non-null = update).
   */
  @Transactional
  public Mission addOrUpdateMissionFrequency(
      @NotNull UUID missionId, @NotNull UUID frequencyTypeId, @NotNull BigDecimal value) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    FrequencyType frequencyType =
        frequencyTypeRepository
            .findById(frequencyTypeId)
            .orElseThrow(() -> new NotFoundException("FrequencyType not found"));

    // Null-guard the type reference: since REQ-MISSION-014 the frequencies collection also holds
    // custom rows (frequencyType == null), so an unguarded getFrequencyType().getId() would NPE
    // when the mission already carries a custom frequency and a typed one is upserted.
    Optional<MissionFrequency> existingOpt =
        mission.getFrequencies().stream()
            .filter(f -> f.getFrequencyType() != null)
            .filter(f -> f.getFrequencyType().getId().equals(frequencyTypeId))
            .findFirst();

    if (existingOpt.isPresent()) {
      MissionFrequency existing = existingOpt.orElseThrow();
      existing.setValue(value);
      missionFrequencyRepository.save(existing);
    } else {
      MissionFrequency newFreq = new MissionFrequency();
      newFreq.setMission(mission);
      newFreq.setFrequencyType(frequencyType);
      newFreq.setValue(value);
      mission.getFrequencies().add(newFreq);
      missionFrequencyRepository.save(newFreq);
    }

    auditService.record(
        AuditEventType.MISSION_FREQUENCY_CHANGED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("frequencyType", frequencyTypeId));
    return mission;
  }

  /**
   * Adds a custom (mission-specific) radio frequency — a free-text label plus a value — to a
   * mission (REQ-MISSION-014). The new row leaves {@link MissionFrequency#getFrequencyType()} null;
   * the check constraint added in V201 enforces the typed-XOR-named invariant at the data layer.
   *
   * <p>The mission's {@code frequencies} collection is {@code @OptimisticLock(excluded = true)}, so
   * persisting the child neither bumps the mission's global {@code @Version} nor any section
   * counter — a frequency change never 409s a concurrent core/schedule/flags edit, matching the
   * typed upsert.
   *
   * @param missionId the mission to attach the channel to.
   * @param name the free-text channel label (validated non-blank ≤100 chars at the boundary).
   * @param value the frequency value (range-validated 0 – 999.99 at the boundary).
   * @return the managed mission with the new custom frequency attached.
   * @throws NotFoundException when the mission is unknown.
   */
  @Transactional
  public Mission addCustomMissionFrequency(
      @NotNull UUID missionId, @NotNull String name, @NotNull BigDecimal value) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    MissionFrequency freq = new MissionFrequency();
    freq.setMission(mission);
    freq.setName(name.trim());
    freq.setValue(value);
    mission.getFrequencies().add(freq);
    MissionFrequency saved = missionFrequencyRepository.save(freq);

    // Audit finding parity with the typed path: the label is user free text, so log only the row id
    // (never the name), per REQ-AUDIT-001's "no free text / no PII in the details payload" rule.
    auditService.record(
        AuditEventType.MISSION_FREQUENCY_CHANGED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("custom", saved.getId()));
    return mission;
  }

  /**
   * Updates the label and value of an existing custom frequency (REQ-MISSION-014). Optimistic-locks
   * on the frequency row's own {@code @Version} (echoed back from the rendered row); a stale
   * version surfaces as HTTP 409. Rejects an attempt to reach a typed (global) frequency row
   * through this path so the two families cannot be crossed.
   *
   * @param missionId the owning mission id.
   * @param frequencyId the custom frequency row id.
   * @param name the new channel label.
   * @param value the new frequency value.
   * @param expectedVersion the optimistic-lock version the caller echoed back.
   * @return the managed mission with the updated custom frequency.
   * @throws NotFoundException when the mission or a matching custom frequency is unknown.
   * @throws IllegalArgumentException when the target row is a typed (global) frequency.
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the supplied
   *     version is stale.
   */
  @Transactional
  public Mission updateCustomMissionFrequency(
      @NotNull UUID missionId,
      @NotNull UUID frequencyId,
      @NotNull String name,
      @NotNull BigDecimal value,
      @NotNull Long expectedVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    MissionFrequency freq =
        mission.getFrequencies().stream()
            .filter(f -> f.getId() != null && f.getId().equals(frequencyId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Frequency not found in this mission"));

    if (freq.getFrequencyType() != null) {
      throw new IllegalArgumentException(
          "Cannot edit a typed frequency through the custom-frequency endpoint");
    }

    long current = freq.getVersion() == null ? 0L : freq.getVersion();
    if (!expectedVersion.equals(current)) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          MissionFrequency.class, frequencyId);
    }

    freq.setName(name.trim());
    freq.setValue(value);
    missionFrequencyRepository.save(freq);

    auditService.record(
        AuditEventType.MISSION_FREQUENCY_CHANGED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("custom", frequencyId));
    return mission;
  }

  /** Removes a frequency entry from a mission. */
  @Transactional
  public Mission removeMissionFrequency(@NotNull UUID missionId, @NotNull UUID frequencyId) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    boolean removed =
        mission.getFrequencies().removeIf(f -> f.getId() != null && f.getId().equals(frequencyId));
    if (!removed) {
      throw new NotFoundException("Frequency not found in this mission");
    }

    auditService.record(
        AuditEventType.MISSION_FREQUENCY_REMOVED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("frequency", frequencyId));
    return mission;
  }

  /**
   * Transfers mission ownership to another user. The previous owner is automatically added to the
   * manager list so they don't lose all access in one click.
   *
   * <p>Versioning: bumps the dedicated {@code mission_ownership} version, NOT the mission's main
   * version — so concurrent participant or finance edits don't race with the owner change.
   */
  @Transactional
  public Mission setMissionOwner(@NotNull UUID missionId, @NotNull UUID userId) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    mission.setOwner(user);
    // Mission.owner is @OptimisticLock(excluded=true), so this does NOT bump Mission.version.
    // Sub-level optimistic locking for ownership is provided by the dedicated MissionOwnership
    // aggregate; see updateMissionOwner(UUID,UUID,Long) below.
    upsertMissionOwnership(mission, user, null);
    auditService.record(
        AuditEventType.MISSION_OWNER_CHANGED, mission.getId(), mission.getName(), userId, null);
    return mission;
  }

  /**
   * Version-checked owner change for multi-user concurrency (Option A). The {@code
   * expectedOwnershipVersion} must match {@link MissionOwnership#getVersion()}; otherwise a 409
   * {@link org.springframework.orm.ObjectOptimisticLockingFailureException} is raised.
   *
   * <p>This method intentionally does NOT bump {@code Mission.version} (the owner association is
   * excluded from parent optimistic locking), so concurrent edits on other sections of the same
   * mission remain unaffected.
   */
  @Transactional
  public Mission updateMissionOwner(
      @NotNull UUID missionId, @NotNull UUID userId, @NotNull Long expectedOwnershipVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    mission.setOwner(user);
    upsertMissionOwnership(mission, user, expectedOwnershipVersion);
    auditService.record(
        AuditEventType.MISSION_OWNER_CHANGED, mission.getId(), mission.getName(), userId, null);
    return mission;
  }

  /**
   * Returns the current optimistic-lock version of the mission ownership aggregate (0 if absent).
   *
   * @param missionId mission id
   * @return current value of the dedicated {@code mission_ownership.version} field — frontend
   *     echoes this back on the next owner-change call so two admins editing the same mission
   *     surface a 409 instead of silently overwriting
   */
  public long getMissionOwnershipVersion(@NotNull UUID missionId) {
    return missionOwnershipRepository
        .findByMissionId(missionId)
        .map(mo -> mo.getVersion() == null ? 0L : mo.getVersion())
        .orElse(0L);
  }

  private void upsertMissionOwnership(Mission mission, User newOwner, Long expectedVersion) {
    MissionOwnership ownership =
        missionOwnershipRepository
            .findByMissionId(mission.getId())
            .orElseGet(
                () -> {
                  MissionOwnership fresh = new MissionOwnership();
                  fresh.setMission(mission);
                  return fresh;
                });
    if (expectedVersion != null && ownership.getId() != null) {
      Long currentVersion = ownership.getVersion() == null ? 0L : ownership.getVersion();
      if (!expectedVersion.equals(currentVersion)) {
        throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
            MissionOwnership.class, ownership.getId());
      }
    }
    ownership.setOwner(newOwner);
    missionOwnershipRepository.save(ownership);
  }

  /**
   * Reassigns the owning org unit of an existing mission (REQ-ORG-018 / ADR-0050). The mission is
   * re-homed to {@code targetOrgUnitId} (a Staffel, Spezialkommando, Bereich or
   * Organisationsleitung) or made an ownerless leadership mission when {@code targetOrgUnitId} is
   * {@code null}. The target is validated against the caller's assignable scope by {@link
   * OwnerScopeService#resolveReassignTargetOrgUnit(UUID)} — the orthogonal second gate on top of
   * the per-mission {@code canChangeOwner} gate the controller enforces.
   *
   * <p>Versioning: validates and bumps the dedicated {@code owningOrgUnitVersion} counter only. The
   * association and column are {@code @OptimisticLock(excluded = true)}, so the global {@code
   * Mission.version} and the other section counters stay untouched and concurrent edits on other
   * sections of the same mission remain valid (Option A / multi-user concurrency).
   *
   * <p>Re-homing retroactively re-scopes who may see/edit the mission (the {@code owningOrgUnit} +
   * {@code isInternal} visibility gates); it deliberately does NOT cascade to the mission's
   * participants, finance entries, units or linked operation, which keep their own ownership.
   *
   * @param missionId mission to reassign.
   * @param targetOrgUnitId the target org-unit id, or {@code null} for an ownerless mission.
   * @param expectedOwningOrgUnitVersion expected value of {@code Mission.owningOrgUnitVersion}.
   * @return the managed mission with the new owning org unit applied.
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when the mission is
   *     unknown.
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when {@code
   *     expectedOwningOrgUnitVersion} is stale.
   * @throws org.springframework.security.access.AccessDeniedException when the caller may not
   *     assign to the requested target.
   */
  @Transactional
  public Mission updateOwningOrgUnit(
      @NotNull UUID missionId, UUID targetOrgUnitId, @NotNull Long expectedOwningOrgUnitVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertSectionVersion(
        mission, MissionSection.OWNING_ORG_UNIT, expectedOwningOrgUnitVersion, missionId);

    OrgUnit previous = mission.getOwningOrgUnit();
    OrgUnit target = ownerScopeService.resolveReassignTargetOrgUnit(targetOrgUnitId);
    mission.setOwningOrgUnit(target);
    bumpSectionVersion(mission, MissionSection.OWNING_ORG_UNIT);
    Mission saved = missionRepository.save(mission);
    // Audit detail carries org-unit identifiers + kinds only — no PII, no user free text (the
    // mission name is the entity label, handled separately by the audit record).
    auditService.record(
        AuditEventType.MISSION_OWNING_ORG_UNIT_CHANGED,
        mission.getId(),
        mission.getName(),
        null,
        AuditDetails.of("fromOrgUnit", formatOrgUnitRef(previous))
            .with("toOrgUnit", formatOrgUnitRef(target)));
    return saved;
  }

  /**
   * Formats an org unit as a non-PII audit reference ({@code <kind>:<id>}), or {@code "none"} for
   * an ownerless target. Used in the {@link AuditEventType#MISSION_OWNING_ORG_UNIT_CHANGED} detail
   * payload, which must carry identifiers and kinds only — never names or other free text.
   *
   * @param orgUnit the org unit to format, or {@code null} for an ownerless target.
   * @return a stable {@code kind:id} reference, or {@code "none"} when {@code orgUnit} is {@code
   *     null}.
   */
  private static String formatOrgUnitRef(OrgUnit orgUnit) {
    return orgUnit == null ? "none" : orgUnit.getKind() + ":" + orgUnit.getId();
  }

  /**
   * Assigns or clears a mission's party lead (Partyleiter). The party lead is either a linked
   * registered user or a free-text guest handle — mutually exclusive, mirroring the participant
   * {@code user}/{@code guestName} model. Free-text-to-user resolution is performed by the caller
   * (controller, like the participant-add endpoints); this method persists whatever {@code userId}
   * / {@code guestName} it is handed:
   *
   * <ul>
   *   <li>{@code userId != null} → link the registered user, clear any guest handle;
   *   <li>{@code userId == null} and {@code guestName} non-blank → store the trimmed guest handle,
   *       clear any linked user;
   *   <li>both {@code null}/blank → clear the party lead entirely.
   * </ul>
   *
   * <p>Versioning: validates and bumps the dedicated {@code partyLeadVersion} counter only. The
   * association and columns are {@code @OptimisticLock(excluded = true)}, so the global {@code
   * Mission.version} and the other section counters stay untouched and concurrent edits on other
   * sections of the same mission remain valid (Option A / multi-user concurrency).
   *
   * @param missionId mission to update
   * @param userId registered party-lead reference, or {@code null}
   * @param guestName free-text party-lead handle, or {@code null}
   * @param expectedPartyLeadVersion expected value of {@code Mission.partyLeadVersion}
   * @return the managed mission with the party lead applied
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when the mission or
   *     the referenced user is unknown
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when {@code
   *     expectedPartyLeadVersion} is stale
   */
  @Transactional
  public Mission setPartyLead(
      @NotNull UUID missionId,
      UUID userId,
      String guestName,
      @NotNull Long expectedPartyLeadVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertSectionVersion(mission, MissionSection.PARTY_LEAD, expectedPartyLeadVersion, missionId);

    if (userId != null) {
      User user =
          userRepository
              .findById(userId)
              .orElseThrow(() -> new NotFoundException("User not found"));
      mission.setPartyLeadUser(user);
      mission.setPartyLeadGuestName(null);
    } else if (guestName != null && !guestName.isBlank()) {
      mission.setPartyLeadUser(null);
      mission.setPartyLeadGuestName(guestName.trim());
    } else {
      mission.setPartyLeadUser(null);
      mission.setPartyLeadGuestName(null);
    }

    bumpSectionVersion(mission, MissionSection.PARTY_LEAD);
    Mission saved = missionRepository.save(mission);
    auditService.record(
        AuditEventType.MISSION_PARTY_LEAD_CHANGED,
        mission.getId(),
        mission.getName(),
        userId,
        AuditDetails.of(
            "kind",
            userId != null
                ? "user"
                : (guestName != null && !guestName.isBlank() ? "guest" : "cleared")));
    return saved;
  }

  /**
   * Adds a co-manager. Co-managers can edit the mission with the same privileges as the owner
   * (except they cannot transfer ownership — see {@link MissionSecurityService#canChangeOwner}).
   */
  @Transactional
  public Mission addManager(@NotNull UUID missionId, @NotNull UUID userId) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    mission.getManagers().add(user);
    auditService.record(
        AuditEventType.MISSION_MANAGER_ADDED, mission.getId(), mission.getName(), userId, null);
    return mission;
  }

  /**
   * Removes a co-manager. The owner cannot be removed via this method — use {@link
   * #setMissionOwner} to transfer ownership first.
   */
  @Transactional
  public Mission removeManager(@NotNull UUID missionId, @NotNull UUID userId) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    mission.getManagers().removeIf(u -> u.getId().equals(userId));
    auditService.record(
        AuditEventType.MISSION_MANAGER_REMOVED, mission.getId(), mission.getName(), userId, null);
    return mission;
  }

  /**
   * Resolves every org unit a registered user belongs to (Staffel and/or Spezialkommandos) into the
   * managed {@link OrgUnit} entities to stamp on the participant. Reads the membership rows via
   * {@link OrgUnitMembershipService#findAllMembershipsForUser(UUID)} (already Staffel-first, then
   * SK alphabetical) and materialises each org-unit id through the polymorphic {@code
   * OrgUnitRepository}. A user with no memberships yields an empty list — there is deliberately no
   * IRIDIUM fallback, so an admin who belongs to nothing shows no affiliation on the roster.
   *
   * @param userId the registered user whose memberships to resolve; never {@code null}.
   * @return the managed org-unit entities, membership order preserved; never {@code null}, possibly
   *     empty.
   */
  private java.util.List<OrgUnit> resolveMembershipOrgUnits(@NotNull UUID userId) {
    java.util.List<UUID> orgUnitIds =
        orgUnitMembershipService.findAllMembershipsForUser(userId).stream()
            .map(m -> m.getId().getOrgUnitId())
            .toList();
    if (orgUnitIds.isEmpty()) {
      return java.util.List.of();
    }
    // One batched lookup instead of findById per membership; re-key to preserve membership order
    // and drop any id that no longer resolves (mirrors the previous nonNull filter).
    java.util.Map<UUID, OrgUnit> byId =
        orgUnitRepository.findAllById(orgUnitIds).stream()
            .collect(java.util.stream.Collectors.toMap(OrgUnit::getId, o -> o));
    return orgUnitIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
  }

  /**
   * Resolves a caller-submitted {@code orgUnitIds} list for a GUEST participant entry to the org
   * units to persist. A guest's org-unit affiliation is mission-scoped roster metadata only: it is
   * a label on a single mission's participant row, drives nothing but the roster badges (see {@code
   * MissionMapper.orgUnitsToReferenceDtos}), grants no permissions and touches no user data. Anyone
   * who may add the guest at all — the endpoint's {@code canSeeMission} gate already governs that,
   * including anonymous sign-ups on public (non-internal) missions — may therefore label it with
   * any Staffel or SK:
   *
   * <ul>
   *   <li>{@code null} / empty input → empty list (no affiliation).
   *   <li>otherwise → every id that resolves to a real {@link OrgUnit} is kept, in submission
   *       order; a {@code null} or unknown id is silently skipped (a roster mislabel is not a
   *       forgery worth a 403, and a non-resolving id simply yields no badge).
   * </ul>
   *
   * <p>This deliberately drops the former audit-H-3 authorization filter (admin / own-membership
   * required) for guests: that gate denied an SK lead — and every anonymous sign-up — from tagging
   * a guest with the relevant org unit, even though the tag carries no authority. Registered-user
   * participants are unaffected: their affiliations are auto-derived from their actual memberships
   * in the caller paths, never from this submitted list.
   *
   * @param submittedOrgUnitIds the caller-supplied org-unit ids from the request DTO.
   * @return the managed org-unit entities to persist on the guest participant; never {@code null},
   *     possibly empty.
   */
  private java.util.List<OrgUnit> resolveGuestSubmittedOrgUnits(
      java.util.List<UUID> submittedOrgUnitIds) {
    if (submittedOrgUnitIds == null || submittedOrgUnitIds.isEmpty()) {
      return java.util.List.of();
    }
    java.util.List<OrgUnit> resolved = new java.util.ArrayList<>();
    for (UUID orgUnitId : submittedOrgUnitIds) {
      if (orgUnitId == null) {
        continue;
      }
      orgUnitRepository.findById(orgUnitId).ifPresent(resolved::add);
    }
    return resolved;
  }
}

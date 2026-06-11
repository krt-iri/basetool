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

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.FrequencyType;
import de.greluc.krt.iri.basetool.backend.model.JobType;
import de.greluc.krt.iri.basetool.backend.model.JobTypeArchetype;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionCrew;
import de.greluc.krt.iri.basetool.backend.model.MissionFrequency;
import de.greluc.krt.iri.basetool.backend.model.MissionOwnership;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.MissionUnit;
import de.greluc.krt.iri.basetool.backend.model.Operation;
import de.greluc.krt.iri.basetool.backend.model.OrgUnit;
import de.greluc.krt.iri.basetool.backend.model.PayoutPreference;
import de.greluc.krt.iri.basetool.backend.model.Ship;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.request.CreateMissionRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.request.UpdateMissionRequest;
import de.greluc.krt.iri.basetool.backend.repository.FrequencyTypeRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobTypeRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionCrewRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionFrequencyRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionOwnershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionUnitRepository;
import de.greluc.krt.iri.basetool.backend.repository.OperationRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
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
  private final FrequencyTypeRepository frequencyTypeRepository;
  private final MissionFrequencyRepository missionFrequencyRepository;
  private final MissionOwnershipRepository missionOwnershipRepository;
  private final OperationRepository operationRepository;
  private final UserService userService;
  private final OwnerScopeService ownerScopeService;
  private final AuthHelperService authHelperService;
  private final OrgUnitMembershipService orgUnitMembershipService;
  private final de.greluc.krt.iri.basetool.backend.repository.OrgUnitRepository orgUnitRepository;

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
  public List<de.greluc.krt.iri.basetool.backend.model.dto.MissionReferenceDto>
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
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no match
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
   * @param allowInternal whether internal missions should be included
   * @return the next mission, or empty when none upcoming
   */
  public Optional<Mission> getNextMission(boolean allowInternal) {
    Optional<Mission> next = findNextMissionHead(Instant.now(), allowInternal);
    // The limit-1 lookup above is intentionally not graphed — a collection fetch combined with the
    // limit forces Hibernate into in-memory pagination (HHH90003004). Re-fetch the single hit by id
    // through the graphed findById so participants / assignedUnits are eagerly loaded for the
    // mapper
    // (and the home-page guest redaction) without paginating the whole upcoming-mission set.
    return next.map(Mission::getId).flatMap(missionRepository::findById);
  }

  /**
   * Resolves the ungraphed limit-1 head of the next-mission lookup, filtered to {@link
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
   * Persists a new mission from the request DTO. Every server-managed column ({@code id}, {@code
   * version}, the section counters, {@code owner}, {@code owningSquadron}, {@code parent}, the
   * sub-aggregate collections) is set inside this method — the request record carries only
   * caller-controllable fields, which makes the mass-assignment vector exploited in audit finding
   * C-3 structurally impossible.
   *
   * @param request create payload (already validated by Bean Validation at the controller boundary)
   * @return the persisted mission entity
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when {@code operationId}
   *     does not resolve
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

    return missionRepository.save(mission);
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
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when the mission or any
   *     referenced id is unknown
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

    bumpCoreVersion(mission);
    bumpScheduleVersion(mission);
    bumpFlagsVersion(mission);

    return missionRepository.save(mission);
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
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when any referenced id
   *     is unknown
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
      @NotNull Long expectedCoreVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertCoreVersion(mission, expectedCoreVersion, missionId);

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

    bumpCoreVersion(mission);
    return missionRepository.save(mission);
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
    assertScheduleVersion(mission, expectedScheduleVersion, missionId);
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
    bumpScheduleVersion(mission);
    return missionRepository.save(mission);
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
    assertFlagsVersion(mission, expectedFlagsVersion, missionId);
    mission.setIsInternal(isInternal);
    bumpFlagsVersion(mission);
    return missionRepository.save(mission);
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
    bumpScheduleVersion(mission);
  }

  private void assertCoreVersion(
      @NotNull Mission mission, @NotNull Long expectedVersion, @NotNull UUID missionId) {
    long current = mission.getCoreVersion() == null ? 0L : mission.getCoreVersion();
    if (!expectedVersion.equals(current)) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          Mission.class, missionId);
    }
  }

  private void assertScheduleVersion(
      @NotNull Mission mission, @NotNull Long expectedVersion, @NotNull UUID missionId) {
    long current = mission.getScheduleVersion() == null ? 0L : mission.getScheduleVersion();
    if (!expectedVersion.equals(current)) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          Mission.class, missionId);
    }
  }

  private void assertFlagsVersion(
      @NotNull Mission mission, @NotNull Long expectedVersion, @NotNull UUID missionId) {
    long current = mission.getFlagsVersion() == null ? 0L : mission.getFlagsVersion();
    if (!expectedVersion.equals(current)) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          Mission.class, missionId);
    }
  }

  private void assertPartyLeadVersion(
      @NotNull Mission mission, @NotNull Long expectedVersion, @NotNull UUID missionId) {
    long current = mission.getPartyLeadVersion() == null ? 0L : mission.getPartyLeadVersion();
    if (!expectedVersion.equals(current)) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          Mission.class, missionId);
    }
  }

  private void bumpCoreVersion(@NotNull Mission mission) {
    long current = mission.getCoreVersion() == null ? 0L : mission.getCoreVersion();
    mission.setCoreVersion(current + 1L);
  }

  private void bumpScheduleVersion(@NotNull Mission mission) {
    long current = mission.getScheduleVersion() == null ? 0L : mission.getScheduleVersion();
    mission.setScheduleVersion(current + 1L);
  }

  private void bumpFlagsVersion(@NotNull Mission mission) {
    long current = mission.getFlagsVersion() == null ? 0L : mission.getFlagsVersion();
    mission.setFlagsVersion(current + 1L);
  }

  private void bumpPartyLeadVersion(@NotNull Mission mission) {
    long current = mission.getPartyLeadVersion() == null ? 0L : mission.getPartyLeadVersion();
    mission.setPartyLeadVersion(current + 1L);
  }

  /**
   * Deletes a mission. The cascade unlinks inventory items and refinery orders (rather than
   * deleting them) so individual member contributions survive the mission delete. Mission
   * participants, finance entries, units, crews and frequencies ARE deleted because they only exist
   * in the context of this mission.
   *
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when the mission is
   *     unknown
   */
  @Transactional
  public void deleteMission(@NotNull UUID missionId) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

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
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when any referenced id
   *     is unknown
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
      throw new de.greluc.krt.iri.basetool.backend.exception.BusinessConflictException(
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
        throw new de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException(
            "error.mission.participant.duplicate.user");
      }
    } else if (finalGuestName != null && !finalGuestName.isBlank()) {
      boolean exists =
          mission.getParticipants().stream()
              .anyMatch(p -> finalGuestName.equalsIgnoreCase(p.getGuestName()));
      if (exists) {
        throw new de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException(
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
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when the participant
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
    return mission;
  }

  /**
   * Updates a participant's per-mission attributes (job type, ship, unit, crew, guest name, payout
   * preference). Per-participant optimistic lock via the participant's own {@code @Version} field —
   * the wider mission's version is NOT bumped, so concurrent participant edits don't collide with
   * each other or with mission-level edits.
   *
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when the participant or
   *     any referenced id is unknown
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
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when the id is unknown
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

    return mission;
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
      throw new de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException(
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
    return missionRepository.save(subMission);
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

    Optional<MissionFrequency> existingOpt =
        mission.getFrequencies().stream()
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
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when the mission or the
   *     referenced user is unknown
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
    assertPartyLeadVersion(mission, expectedPartyLeadVersion, missionId);

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

    bumpPartyLeadVersion(mission);
    return missionRepository.save(mission);
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
    return orgUnitMembershipService.findAllMembershipsForUser(userId).stream()
        .map(m -> m.getId().getOrgUnitId())
        .map(id -> orgUnitRepository.findById(id).orElse(null))
        .filter(java.util.Objects::nonNull)
        .toList();
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

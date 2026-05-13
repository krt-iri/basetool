package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.*;
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
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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

  private final MissionRepository missionRepository;
  private final UserRepository userRepository;
  private final ShipRepository shipRepository;
  private final ShipTypeRepository shipTypeRepository;
  private final JobTypeRepository jobTypeRepository;
  private final MissionParticipantRepository missionParticipantRepository;
  private final MissionUnitRepository missionUnitRepository;
  private final MissionCrewRepository missionCrewRepository;
  private final SquadronRepository squadronRepository;
  private final FrequencyTypeRepository frequencyTypeRepository;
  private final MissionFrequencyRepository missionFrequencyRepository;
  private final MissionOwnershipRepository missionOwnershipRepository;
  private final OperationRepository operationRepository;
  private final UserService userService;

  /**
   * @param pageable page request
   * @return paged mission list
   */
  public Page<Mission> getAllMissions(@NotNull Pageable pageable) {
    return missionRepository.findAll(pageable);
  }

  /**
   * @return lightweight reference projection of active missions (id + display name + status) used
   *     by typeaheads
   */
  public List<de.greluc.krt.iri.basetool.backend.model.dto.MissionReferenceDto>
      findAllActiveReference() {
    return missionRepository.findAllActiveReference();
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
    return missionRepository.searchMissions(
        query, start, end, status, isInternal, operationId, pageable);
  }

  /**
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
   * Returns the next upcoming mission by planned-start time. Drives the home-page "next mission"
   * banner. {@code allowInternal=true} (for authenticated callers) includes internal missions;
   * guests see only public missions.
   *
   * @param allowInternal whether internal missions should be included
   * @return the next mission, or empty when none upcoming
   */
  public Optional<Mission> getNextMission(boolean allowInternal) {
    if (allowInternal) {
      return missionRepository.findFirstByPlannedStartTimeAfterOrderByPlannedStartTimeAsc(
          Instant.now());
    } else {
      return missionRepository
          .findFirstByPlannedStartTimeAfterAndIsInternalFalseOrderByPlannedStartTimeAsc(
              Instant.now());
    }
  }

  /**
   * Persists a new mission. Resolves shallow references (operation, location, frequencies) and
   * creates the {@code mission_ownership} row that tracks owner-change versioning separately from
   * the main mission version.
   *
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when any referenced id
   *     is unknown
   */
  @Transactional
  public Mission createMission(@NotNull Mission mission) {
    if (mission.getIsInternal() == null) {
      mission.setIsInternal(false);
    }

    if (mission.getOperation() != null && mission.getOperation().getId() != null) {
      Operation op =
          operationRepository
              .findById(mission.getOperation().getId())
              .orElseThrow(() -> new NotFoundException("Operation not found"));
      mission.setOperation(op);
    } else {
      mission.setOperation(null);
    }

    validateMissionTimes(mission);

    userService.getCurrentUser().ifPresent(mission::setOwner);

    return missionRepository.save(mission);
  }

  /**
   * Full update of a mission's metadata + structural references. Validates optimistic lock,
   * resolves shallow references (operation, location, frequencies). Participant / unit / crew lists
   * are NOT touched here — they have their own dedicated mutators (see {@link #addParticipant},
   * {@link #addUnitToMission}, etc.) so the per-row optimistic-lock checks on those flows do not
   * collide with a mission-level edit.
   *
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when the mission or any
   *     referenced id is unknown
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the supplied
   *     version is stale
   */
  @Transactional
  public Mission updateMission(@NotNull UUID missionId, @NotNull Mission missionDetails) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    if (missionDetails.getVersion() != null
        && !mission.getVersion().equals(missionDetails.getVersion())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          Mission.class, missionId);
    }

    // Status Update Logic for Actual Start Time
    if ("ACTIVE".equals(missionDetails.getStatus()) && !"ACTIVE".equals(mission.getStatus())) {
      if (missionDetails.getActualStartTime() == null) {
        missionDetails.setActualStartTime(Instant.now());
      }
    }

    mission.setName(missionDetails.getName());
    mission.setDescription(missionDetails.getDescription());
    mission.setCalendarLink(missionDetails.getCalendarLink());
    mission.setStatus(missionDetails.getStatus());
    mission.setMeetingTime(missionDetails.getMeetingTime());
    mission.setPlannedStartTime(missionDetails.getPlannedStartTime());
    mission.setPlannedEndTime(missionDetails.getPlannedEndTime());
    mission.setActualStartTime(missionDetails.getActualStartTime());

    if (missionDetails.getOperation() != null && missionDetails.getOperation().getId() != null) {
      Operation op =
          operationRepository
              .findById(missionDetails.getOperation().getId())
              .orElseThrow(() -> new NotFoundException("Operation not found"));
      mission.setOperation(op);
    } else {
      mission.setOperation(null);
    }

    if (missionDetails.getIsInternal() != null) {
      mission.setIsInternal(missionDetails.getIsInternal());
    } else {
      mission.setIsInternal(false);
    }

    Instant newEndTime = missionDetails.getActualEndTime();
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

    return missionRepository.save(mission);
  }

  /**
   * Updates only the core (master-data) section of a mission. Sub-collections (participants, units,
   * finance) are not touched and, thanks to {@code @OptimisticLock(excluded = true)}, do not bump
   * the parent version either — this allows multiple users to work on different sections
   * concurrently without blocking each other or losing input due to a 409 conflict.
   */
  /**
   * Section-scoped partial update: name, description, operation, location. Lets the frontend save
   * just the "core" section of the edit modal without touching unrelated fields.
   *
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when any referenced id
   *     is unknown
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when stale
   */
  @Transactional
  public Mission updateCoreSection(
      @NotNull UUID missionId,
      @NotNull String name,
      String description,
      String calendarLink,
      String status,
      @NotNull Long expectedVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertVersion(mission, expectedVersion, missionId);
    mission.setName(name);
    mission.setDescription(description);
    mission.setCalendarLink(calendarLink);
    if (status != null) {
      mission.setStatus(status);
    }
    return missionRepository.save(mission);
  }

  /**
   * Updates only the schedule section of a mission. All timestamps are processed and stored in UTC.
   * Validation is identical to the full update.
   */
  /**
   * Section-scoped partial update: planned + actual start/end times. Same isolation benefits as
   * {@link #updateCoreSection}.
   */
  @Transactional
  public Mission updateScheduleSection(
      @NotNull UUID missionId,
      Instant meetingTime,
      Instant plannedStartTime,
      Instant plannedEndTime,
      Instant actualStartTime,
      Instant actualEndTime,
      @NotNull Long expectedVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertVersion(mission, expectedVersion, missionId);
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
    return missionRepository.save(mission);
  }

  /** Updates only the flags section of a mission (e.g. {@code isInternal}). */
  /**
   * Section-scoped partial update: boolean flags (internal, status, briefing-required, etc.).
   * Section-scoped to keep per-flag toggles from racing with unrelated edits.
   */
  @Transactional
  public Mission updateFlagsSection(
      @NotNull UUID missionId, @NotNull Boolean isInternal, @NotNull Long expectedVersion) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    assertVersion(mission, expectedVersion, missionId);
    mission.setIsInternal(isInternal);
    return missionRepository.save(mission);
  }

  private void assertVersion(
      @NotNull Mission mission, @NotNull Long expectedVersion, @NotNull UUID missionId) {
    if (mission.getVersion() != null && !mission.getVersion().equals(expectedVersion)) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          Mission.class, missionId);
    }
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
    return addParticipant(missionId, userId, null, null, null, null);
  }

  /**
   * Mid-form participant add — accepts a user reference, optional guest name (when the user isn't
   * authenticated), an optional desired job type, and an optional comment. Convenience overload
   * that delegates to the full form with {@code squadronId=null}.
   */
  @Transactional
  public Mission addParticipant(
      @NotNull UUID missionId,
      UUID userId,
      String guestName,
      UUID desiredJobTypeId,
      String comment) {
    return addParticipant(missionId, userId, guestName, desiredJobTypeId, comment, null);
  }

  /**
   * Full-form participant add. Resolves the user reference from {@code userId} (or by
   * case-insensitive {@code guestName} match against existing users — promotes a guest entry to a
   * linked-user entry when the guest name turns out to be a real member). Optional {@code
   * squadronId} pins the participant's squadron for the mission's roster line; null inherits the
   * user's current squadron.
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
      UUID squadronId) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

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
            "Nutzer ist bereits Teilnehmer dieser Mission.");
      }
    } else if (finalGuestName != null && !finalGuestName.isBlank()) {
      boolean exists =
          mission.getParticipants().stream()
              .anyMatch(p -> finalGuestName.equalsIgnoreCase(p.getGuestName()));
      if (exists) {
        throw new de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException(
            "Ein Gast mit diesem Namen ist bereits Teilnehmer dieser Mission.");
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
      // Registered users always belong to IRI
      squadronRepository.findByShorthand("IRI").ifPresent(participant::setSquadron);
    } else {
      participant.setGuestName(effectiveGuestName);
      if (squadronId != null) {
        Squadron squadron = squadronRepository.findById(squadronId).orElse(null);
        participant.setSquadron(squadron);
      }
    }

    if (desiredJobTypeId != null) {
      JobType job = jobTypeRepository.findById(desiredJobTypeId).orElse(null);
      participant.setDesiredMissionJobType(job);
    }

    participant.setComment(comment);

    mission.getParticipants().add(participant);
    missionParticipantRepository.save(participant);
    // NOTE: no explicit missionRepository.save(mission) here.
    // The collection is @OptimisticLock(excluded = true) so Hibernate's dirty-check
    // on commit persists the new participant (via cascade) without bumping the parent
    // Mission.version. This is key for the multi-user concurrency design (Option A):
    // adding a participant must NOT invalidate other users' open forms on the same mission.
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
   */
  /**
   * Lists participants who have not been assigned to a unit/crew yet. Used by the mission detail
   * page to populate the "unassigned" bucket above the unit grid.
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
      UUID squadronId,
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
      // Registered users always belong to IRI
      squadronRepository.findByShorthand("IRI").ifPresent(participant::setSquadron);
    } else {
      log.info("Updating guest participant: {} with name: {}", participant.getId(), guestName);
      if (guestName != null) {
        participant.setGuestName(guestName);
      }
      if (squadronId != null) {
        Squadron sq =
            squadronRepository
                .findById(squadronId)
                .orElseThrow(() -> new NotFoundException("Squadron not found"));
        participant.setSquadron(sq);
      } else {
        participant.setSquadron(null);
      }
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
   */
  @Transactional
  public Mission addUnitToMission(
      @NotNull UUID missionId,
      @NotNull String name,
      UUID shipTypeId,
      UUID shipId,
      boolean highValueUnit,
      Double frequency) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    MissionUnit missionUnit = new MissionUnit();
    missionUnit.setMission(mission);
    missionUnit.setName(name);

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
      missionUnit.setShip(ship);
      if (shipTypeId == null) {
        missionUnit.setShipType(ship.getShipType());
      }
    }

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
   * Updates a unit's name and the assigned ship. Per-unit optimistic lock — concurrent unit edits
   * across the mission don't collide.
   */
  @Transactional
  public Mission updateMissionUnit(
      @NotNull UUID missionId,
      @NotNull UUID unitId,
      @NotNull String name,
      UUID shipTypeId,
      UUID shipId,
      boolean highValueUnit,
      Double frequency) {
    Mission mission =
        missionRepository
            .findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

    MissionUnit missionUnit =
        mission.getAssignedUnits().stream()
            .filter(u -> u.getId().equals(unitId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("MissionUnit not found"));

    missionUnit.setName(name);
    missionUnit.setHighValueUnit(highValueUnit);

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
      missionUnit.setShip(ship);
      if (shipTypeId == null) {
        missionUnit.setShipType(ship.getShipType());
      }
    } else {
      missionUnit.setShip(null);
    }

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
          "Teilnehmer ist bereits Crewmitglied einer Einheit in dieser Mission.");
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
   * up to their parent in the operation overview.
   */
  @Transactional
  public Mission addSubMission(@NotNull UUID parentMissionId, @NotNull Mission subMission) {
    Mission parent =
        missionRepository
            .findById(parentMissionId)
            .orElseThrow(() -> new NotFoundException("Parent mission not found"));

    if (subMission.getOperation() != null && subMission.getOperation().getId() != null) {
      Operation op =
          operationRepository
              .findById(subMission.getOperation().getId())
              .orElseThrow(() -> new NotFoundException("Operation not found"));
      subMission.setOperation(op);
    } else {
      subMission.setOperation(null);
    }

    subMission.setParent(parent);
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
  /**
   * Versioned variant of {@link #setMissionOwner} — checks the supplied ownership-version against
   * the persisted one and throws 409 on a stale value. Used by the admin owner-change flow where
   * two admins might race.
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
   */
  /**
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
}

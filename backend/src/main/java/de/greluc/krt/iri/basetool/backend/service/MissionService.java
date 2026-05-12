package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.*;
import de.greluc.krt.iri.basetool.backend.repository.JobTypeRepository;
import de.greluc.krt.iri.basetool.backend.repository.FrequencyTypeRepository;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;

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

    public Page<Mission> getAllMissions(@NotNull Pageable pageable) {
        return missionRepository.findAll(pageable);
    }

    public List<de.greluc.krt.iri.basetool.backend.model.dto.MissionReferenceDto> findAllActiveReference() {
        return missionRepository.findAllActiveReference();
    }

    public Page<Mission> searchMissions(String query, Instant start, Instant end, List<String> status, Boolean isInternal, UUID operationId, @NotNull Pageable pageable) {
        if (status == null || status.isEmpty()) {
            status = List.of("PLANNED", "ACTIVE", "COMPLETED", "CANCELLED");
        }
        return missionRepository.searchMissions(query, start, end, status, isInternal, operationId, pageable);
    }

    public Mission getMissionById(@NotNull UUID id) {
        return missionRepository.findById(id).orElseThrow(() -> new NotFoundException("Mission not found"));
    }

    public Optional<Mission> getNextMission(boolean allowInternal) {
        if (allowInternal) {
            return missionRepository.findFirstByPlannedStartTimeAfterOrderByPlannedStartTimeAsc(Instant.now());
        } else {
            return missionRepository.findFirstByPlannedStartTimeAfterAndIsInternalFalseOrderByPlannedStartTimeAsc(Instant.now());
        }
    }

    @Transactional
    public Mission createMission(@NotNull Mission mission) {
        if (mission.getIsInternal() == null) {
            mission.setIsInternal(false);
        }

        if (mission.getOperation() != null && mission.getOperation().getId() != null) {
            Operation op = operationRepository.findById(mission.getOperation().getId())
                    .orElseThrow(() -> new NotFoundException("Operation not found"));
            mission.setOperation(op);
        } else {
            mission.setOperation(null);
        }

        validateMissionTimes(mission);
        
        userService.getCurrentUser().ifPresent(mission::setOwner);
        
        return missionRepository.save(mission);
    }

    @Transactional
    public Mission updateMission(@NotNull UUID missionId, @NotNull Mission missionDetails) {
        Mission mission = missionRepository.findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

        if (missionDetails.getVersion() != null && !mission.getVersion().equals(missionDetails.getVersion())) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(Mission.class, missionId);
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
            Operation op = operationRepository.findById(missionDetails.getOperation().getId())
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
     * Aktualisiert ausschliesslich die Stammdaten-Sektion (Core) eines Einsatzes. Sub-Collections
     * (Teilnehmer, Units, Finanzen) werden nicht beruehrt und inkrementieren dank
     * {@code @OptimisticLock(excluded = true)} die Parent-Version ebenfalls nicht — dadurch
     * koennen mehrere Nutzer gleichzeitig an unterschiedlichen Sektionen arbeiten, ohne sich
     * gegenseitig zu behindern oder Eingaben durch einen 409-Conflict zu verlieren.
     */
    @Transactional
    public Mission updateCoreSection(@NotNull UUID missionId, @NotNull String name, String description,
                                     String calendarLink, String status, @NotNull Long expectedVersion) {
        Mission mission = missionRepository.findById(missionId)
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
     * Aktualisiert ausschliesslich die Zeitplan-Sektion (Schedule) eines Einsatzes. Alle
     * Zeitstempel werden in UTC verarbeitet und gespeichert. Validierung gleich wie beim
     * Gesamt-Update.
     */
    @Transactional
    public Mission updateScheduleSection(@NotNull UUID missionId, Instant meetingTime,
                                         Instant plannedStartTime, Instant plannedEndTime,
                                         Instant actualStartTime, Instant actualEndTime,
                                         @NotNull Long expectedVersion) {
        Mission mission = missionRepository.findById(missionId)
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

    /**
     * Aktualisiert ausschliesslich die Flags-Sektion eines Einsatzes (z.B. {@code isInternal}).
     */
    @Transactional
    public Mission updateFlagsSection(@NotNull UUID missionId, @NotNull Boolean isInternal,
                                      @NotNull Long expectedVersion) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new NotFoundException("Mission not found"));
        assertVersion(mission, expectedVersion, missionId);
        mission.setIsInternal(isInternal);
        return missionRepository.save(mission);
    }

    private void assertVersion(@NotNull Mission mission, @NotNull Long expectedVersion, @NotNull UUID missionId) {
        if (mission.getVersion() != null && !mission.getVersion().equals(expectedVersion)) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(Mission.class, missionId);
        }
    }

    @Transactional
    public void deleteMission(@NotNull UUID missionId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new NotFoundException("Mission not found"));
                
        // Entkoppeln von Inventareinträgen (damit diese nicht gelöscht werden, aber die FK-Verletzung vermieden wird)
        if (mission.getInventoryEntries() != null) {
            mission.getInventoryEntries().forEach(entry -> entry.setMission(null));
            mission.getInventoryEntries().clear();
        }

        // Entkoppeln von Raffinerie-Aufträgen
        if (mission.getRefineryOrders() != null) {
            mission.getRefineryOrders().forEach(order -> order.setMission(null));
            mission.getRefineryOrders().clear();
        }

        // Entkoppeln von Sub-Missionen
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
                throw new IllegalArgumentException("Planned start time cannot be later than planned end time");
            }
        }
    }

    @Transactional
    public Mission addParticipant(@NotNull UUID missionId, @NotNull UUID userId) {
        return addParticipant(missionId, userId, null, null, null, null);
    }

    @Transactional
    public Mission addParticipant(@NotNull UUID missionId, UUID userId, String guestName, UUID desiredJobTypeId, String comment) {
        return addParticipant(missionId, userId, guestName, desiredJobTypeId, comment, null);
    }

    @Transactional
    public Mission addParticipant(@NotNull UUID missionId, UUID userId, String guestName, UUID desiredJobTypeId, String comment, UUID squadronId) {
        Mission mission = missionRepository.findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

        UUID effectiveUserId = userId;
        String effectiveGuestName = guestName;

        if (effectiveUserId == null && effectiveGuestName != null && !effectiveGuestName.isBlank()) {
            Optional<User> matchedUser = userRepository.findByUsernameIgnoreCaseOrDisplayNameIgnoreCase(effectiveGuestName.trim(), effectiveGuestName.trim());
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
            boolean exists = mission.getParticipants().stream()
                    .anyMatch(p -> p.getUser() != null && p.getUser().getId().equals(finalUserId));
            if (exists) {
                throw new de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException("Nutzer ist bereits Teilnehmer dieser Mission.");
            }
        } else if (finalGuestName != null && !finalGuestName.isBlank()) {
            boolean exists = mission.getParticipants().stream()
                    .anyMatch(p -> finalGuestName.equalsIgnoreCase(p.getGuestName()));
            if (exists) {
                throw new de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException("Ein Gast mit diesem Namen ist bereits Teilnehmer dieser Mission.");
            }
        }

        MissionParticipant participant = new MissionParticipant();
        participant.setMission(mission);

        if (effectiveUserId != null) {
            User user = userRepository.findById(effectiveUserId)
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


    public MissionParticipant getParticipant(@NotNull UUID missionId, @NotNull UUID participantId) {
        return missionParticipantRepository.findById(participantId)
                .filter(p -> p.getMission().getId().equals(missionId))
                .orElseThrow(() -> new NotFoundException("Participant not found in mission"));
    }

    /**
     * Returns all participants of a mission that are not yet assigned to any unit (crew).
     * Used to filter the "Crew zuweisen" dropdown so only unassigned participants are selectable.
     */
    public List<MissionParticipant> getUnassignedParticipants(@NotNull UUID missionId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new NotFoundException("Mission not found"));
        Set<UUID> assignedParticipantIds = mission.getAssignedUnits().stream()
                .flatMap(unit -> unit.getCrew().stream())
                .map(crew -> crew.getParticipant().getId())
                .collect(java.util.stream.Collectors.toSet());
        return mission.getParticipants().stream()
                .filter(p -> !assignedParticipantIds.contains(p.getId()))
                .toList();
    }

    @Transactional
    public Mission removeParticipant(@NotNull UUID missionId, @NotNull UUID participantId) {
        Mission mission = missionRepository.findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

        boolean removed = mission.getParticipants().removeIf(p -> p.getId().equals(participantId));

        if (!removed) {
            throw new NotFoundException("Participant not found in this mission");
        }
        
        // Also remove from any crews in this mission
        for (MissionUnit ship : mission.getAssignedUnits()) {
            ship.getCrew().removeIf(crew ->
                crew.getParticipant() != null && crew.getParticipant().getId().equals(participantId));
        }

        // NOTE: no explicit missionRepository.save(mission). orphanRemoval + @OptimisticLock(excluded)
        // on participants/assignedUnits ensures dirty-flush on commit without bumping Mission.version.
        return mission;
    }

    @Transactional
    public Mission updateParticipantAttributes(@NotNull UUID missionId, @NotNull UUID participantId, UUID desiredMissionJobTypeId,
                                               UUID plannedMissionJobTypeId, String comment,
                                               Instant startTime, Instant endTime,
                                               UUID squadronId, PayoutPreference payoutPreference, String guestName, Long version) {
        Mission mission = missionRepository.findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

        MissionParticipant participant = mission.getParticipants().stream()
            .filter(p -> p.getId().equals(participantId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Participant not found in this mission"));

        if (version != null && !version.equals(participant.getVersion())) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(MissionParticipant.class, participant.getId());
        }

        if (payoutPreference != null) {
            participant.setPayoutPreference(payoutPreference);
            missionParticipantRepository.save(participant);
            missionParticipantRepository.flush();
        }

        if (desiredMissionJobTypeId != null) {
            JobType jt = jobTypeRepository.findById(desiredMissionJobTypeId)
                .orElseThrow(() -> new NotFoundException("Desired JobType not found"));
            if (jt.getArchetype() != JobTypeArchetype.MISSION) {
                throw new IllegalArgumentException("Desired JobType " + jt.getName() + " is not of archetype MISSION");
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
                 Squadron sq = squadronRepository.findById(squadronId)
                     .orElseThrow(() -> new NotFoundException("Squadron not found"));
                 participant.setSquadron(sq);
            } else {
                 participant.setSquadron(null);
            }
        }

        if (plannedMissionJobTypeId != null) {
            JobType jt = jobTypeRepository.findById(plannedMissionJobTypeId)
                .orElseThrow(() -> new NotFoundException("Planned JobType not found"));
            if (jt.getArchetype() != JobTypeArchetype.MISSION) {
                throw new IllegalArgumentException("Planned JobType " + jt.getName() + " is not of archetype MISSION");
            }
            participant.setPlannedMissionJobType(jt);
        } else {
            participant.setPlannedMissionJobType(null);
        }

        participant.setComment(comment);

        if (startTime != null) {
            if (mission.getActualStartTime() == null) {
                throw new IllegalArgumentException("Cannot set participant start time before mission actual start time is set");
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

    @Transactional
    public Mission checkIn(UUID missionId, UUID participantId) {
        Mission mission = missionRepository.findById(missionId).orElseThrow(() -> new NotFoundException("Mission not found"));
        MissionParticipant participant = getParticipant(missionId, participantId);
        if (mission.getActualStartTime() == null) {
            throw new IllegalArgumentException("Cannot check in before mission actual start time is set");
        }
        participant.setStartTime(Instant.now());
        missionParticipantRepository.save(participant);
        return mission;
    }

    @Transactional
    public Mission checkOut(UUID missionId, UUID participantId) {
        Mission mission = missionRepository.findById(missionId).orElseThrow(() -> new NotFoundException("Mission not found"));
        MissionParticipant participant = getParticipant(missionId, participantId);
        if (mission.getActualEndTime() != null && Instant.now().isAfter(mission.getActualEndTime())) {
            if (participant.getStartTime() != null && mission.getActualEndTime().isBefore(participant.getStartTime())) {
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

    @Transactional
    public Mission updatePayoutPreference(UUID missionId, UUID participantId, PayoutPreference preference) {
        Mission mission = getMissionById(missionId);
        MissionParticipant participant = mission.getParticipants().stream()
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

    @Transactional
    public Mission addUnitToMission(@NotNull UUID missionId, @NotNull String name, UUID shipTypeId, UUID shipId, boolean highValueUnit, Double frequency) {
        Mission mission = missionRepository.findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

        MissionUnit missionUnit = new MissionUnit();
        missionUnit.setMission(mission);
        missionUnit.setName(name);

        if (shipTypeId != null) {
            ShipType shipType = shipTypeRepository.findById(shipTypeId)
                .orElseThrow(() -> new NotFoundException("ShipType not found"));
            missionUnit.setShipType(shipType);
        } else {
            missionUnit.setShipType(null);
        }

        if (shipId != null) {
            Ship ship = shipRepository.findById(shipId)
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
            double roundedFrequency = BigDecimal.valueOf(frequency)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

            if (roundedFrequency < 100.00 || roundedFrequency > 999.99) {
                throw new IllegalArgumentException("Frequency must be between 100.00 and 999.99");
            }
            missionUnit.setFrequency(roundedFrequency);
        }

        mission.getAssignedUnits().add(missionUnit);
        missionUnitRepository.save(missionUnit);
        return mission;
    }

    @Transactional
    public Mission updateMissionUnit(@NotNull UUID missionId, @NotNull UUID unitId, @NotNull String name, UUID shipTypeId, UUID shipId, boolean highValueUnit, Double frequency) {
        Mission mission = missionRepository.findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

        MissionUnit missionUnit = mission.getAssignedUnits().stream()
            .filter(u -> u.getId().equals(unitId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("MissionUnit not found"));

        missionUnit.setName(name);
        missionUnit.setHighValueUnit(highValueUnit);

        if (shipTypeId != null) {
            ShipType shipType = shipTypeRepository.findById(shipTypeId)
                .orElseThrow(() -> new NotFoundException("ShipType not found"));
            missionUnit.setShipType(shipType);
        } else {
            missionUnit.setShipType(null);
        }

        if (shipId != null) {
            Ship ship = shipRepository.findById(shipId)
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
            double roundedFrequency = BigDecimal.valueOf(frequency)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

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

    @Transactional
    public Mission removeMissionUnit(@NotNull UUID missionId, @NotNull UUID unitId) {
        Mission mission = missionRepository.findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

        boolean removed = mission.getAssignedUnits().removeIf(u -> u.getId().equals(unitId));

        if (!removed) {
            throw new NotFoundException("MissionUnit not found in this mission");
        }

        return mission;
    }

    @Transactional
    public Mission addCrewToShip(@NotNull UUID missionId, @NotNull UUID missionUnitId, @NotNull UUID participantId, @NotNull Set<UUID> jobTypeIds) {
        Mission mission = missionRepository.findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

        MissionUnit missionShip = mission.getAssignedUnits().stream()
            .filter(ms -> ms != null && ms.getId() != null && ms.getId().equals(missionUnitId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("MissionUnit not found in this mission"));

        MissionParticipant participant = mission.getParticipants().stream()
            .filter(p -> p.getId().equals(participantId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Participant not found in this mission"));

        boolean isAlreadyAssigned = mission.getAssignedUnits().stream()
            .flatMap(u -> u.getCrew().stream())
            .anyMatch(c -> c.getParticipant().getId().equals(participantId));

        if (isAlreadyAssigned) {
            throw new de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException("Teilnehmer ist bereits Crewmitglied einer Einheit in dieser Mission.");
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

    @Transactional
    public Mission updateCrewInShip(@NotNull UUID missionId, @NotNull UUID missionUnitId, @NotNull UUID crewId, @NotNull Set<UUID> jobTypeIds) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new NotFoundException("Mission not found"));

        MissionUnit missionUnit = mission.getAssignedUnits().stream()
                .filter(u -> u.getId().equals(missionUnitId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("MissionUnit not found"));

        MissionCrew crew = missionUnit.getCrew().stream()
                .filter(c -> c.getId().equals(crewId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Crew member not found in this unit"));

        Set<JobType> jobTypes = validateAndFetchJobTypes(jobTypeIds);
        crew.setJobTypes(jobTypes);

        missionCrewRepository.save(crew);
        return mission;
    }

    @Transactional
    public Mission removeCrewFromShip(@NotNull UUID missionId, @NotNull UUID missionUnitId, @NotNull UUID crewId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new NotFoundException("Mission not found"));

        MissionUnit missionUnit = mission.getAssignedUnits().stream()
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
                JobType jt = jobTypeRepository.findById(jtId)
                        .orElseThrow(() -> new NotFoundException("JobType not found: " + jtId));

                if (jt.getArchetype() != JobTypeArchetype.CREW) {
                    throw new IllegalArgumentException("JobType " + jt.getName() + " is not of archetype CREW");
                }
                jobTypes.add(jt);
            }
        }
        return jobTypes;
    }
    @Transactional
    public Mission addSubMission(@NotNull UUID parentMissionId, @NotNull Mission subMission) {
        Mission parent = missionRepository.findById(parentMissionId)
            .orElseThrow(() -> new NotFoundException("Parent mission not found"));

        if (subMission.getOperation() != null && subMission.getOperation().getId() != null) {
            Operation op = operationRepository.findById(subMission.getOperation().getId())
                    .orElseThrow(() -> new NotFoundException("Operation not found"));
            subMission.setOperation(op);
        } else {
            subMission.setOperation(null);
        }

        subMission.setParent(parent);
        return missionRepository.save(subMission);
    }

    @Transactional
    public Mission addOrUpdateMissionFrequency(@NotNull UUID missionId, @NotNull UUID frequencyTypeId, @NotNull BigDecimal value) {
        Mission mission = missionRepository.findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

        FrequencyType frequencyType = frequencyTypeRepository.findById(frequencyTypeId)
            .orElseThrow(() -> new NotFoundException("FrequencyType not found"));

        Optional<MissionFrequency> existingOpt = mission.getFrequencies().stream()
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

    @Transactional
    public Mission removeMissionFrequency(@NotNull UUID missionId, @NotNull UUID frequencyId) {
        Mission mission = missionRepository.findById(missionId)
            .orElseThrow(() -> new NotFoundException("Mission not found"));

        boolean removed = mission.getFrequencies().removeIf(f -> f.getId() != null && f.getId().equals(frequencyId));
        if (!removed) {
            throw new NotFoundException("Frequency not found in this mission");
        }

        return mission;
    }

    @Transactional
    public Mission setMissionOwner(@NotNull UUID missionId, @NotNull UUID userId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new NotFoundException("Mission not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
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
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new NotFoundException("Mission not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        mission.setOwner(user);
        upsertMissionOwnership(mission, user, expectedOwnershipVersion);
        return mission;
    }

    /** Returns the current optimistic-lock version of the mission ownership aggregate (0 if absent). */
    public long getMissionOwnershipVersion(@NotNull UUID missionId) {
        return missionOwnershipRepository.findByMissionId(missionId)
                .map(mo -> mo.getVersion() == null ? 0L : mo.getVersion())
                .orElse(0L);
    }

    private void upsertMissionOwnership(Mission mission, User newOwner, Long expectedVersion) {
        MissionOwnership ownership = missionOwnershipRepository.findByMissionId(mission.getId())
                .orElseGet(() -> {
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

    @Transactional
    public Mission addManager(@NotNull UUID missionId, @NotNull UUID userId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new NotFoundException("Mission not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        mission.getManagers().add(user);
        return mission;
    }

    @Transactional
    public Mission removeManager(@NotNull UUID missionId, @NotNull UUID userId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new NotFoundException("Mission not found"));
        mission.getManagers().removeIf(u -> u.getId().equals(userId));
        return mission;
    }
}

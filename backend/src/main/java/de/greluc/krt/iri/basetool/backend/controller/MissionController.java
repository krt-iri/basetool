package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.MissionMapper;
import de.greluc.krt.iri.basetool.backend.model.MissionFinanceEntry;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.*;
import de.greluc.krt.iri.basetool.backend.service.MissionSecurityService;
import de.greluc.krt.iri.basetool.backend.service.MissionService;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
@Tag(name = "Missions", description = "Mission management endpoints")
@Transactional
@Slf4j
public class MissionController {

    private final MissionService missionService;
    private final UserService userService;
    private final MissionMapper missionMapper;
    private final MissionSecurityService missionSecurityService;

    @GetMapping
    @Operation(summary = "List all missions (paginated)")
    @Transactional(readOnly = true)
    public PageResponse<MissionListDto> getAllMissions(@RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size,
                                                   @RequestParam(required = false) String sort,
                                                   Principal principal) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("plannedStartTime", "name", "status", "id"), "plannedStartTime");
        Page<MissionListDto> pageResult;
        if (principal == null) {
            pageResult = missionService.searchMissions(null, null, null, List.of("PLANNED", "ACTIVE"), false, null, pageable)
                    .map(missionMapper::toListDto);
        } else {
            pageResult = missionService.getAllMissions(pageable).map(missionMapper::toListDto);
        }
        return toPageResponse(pageResult);
    }

    @GetMapping("/lookup")
    @Operation(summary = "Lookup active missions", description = "Returns a reference list of active missions.")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<de.greluc.krt.iri.basetool.backend.model.dto.MissionReferenceDto> lookupMissions() {
        return missionService.findAllActiveReference();
    }

    @GetMapping("/search")
    @Operation(summary = "Search missions (paginated)")
    @Transactional(readOnly = true)
    public PageResponse<MissionListDto> searchMissions(@RequestParam(required = false) String query,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
                                                    @RequestParam(required = false) List<String> status,
                                                    @RequestParam(required = false) UUID operationId,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size,
                                                    @RequestParam(required = false) String sort,
                                                    Principal principal) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("plannedStartTime", "name", "status", "id"), "plannedStartTime");
        if (principal == null) {
            List<String> allowed = List.of("PLANNED", "ACTIVE");
            if (status == null || status.isEmpty()) {
                status = allowed;
            } else {
                status = status.stream().filter(allowed::contains).toList();
                if (status.isEmpty()) {
                    return new PageResponse<>(Collections.emptyList(), 0, pageable.getPageSize(), 0, 0, List.of());
                }
            }
            Page<MissionListDto> pageResult = missionService.searchMissions(query, start, end, status, false, operationId, pageable)
                    .map(missionMapper::toListDto);
            return toPageResponse(pageResult);
        }
        Page<MissionListDto> pageResult = missionService.searchMissions(query, start, end, status, null, operationId, pageable)
                .map(missionMapper::toListDto);
        return toPageResponse(pageResult);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get mission by ID")
    @Transactional(readOnly = true)
    public MissionDto getMissionById(@PathVariable @NotNull UUID id, Principal principal) {
        var mission = missionService.getMissionById(id);
        if (principal == null) {
            if (mission.getIsInternal() != null && mission.getIsInternal()) {
                throw new AccessDeniedException("Guests cannot view internal missions.");
            }
            if ("COMPLETED".equals(mission.getStatus()) || "CANCELLED".equals(mission.getStatus())) {
                throw new AccessDeniedException("Guests cannot view past missions.");
            }
        }
        var dto = missionMapper.toDto(mission);
        if (principal == null) {
            dto = cleanupMissionForGuest(dto);
        }
        return dto;
    }

    @GetMapping("/next")
    @Operation(summary = "Get next upcoming mission")
    @Transactional(readOnly = true)
    public ResponseEntity<MissionDto> getNextMission(Principal principal) {
        boolean allowInternal = principal != null;
        return missionService.getNextMission(allowInternal)
                .map(m -> {
                    var dto = missionMapper.toDto(m);
                    if (principal == null) {
                        dto = cleanupMissionForGuest(dto);
                    }
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.noContent().build());
    }

    private MissionDto cleanupMissionForGuest(MissionDto dto) {
        Set<MissionParticipantDto> cleanedParticipants = dto.participants() == null ? null :
                dto.participants().stream()
                        .map(this::cleanupParticipantForGuest)
                        .collect(Collectors.toSet());

        Set<MissionDto> cleanedSubMissions = dto.subMissions() == null ? null :
                dto.subMissions().stream()
                        .map(this::cleanupMissionForGuest)
                        .collect(Collectors.toSet());

        return new MissionDto(
                dto.id(),
                dto.name(),
                dto.description(),
                dto.calendarLink(),
                dto.status(),
                dto.meetingTime(),
                dto.plannedStartTime(),
                dto.actualStartTime(),
                dto.plannedEndTime(),
                dto.actualEndTime(),
                dto.isInternal(),
                cleanedParticipants,
                dto.assignedUnits(),
                dto.frequencies(),
                cleanedSubMissions,
                Collections.emptyList(), // inventoryEntries
                Collections.emptyList(), // refineryOrders
                dto.operation(),
                null, // owner
                null, // managers
                false, // canEdit
                false, // canManageManagers
                dto.version()
        );
    }

    private MissionParticipantDto cleanupParticipantForGuest(MissionParticipantDto dto) {
        UserDto cleanedUser = dto.user() != null ? cleanupUserForGuest(dto.user()) : null;
        return new MissionParticipantDto(
                dto.id(),
                cleanedUser,
                dto.guestName(),
                dto.squadron(),
                dto.desiredMissionJobType(),
                dto.plannedMissionJobType(),
                dto.comment(),
                dto.startTime(),
                dto.endTime(),
                dto.payoutPreference(),
                dto.version()
        );
    }

    private UserDto cleanupUserForGuest(UserDto dto) {
        return new UserDto(
                dto.id(),
                dto.username(),
                dto.displayName(),
                dto.effectiveName(),
                null, // firstName
                null, // lastName
                null, // email
                dto.rank(),
                null, // description
                null, // roles
                null, // permissions
                null, // lastReadAnnouncementId
                false, // isLogistician
                false, // isMissionManager
                dto.inKeycloak(),
                dto.version()
        );
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a new mission")
    public MissionDto createMission(@RequestBody @jakarta.validation.Valid de.greluc.krt.iri.basetool.backend.model.dto.MissionDto mission) {
        return missionMapper.toDto(missionService.createMission(missionMapper.toEntity(mission)));
    }

    @PostMapping("/{id}/sub-missions")
    @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
    @Operation(summary = "Create a sub-mission")
    public MissionDto createSubMission(@PathVariable @NotNull UUID id, @RequestBody @jakarta.validation.Valid de.greluc.krt.iri.basetool.backend.model.dto.MissionDto subMission) {
        return missionMapper.toDto(missionService.addSubMission(id, missionMapper.toEntity(subMission)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
    @Operation(summary = "Update a mission")
    public MissionDto updateMission(@PathVariable @NotNull UUID id, @RequestBody @jakarta.validation.Valid de.greluc.krt.iri.basetool.backend.model.dto.MissionDto mission) {
        return missionMapper.toDto(missionService.updateMission(id, missionMapper.toEntity(mission)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a mission")
    public ResponseEntity<Void> deleteMission(@PathVariable @NotNull UUID id) {
        missionService.deleteMission(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/join")
    @Operation(summary = "Join a mission")
    public MissionDto joinMission(@AuthenticationPrincipal Jwt jwt, @PathVariable @NotNull UUID id) {
        return missionMapper.toDto(missionService.addParticipant(id, userService.getUserIdFromJwt(jwt)));
    }

    @PostMapping("/{id}/units")
    @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
    @Operation(summary = "Add a unit to a mission")
    public MissionDto addUnit(@PathVariable @NotNull UUID id, @jakarta.validation.Valid @RequestBody @NotNull AddUnitRequest request) {
        return missionMapper.toDto(missionService.addUnitToMission(id, request.name(), request.shipTypeId(), request.shipId(), request.isHighValueUnit(), request.frequency()));
    }

    @PutMapping("/{id}/units/{unitId}")
    @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
    @Operation(summary = "Update a mission unit")
    public MissionDto updateUnit(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID unitId, @jakarta.validation.Valid @RequestBody @NotNull AddUnitRequest request) {
        return missionMapper.toDto(missionService.updateMissionUnit(id, unitId, request.name(), request.shipTypeId(), request.shipId(), request.isHighValueUnit(), request.frequency()));
    }

    @DeleteMapping("/{id}/units/{unitId}")
    @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
    @Operation(summary = "Delete a mission unit")
    public MissionDto deleteUnit(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID unitId) {
        return missionMapper.toDto(missionService.removeMissionUnit(id, unitId));
    }

    @PostMapping("/{id}/units/{missionUnitId}/crew")
    @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
    @Operation(summary = "Add crew to a mission unit")
    public MissionDto addCrew(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID missionUnitId, @RequestBody @jakarta.validation.Valid @NotNull AddCrewRequest request) {
        java.util.Set<UUID> jobTypeIds = request.jobTypeIds() != null ? request.jobTypeIds() : java.util.Collections.emptySet();
        return missionMapper.toDto(missionService.addCrewToShip(id, missionUnitId, request.participantId(), jobTypeIds));
    }

    @PutMapping("/{id}/units/{missionUnitId}/crew/{crewId}")
    @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
    @Operation(summary = "Update crew in a mission unit")
    public MissionDto updateCrew(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID missionUnitId, @PathVariable @NotNull UUID crewId, @RequestBody @jakarta.validation.Valid @NotNull UpdateCrewRequest request) {
        java.util.Set<UUID> jobTypeIds = request.jobTypeIds() != null ? request.jobTypeIds() : java.util.Collections.emptySet();
        return missionMapper.toDto(missionService.updateCrewInShip(id, missionUnitId, crewId, jobTypeIds));
    }

    @DeleteMapping("/{id}/units/{missionUnitId}/crew/{crewId}")
    @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
    @Operation(summary = "Remove crew from a mission unit")
    public MissionDto removeCrew(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID missionUnitId, @PathVariable @NotNull UUID crewId) {
        return missionMapper.toDto(missionService.removeCrewFromShip(id, missionUnitId, crewId));
    }

    @PutMapping("/{id}/participants/{participantId}")
    @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
    @Operation(summary = "Update a participant")
    public MissionDto updateParticipant(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID participantId, @RequestBody @jakarta.validation.Valid @NotNull UpdateParticipantRequest request, Authentication authentication) {
        return missionMapper.toDto(missionService.updateParticipantAttributes(id, participantId,
                request.desiredMissionJobTypeId(),
                request.plannedMissionJobTypeId(),
                request.comment(),
                request.startTime(),
                request.endTime(),
                request.squadronId(),
                request.payoutPreference(),
                request.guestName(),
                request.version()));
    }

    @PostMapping("/{id}/participants/{participantId}/check-in")
    @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
    @Operation(summary = "Check in a participant")
    public MissionDto checkInParticipant(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID participantId, Authentication authentication) {
        return missionMapper.toDto(missionService.checkIn(id, participantId));
    }

    @PostMapping("/{id}/participants/{participantId}/check-out")
    @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
    @Operation(summary = "Check out a participant")
    public MissionDto checkOutParticipant(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID participantId, Authentication authentication) {
        return missionMapper.toDto(missionService.checkOut(id, participantId));
    }

    @PutMapping("/{id}/participants/{participantId}/payout-preference")
    @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
    @Operation(summary = "Update payout preference for a participant")
    public MissionDto updatePayoutPreference(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID participantId, @RequestBody @jakarta.validation.Valid @NotNull UpdatePayoutPreferenceRequest request, Authentication authentication) {
        return missionMapper.toDto(missionService.updatePayoutPreference(id, participantId, request.preference()));
    }

    @PostMapping("/{id}/participants")
    @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
    @Operation(summary = "Add a participant (admin)")
    public MissionDto addParticipant(@PathVariable @NotNull UUID id, @RequestBody @jakarta.validation.Valid @NotNull AddParticipantRequest request) {
        return missionMapper.toDto(missionService.addParticipant(id, request.userId()));
    }

    @DeleteMapping("/{id}/participants/{participantId}")
    @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
    @Operation(summary = "Remove a participant")
    public MissionDto removeParticipant(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID participantId, Authentication authentication) {
        return missionMapper.toDto(missionService.removeParticipant(id, participantId));
    }


    @PostMapping("/{id}/participants/add")
    @Operation(summary = "Add a participant (public)")
    public MissionDto addParticipantPublic(@PathVariable @NotNull UUID id, @RequestBody @jakarta.validation.Valid @NotNull AddParticipantPublicRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID finalUserId = request.userId();

        if (jwt != null && finalUserId == null && (request.guestName() == null || request.guestName().isBlank())) {
            finalUserId = userService.getUserIdFromJwt(jwt);
        }

        if (jwt == null && finalUserId != null) {
            throw new AccessDeniedException("Anonymous users cannot add registered users.");
        }

        if (finalUserId == null && request.guestName() != null && !request.guestName().isBlank() && userService.isUsernameOrDisplayNameTaken(request.guestName())) {
             throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Guest name is already taken.");
        }

        return missionMapper.toDto(missionService.addParticipant(id, finalUserId, request.guestName(), request.desiredJobTypeId(), request.comment(), request.squadronId()));
    }

    @PostMapping("/{id}/frequencies")
    @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
    @Operation(summary = "Add or update a frequency for a mission")
    public MissionDto addOrUpdateFrequency(@PathVariable @NotNull UUID id, @RequestBody @jakarta.validation.Valid de.greluc.krt.iri.basetool.backend.model.dto.request.AddFrequencyRequest request) {
        return missionMapper.toDto(missionService.addOrUpdateMissionFrequency(id, request.frequencyTypeId(), request.value()));
    }

    @DeleteMapping("/{id}/frequencies/{frequencyId}")
    @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
    @Operation(summary = "Remove a frequency from a mission")
    public MissionDto removeFrequency(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID frequencyId) {
        return missionMapper.toDto(missionService.removeMissionFrequency(id, frequencyId));
    }

    @PostMapping("/{id}/managers/{userId}")
    @PreAuthorize("@missionSecurityService.canManageManagers(#id, authentication)")
    @Operation(summary = "Add a manager to a mission")
    public MissionDto addManager(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID userId) {
        log.info("[DEBUG_LOG] MissionController.addManager START - id: {}, userId: {}", id, userId);
        try {
            var mission = missionService.addManager(id, userId);
            log.info("[DEBUG_LOG] MissionController.addManager SUCCESS - id: {}, userId: {}", id, userId);
            return missionMapper.toDto(mission);
        } catch (Exception e) {
            log.error("[DEBUG_LOG] MissionController.addManager ERROR - id: {}, userId: {}, error: {}", id, userId, e.getMessage(), e);
            throw e;
        }
    }

    @DeleteMapping("/{id}/managers/{userId}")
    @PreAuthorize("@missionSecurityService.canManageManagers(#id, authentication)")
    @Operation(summary = "Remove a manager from a mission")
    public MissionDto removeManager(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID userId) {
        log.info("Request to remove manager {} from mission {}", userId, id);
        try {
            var mission = missionService.removeManager(id, userId);
            log.info("Manager {} removed from mission {} successfully", userId, id);
            return missionMapper.toDto(mission);
        } catch (Exception e) {
            log.error("Failed to remove manager {} from mission {}: {}", userId, id, e.getMessage(), e);
            throw e;
        }
    }

    @PutMapping("/{id}/owner/{userId}")
    @PreAuthorize("@missionSecurityService.canManageManagers(#id, authentication)")
    @Operation(summary = "Change the owner of a mission")
    public MissionDto setMissionOwner(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID userId) {
        log.info("[DEBUG_LOG] MissionController.setMissionOwner START - id: {}, userId: {}", id, userId);
        try {
            var mission = missionService.setMissionOwner(id, userId);
            log.info("[DEBUG_LOG] MissionController.setMissionOwner SUCCESS - id: {}, userId: {}", id, userId);
            return missionMapper.toDto(mission);
        } catch (Exception e) {
            log.error("[DEBUG_LOG] MissionController.setMissionOwner ERROR - id: {}, userId: {}, error: {}", id, userId, e.getMessage(), e);
            throw e;
        }
    }

    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getSort().stream().map(o -> o.getProperty() + "," + o.getDirection()).toList()
        );
    }
}

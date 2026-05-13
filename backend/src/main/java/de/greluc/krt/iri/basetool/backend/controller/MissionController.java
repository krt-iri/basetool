package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.BusinessConflictException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.MissionMapper;
import de.greluc.krt.iri.basetool.backend.mapper.UserMapper;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.AddCrewRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.AddParticipantPublicRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.AddParticipantRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.AddUnitRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionCrewDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionFrequencyDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionListDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionParticipantDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionUnitDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.UpdateCrewRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.UpdateParticipantRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.UpdatePayoutPreferenceRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UserReferenceDto;
import de.greluc.krt.iri.basetool.backend.service.MissionSecurityService;
import de.greluc.krt.iri.basetool.backend.service.MissionService;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
  private final UserMapper userMapper;
  private final MissionSecurityService missionSecurityService;

  /** Sunset date for legacy sub-section endpoints that still return the full MissionDto. */
  private static final String SLIM_DEPRECATION_SUNSET = "2026-10-20";

  @GetMapping
  @Operation(summary = "List all missions (paginated)")
  @Transactional(readOnly = true)
  public PageResponse<MissionListDto> getAllMissions(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort,
      Principal principal) {
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page,
            size,
            sort,
            Set.of("plannedStartTime", "name", "status", "id"),
            "plannedStartTime");
    Page<MissionListDto> pageResult;
    if (principal == null) {
      pageResult =
          missionService
              .searchMissions(null, null, null, List.of("PLANNED", "ACTIVE"), false, null, pageable)
              .map(missionMapper::toListDto);
    } else {
      pageResult = missionService.getAllMissions(pageable).map(missionMapper::toListDto);
    }
    return toPageResponse(pageResult);
  }

  @GetMapping("/lookup")
  @Operation(
      summary = "Lookup active missions",
      description = "Returns a reference list of active missions.")
  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public List<de.greluc.krt.iri.basetool.backend.model.dto.MissionReferenceDto> lookupMissions() {
    return missionService.findAllActiveReference();
  }

  @GetMapping("/search")
  @Operation(summary = "Search missions (paginated)")
  @Transactional(readOnly = true)
  public PageResponse<MissionListDto> searchMissions(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant end,
      @RequestParam(required = false) List<String> status,
      @RequestParam(required = false) UUID operationId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort,
      Principal principal) {
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page,
            size,
            sort,
            Set.of("plannedStartTime", "name", "status", "id"),
            "plannedStartTime");
    if (principal == null) {
      List<String> allowed = List.of("PLANNED", "ACTIVE");
      if (status == null || status.isEmpty()) {
        status = allowed;
      } else {
        status = status.stream().filter(allowed::contains).toList();
        if (status.isEmpty()) {
          return new PageResponse<>(
              Collections.emptyList(), 0, pageable.getPageSize(), 0, 0, List.of());
        }
      }
      Page<MissionListDto> pageResult =
          missionService
              .searchMissions(query, start, end, status, false, operationId, pageable)
              .map(missionMapper::toListDto);
      return toPageResponse(pageResult);
    }
    Page<MissionListDto> pageResult =
        missionService
            .searchMissions(query, start, end, status, null, operationId, pageable)
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
    return missionService
        .getNextMission(allowInternal)
        .map(
            m -> {
              var dto = missionMapper.toDto(m);
              if (principal == null) {
                dto = cleanupMissionForGuest(dto);
              }
              return ResponseEntity.ok(dto);
            })
        .orElse(ResponseEntity.noContent().build());
  }

  private MissionDto cleanupMissionForGuest(MissionDto dto) {
    Set<MissionParticipantDto> cleanedParticipants =
        dto.participants() == null
            ? null
            : dto.participants().stream()
                .map(this::cleanupParticipantForGuest)
                .collect(Collectors.toSet());

    Set<MissionDto> cleanedSubMissions =
        dto.subMissions() == null
            ? null
            : dto.subMissions().stream()
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
        dto.version(),
        dto.checkedInParticipants(),
        dto.registeredParticipants());
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
        dto.version());
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
        dto.version(),
        null // joinDate – not exposed to guests
        );
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Create a new mission")
  public MissionDto createMission(
      @RequestBody @jakarta.validation.Valid de.greluc.krt.iri.basetool.backend.model.dto.MissionDto mission) {
    return missionMapper.toDto(missionService.createMission(missionMapper.toEntity(mission)));
  }

  @PostMapping("/{id}/sub-missions")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(summary = "Create a sub-mission")
  public MissionDto createSubMission(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid de.greluc.krt.iri.basetool.backend.model.dto.MissionDto subMission) {
    return missionMapper.toDto(
        missionService.addSubMission(id, missionMapper.toEntity(subMission)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(
      summary = "Update a mission (full replace)",
      description =
          "Aktualisiert den gesamten Einsatz in einem Request. Fuer eine bessere "
              + "Multi-User-Experience wird empfohlen, stattdessen die Section-PATCH-Endpoints "
              + "(/core, /schedule, /flags) zu verwenden, damit parallele Aenderungen an "
              + "anderen Sektionen keine Optimistic-Lock-Konflikte ausloesen.")
  public MissionDto updateMission(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid de.greluc.krt.iri.basetool.backend.model.dto.MissionDto mission) {
    return missionMapper.toDto(missionService.updateMission(id, missionMapper.toEntity(mission)));
  }

  @PatchMapping("/{id}/core")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(
      summary = "Patch mission core section",
      description =
          "Aktualisiert nur die Stammdaten-Sektion (Name, Beschreibung, Kalenderlink, "
              + "Status) eines Einsatzes. Andere Sektionen und Sub-Aggregate bleiben unberuehrt. "
              + "Bei einem Versionskonflikt wird HTTP 409 (application/problem+json) zurueckgegeben.")
  public MissionDto patchMissionCore(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid @NotNull de.greluc.krt.iri.basetool.backend.model.dto.request.PatchMissionCoreRequest request) {
    return missionMapper.toDto(
        missionService.updateCoreSection(
            id,
            request.name(),
            request.description(),
            request.calendarLink(),
            request.status(),
            request.version()));
  }

  @PatchMapping("/{id}/schedule")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(
      summary = "Patch mission schedule section",
      description =
          "Aktualisiert nur die Zeitplan-Sektion (Meeting-/Planned-/Actual-Zeiten) eines "
              + "Einsatzes. Parallele Aenderungen an Teilnehmern, Units oder Finanzen fuehren dank "
              + "entkoppelter Sub-Collections nicht mehr zum Versionskonflikt. Zeitstempel in UTC.")
  public MissionDto patchMissionSchedule(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid @NotNull de.greluc.krt.iri.basetool.backend.model.dto.request.PatchMissionScheduleRequest
              request) {
    return missionMapper.toDto(
        missionService.updateScheduleSection(
            id,
            request.meetingTime(),
            request.plannedStartTime(),
            request.plannedEndTime(),
            request.actualStartTime(),
            request.actualEndTime(),
            request.version()));
  }

  @PatchMapping("/{id}/flags")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(
      summary = "Patch mission flags section",
      description = "Aktualisiert nur die Flags-Sektion (z.B. isInternal) eines Einsatzes.")
  public MissionDto patchMissionFlags(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid @NotNull de.greluc.krt.iri.basetool.backend.model.dto.request.PatchMissionFlagsRequest request) {
    return missionMapper.toDto(
        missionService.updateFlagsSection(id, request.isInternal(), request.version()));
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
  @PreAuthorize("isAuthenticated()")
  public MissionDto joinMission(@AuthenticationPrincipal Jwt jwt, @PathVariable @NotNull UUID id) {
    return missionMapper.toDto(
        missionService.addParticipant(id, userService.getUserIdFromJwt(jwt)));
  }

  @PostMapping("/{id}/units")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Deprecated(forRemoval = true)
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/units/slim")
  @Operation(
      summary = "Add a unit to a mission (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer POST /api/v1/missions/{id}/units/slim which returns only the updated list of units and avoids parent-coupled payloads (Option A / multi-user concurrency).",
      deprecated = true)
  public MissionDto addUnit(
      @PathVariable @NotNull UUID id,
      @jakarta.validation.Valid @RequestBody @NotNull AddUnitRequest request) {
    return missionMapper.toDto(
        missionService.addUnitToMission(
            id,
            request.name(),
            request.shipTypeId(),
            request.shipId(),
            request.isHighValueUnit(),
            request.frequency()));
  }

  @PutMapping("/{id}/units/{unitId}")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Deprecated(forRemoval = true)
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/units/{unitId}/slim")
  @Operation(
      summary = "Update a mission unit (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer PUT /api/v1/missions/{id}/units/{unitId}/slim which returns only the updated unit.",
      deprecated = true)
  public MissionDto updateUnit(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID unitId,
      @jakarta.validation.Valid @RequestBody @NotNull AddUnitRequest request) {
    return missionMapper.toDto(
        missionService.updateMissionUnit(
            id,
            unitId,
            request.name(),
            request.shipTypeId(),
            request.shipId(),
            request.isHighValueUnit(),
            request.frequency()));
  }

  @DeleteMapping("/{id}/units/{unitId}")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Deprecated(forRemoval = true)
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/units/{unitId}/slim")
  @Operation(
      summary = "Delete a mission unit (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer DELETE /api/v1/missions/{id}/units/{unitId}/slim which returns 204 No Content.",
      deprecated = true)
  public MissionDto deleteUnit(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID unitId) {
    return missionMapper.toDto(missionService.removeMissionUnit(id, unitId));
  }

  @PostMapping("/{id}/units/{missionUnitId}/crew")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Deprecated(forRemoval = true)
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/units/{missionUnitId}/crew/slim")
  @Operation(
      summary = "Add crew to a mission unit (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer POST /api/v1/missions/{id}/units/{missionUnitId}/crew/slim which returns only the crew list of the affected unit.",
      deprecated = true)
  public MissionDto addCrew(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID missionUnitId,
      @RequestBody @jakarta.validation.Valid @NotNull AddCrewRequest request) {
    java.util.Set<UUID> jobTypeIds =
        request.jobTypeIds() != null ? request.jobTypeIds() : java.util.Collections.emptySet();
    return missionMapper.toDto(
        missionService.addCrewToShip(id, missionUnitId, request.participantId(), jobTypeIds));
  }

  @PutMapping("/{id}/units/{missionUnitId}/crew/{crewId}")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Deprecated(forRemoval = true)
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/units/{missionUnitId}/crew/{crewId}/slim")
  @Operation(
      summary = "Update crew in a mission unit (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer PUT /api/v1/missions/{id}/units/{missionUnitId}/crew/{crewId}/slim which returns only the updated crew entry.",
      deprecated = true)
  public MissionDto updateCrew(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID missionUnitId,
      @PathVariable @NotNull UUID crewId,
      @RequestBody @jakarta.validation.Valid @NotNull UpdateCrewRequest request) {
    java.util.Set<UUID> jobTypeIds =
        request.jobTypeIds() != null ? request.jobTypeIds() : java.util.Collections.emptySet();
    return missionMapper.toDto(
        missionService.updateCrewInShip(id, missionUnitId, crewId, jobTypeIds));
  }

  @DeleteMapping("/{id}/units/{missionUnitId}/crew/{crewId}")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Deprecated(forRemoval = true)
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/units/{missionUnitId}/crew/{crewId}/slim")
  @Operation(
      summary = "Remove crew from a mission unit (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer DELETE /api/v1/missions/{id}/units/{missionUnitId}/crew/{crewId}/slim which returns 204 No Content.",
      deprecated = true)
  public MissionDto removeCrew(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID missionUnitId,
      @PathVariable @NotNull UUID crewId) {
    return missionMapper.toDto(missionService.removeCrewFromShip(id, missionUnitId, crewId));
  }

  @PutMapping("/{id}/participants/{participantId}")
  @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
  @Deprecated(forRemoval = true)
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/participants/{participantId}/slim")
  @Operation(
      summary = "Update a participant (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer PUT /api/v1/missions/{id}/participants/{participantId}/slim which returns only the updated participant.",
      deprecated = true)
  public MissionDto updateParticipant(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @RequestBody @jakarta.validation.Valid @NotNull UpdateParticipantRequest request,
      Authentication authentication) {
    return missionMapper.toDto(
        missionService.updateParticipantAttributes(
            id,
            participantId,
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
  @Deprecated(forRemoval = true)
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/participants/{participantId}/check-in/slim")
  @Operation(
      summary = "Check in a participant (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer POST /api/v1/missions/{id}/participants/{participantId}/check-in/slim which returns only the updated participant.",
      deprecated = true)
  public MissionDto checkInParticipant(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      Authentication authentication) {
    return missionMapper.toDto(missionService.checkIn(id, participantId));
  }

  @PostMapping("/{id}/participants/{participantId}/check-out")
  @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
  @Deprecated(forRemoval = true)
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/participants/{participantId}/check-out/slim")
  @Operation(
      summary = "Check out a participant (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer POST /api/v1/missions/{id}/participants/{participantId}/check-out/slim which returns only the updated participant.",
      deprecated = true)
  public MissionDto checkOutParticipant(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      Authentication authentication) {
    return missionMapper.toDto(missionService.checkOut(id, participantId));
  }

  @PutMapping("/{id}/participants/{participantId}/payout-preference")
  @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
  @Deprecated(forRemoval = true)
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/participants/{participantId}/payout-preference/slim")
  @Operation(
      summary = "Update payout preference for a participant (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer PUT /api/v1/missions/{id}/participants/{participantId}/payout-preference/slim which returns only the updated participant.",
      deprecated = true)
  public MissionDto updatePayoutPreference(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @RequestBody @jakarta.validation.Valid @NotNull UpdatePayoutPreferenceRequest request,
      Authentication authentication) {
    return missionMapper.toDto(
        missionService.updatePayoutPreference(id, participantId, request.preference()));
  }

  @PostMapping("/{id}/participants")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Deprecated(forRemoval = true)
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/participants/slim")
  @Operation(
      summary = "Add a participant (admin, legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer POST /api/v1/missions/{id}/participants/slim which returns only the updated participant list.",
      deprecated = true)
  public MissionDto addParticipant(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid @NotNull AddParticipantRequest request) {
    return missionMapper.toDto(missionService.addParticipant(id, request.userId()));
  }

  @DeleteMapping("/{id}/participants/{participantId}")
  @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
  @Deprecated(forRemoval = true)
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/participants/{participantId}/slim")
  @Operation(
      summary = "Remove a participant (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer DELETE /api/v1/missions/{id}/participants/{participantId}/slim which returns 204 No Content.",
      deprecated = true)
  public MissionDto removeParticipant(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      Authentication authentication) {
    return missionMapper.toDto(missionService.removeParticipant(id, participantId));
  }

  @PostMapping("/{id}/participants/add")
  @Operation(
      summary = "Add a participant (public)",
      description =
          "Adds a participant by explicit userId (from autocomplete) or by free-text guestName. "
              + "Free-text names are resolved case-insensitively against existing users: a unique match links the participant as a registered member; "
              + "no match falls back to the guest path; multiple matches return 409 (ambiguous name).")
  @io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Participant added"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description =
            "Validation error or guest name reserved for a registered user (anonymous only)"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Anonymous users cannot add registered users"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "Participant name is ambiguous and matches more than one registered user")
  })
  @PreAuthorize("permitAll()")
  public MissionDto addParticipantPublic(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid @NotNull AddParticipantPublicRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    UUID finalUserId = request.userId();
    String finalGuestName = request.guestName();

    if (jwt != null
        && finalUserId == null
        && (finalGuestName == null || finalGuestName.isBlank())) {
      finalUserId = userService.getUserIdFromJwt(jwt);
    }

    if (jwt == null && finalUserId != null) {
      throw new AccessDeniedException("Anonymous users cannot add registered users.");
    }

    // Resolve free-text participant name to an existing registered user (case-insensitive,
    // exact match on username or displayName). This fixes the bug where an authenticated
    // squadron member typing their own name without using the autocomplete dropdown was
    // rejected with "Guest name is already taken." – now the name is transparently linked
    // to the matching user. Anonymous users may still not spoof a registered member's name.
    if (finalUserId == null && finalGuestName != null && !finalGuestName.isBlank()) {
      List<User> matches = userService.findMatchesByExactName(finalGuestName);
      if (matches.size() > 1) {
        log.info(
            "[DEBUG_LOG] Participant name '{}' is ambiguous ({} matches) for mission {}",
            finalGuestName,
            matches.size(),
            id);
        throw new BusinessConflictException("Participant name is ambiguous.");
      }
      if (matches.size() == 1) {
        if (jwt != null) {
          finalUserId = matches.get(0).getId();
          finalGuestName = null;
          log.debug(
              "[DEBUG_LOG] Resolved free-text participant name '{}' to userId {} for mission {}",
              request.guestName(),
              finalUserId,
              id);
        } else {
          // Anonymous user tried to add a name that belongs to a registered member -> keep spoofing
          // protection.
          throw new BadRequestException("Guest name is already taken.");
        }
      }
    }

    return missionMapper.toDto(
        missionService.addParticipant(
            id,
            finalUserId,
            finalGuestName,
            request.desiredJobTypeId(),
            request.comment(),
            request.squadronId()));
  }

  @PostMapping("/{id}/frequencies")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Deprecated(forRemoval = true)
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/frequencies/slim")
  @Operation(
      summary = "Add or update a frequency for a mission (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer POST /api/v1/missions/{id}/frequencies/slim which returns only the updated frequency list.",
      deprecated = true)
  public MissionDto addOrUpdateFrequency(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid de.greluc.krt.iri.basetool.backend.model.dto.request.AddFrequencyRequest request) {
    return missionMapper.toDto(
        missionService.addOrUpdateMissionFrequency(id, request.frequencyTypeId(), request.value()));
  }

  @DeleteMapping("/{id}/frequencies/{frequencyId}")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Deprecated(forRemoval = true)
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/frequencies/{frequencyId}/slim")
  @Operation(
      summary = "Remove a frequency from a mission (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer DELETE /api/v1/missions/{id}/frequencies/{frequencyId}/slim which returns 204 No Content.",
      deprecated = true)
  public MissionDto removeFrequency(
      @PathVariable @NotNull UUID id, @PathVariable @NotNull UUID frequencyId) {
    return missionMapper.toDto(missionService.removeMissionFrequency(id, frequencyId));
  }

  @PostMapping("/{id}/managers/{userId}")
  @PreAuthorize("@missionSecurityService.canManageManagers(#id, authentication)")
  @Deprecated(forRemoval = true)
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/managers/{userId}/slim")
  @Operation(
      summary = "Add a manager to a mission (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer POST /api/v1/missions/{id}/managers/{userId}/slim which returns only the updated manager list.",
      deprecated = true)
  public MissionDto addManager(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID userId) {
    log.info("[DEBUG_LOG] MissionController.addManager START - id: {}, userId: {}", id, userId);
    try {
      var mission = missionService.addManager(id, userId);
      log.info("[DEBUG_LOG] MissionController.addManager SUCCESS - id: {}, userId: {}", id, userId);
      return missionMapper.toDto(mission);
    } catch (Exception e) {
      log.error(
          "[DEBUG_LOG] MissionController.addManager ERROR - id: {}, userId: {}, error: {}",
          id,
          userId,
          e.getMessage(),
          e);
      throw e;
    }
  }

  @DeleteMapping("/{id}/managers/{userId}")
  @PreAuthorize("@missionSecurityService.canManageManagers(#id, authentication)")
  @Deprecated(forRemoval = true)
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/managers/{userId}/slim")
  @Operation(
      summary = "Remove a manager from a mission (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer DELETE /api/v1/missions/{id}/managers/{userId}/slim which returns 204 No Content.",
      deprecated = true)
  public MissionDto removeManager(
      @PathVariable @NotNull UUID id, @PathVariable @NotNull UUID userId) {
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
  @PreAuthorize("@missionSecurityService.canChangeOwner(#id, authentication)")
  @Deprecated(forRemoval = true)
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = "2026-10-20",
      replacement = "/api/v1/missions/{id}/owner")
  @Operation(
      summary = "Change the owner of a mission (legacy, deprecated)",
      description =
          "Legacy endpoint without optimistic lock on the ownership aggregate. "
              + "Prefer PUT /api/v1/missions/{id}/owner with UpdateMissionOwnerRequest (includes version) "
              + "to benefit from per-section optimistic locking that does not invalidate other users' "
              + "open forms on the same mission.",
      deprecated = true)
  public MissionDto setMissionOwnerLegacy(
      @PathVariable @NotNull UUID id, @PathVariable @NotNull UUID userId) {
    log.info(
        "[DEBUG_LOG] MissionController.setMissionOwnerLegacy START - id: {}, userId: {}",
        id,
        userId);
    try {
      var mission = missionService.setMissionOwner(id, userId);
      log.info(
          "[DEBUG_LOG] MissionController.setMissionOwnerLegacy SUCCESS - id: {}, userId: {}",
          id,
          userId);
      return missionMapper.toDto(mission);
    } catch (Exception e) {
      log.error(
          "[DEBUG_LOG] MissionController.setMissionOwnerLegacy ERROR - id: {}, userId: {}, error: {}",
          id,
          userId,
          e.getMessage(),
          e);
      throw e;
    }
  }

  @PutMapping("/{id}/owner")
  @PreAuthorize("@missionSecurityService.canChangeOwner(#id, authentication)")
  @Operation(
      summary = "Change the owner of a mission (version-checked)",
      description =
          "Updates the mission owner through the dedicated MissionOwnership aggregate. "
              + "The version field in the request body must match the current ownership version "
              + "(NOT the parent Mission.version) to prevent lost updates on concurrent owner "
              + "changes. Changing the owner does NOT bump Mission.version, so other users' "
              + "open forms on the same mission remain valid (Option A / multi-user concurrency).")
  @io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Owner updated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Mission or user not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "Ownership version conflict (application/problem+json)")
  })
  public MissionDto updateMissionOwner(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid @NotNull de.greluc.krt.iri.basetool.backend.model.dto.request.UpdateMissionOwnerRequest request) {
    var mission = missionService.updateMissionOwner(id, request.userId(), request.version());
    return missionMapper.toDto(mission);
  }

  // =====================================================================================
  // Slim sub-resource endpoints (Option A / multi-user concurrency).
  //
  // These endpoints are additive replacements for the legacy MissionDto-returning
  // sub-endpoints above. They return only the affected slim sub-DTO (or a slim list,
  // or 204 No Content) instead of the full MissionDto. This lets the frontend run
  // per-sub-aggregate DOM `data-version` synchronisation without coupling the
  // Mission parent version into every AJAX round-trip.
  //
  // Behaviour and service-level concurrency semantics are IDENTICAL to the legacy
  // endpoints; only the response shape is slim. See ApiDeprecation annotations on
  // the legacy endpoints for the sunset date.
  // =====================================================================================

  private de.greluc.krt.iri.basetool.backend.model.MissionUnit findUnit(
      de.greluc.krt.iri.basetool.backend.model.Mission mission, UUID unitId) {
    return mission.getAssignedUnits().stream()
        .filter(u -> unitId.equals(u.getId()))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Mission unit not found"));
  }

  private de.greluc.krt.iri.basetool.backend.model.MissionParticipant findParticipant(
      de.greluc.krt.iri.basetool.backend.model.Mission mission, UUID participantId) {
    return mission.getParticipants().stream()
        .filter(p -> participantId.equals(p.getId()))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Participant not found"));
  }

  private de.greluc.krt.iri.basetool.backend.model.MissionCrew findCrew(
      de.greluc.krt.iri.basetool.backend.model.MissionUnit unit, UUID crewId) {
    return unit.getCrew().stream()
        .filter(c -> crewId.equals(c.getId()))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Crew member not found"));
  }

  // --- Units ---

  @PostMapping("/{id}/units/slim")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(
      summary = "Add a unit to a mission (slim response)",
      description =
          "Adds a new unit and returns the updated list of units as slim DTOs. "
              + "Preferred replacement for POST /api/v1/missions/{id}/units to support "
              + "multi-user concurrency on the mission detail page.")
  public List<MissionUnitDto> addUnitSlim(
      @PathVariable @NotNull UUID id,
      @jakarta.validation.Valid @RequestBody @NotNull AddUnitRequest request) {
    var mission =
        missionService.addUnitToMission(
            id,
            request.name(),
            request.shipTypeId(),
            request.shipId(),
            request.isHighValueUnit(),
            request.frequency());
    return mission.getAssignedUnits().stream().map(missionMapper::toDto).toList();
  }

  @PutMapping("/{id}/units/{unitId}/slim")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(
      summary = "Update a mission unit (slim response)",
      description = "Updates a unit and returns only the updated unit as a slim DTO.")
  public MissionUnitDto updateUnitSlim(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID unitId,
      @jakarta.validation.Valid @RequestBody @NotNull AddUnitRequest request) {
    var mission =
        missionService.updateMissionUnit(
            id,
            unitId,
            request.name(),
            request.shipTypeId(),
            request.shipId(),
            request.isHighValueUnit(),
            request.frequency());
    return missionMapper.toDto(findUnit(mission, unitId));
  }

  @DeleteMapping("/{id}/units/{unitId}/slim")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(
      summary = "Delete a mission unit (slim response)",
      description = "Deletes a unit and returns 204 No Content.")
  public ResponseEntity<Void> deleteUnitSlim(
      @PathVariable @NotNull UUID id, @PathVariable @NotNull UUID unitId) {
    missionService.removeMissionUnit(id, unitId);
    return ResponseEntity.noContent().build();
  }

  // --- Crew ---

  @PostMapping("/{id}/units/{missionUnitId}/crew/slim")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(
      summary = "Add crew to a mission unit (slim response)",
      description =
          "Adds a crew member and returns the updated crew list of the affected unit as slim DTOs.")
  public List<MissionCrewDto> addCrewSlim(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID missionUnitId,
      @RequestBody @jakarta.validation.Valid @NotNull AddCrewRequest request) {
    java.util.Set<UUID> jobTypeIds =
        request.jobTypeIds() != null ? request.jobTypeIds() : java.util.Collections.emptySet();
    var mission =
        missionService.addCrewToShip(id, missionUnitId, request.participantId(), jobTypeIds);
    return missionMapper.toDto(findUnit(mission, missionUnitId)).crew();
  }

  @PutMapping("/{id}/units/{missionUnitId}/crew/{crewId}/slim")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(
      summary = "Update crew in a mission unit (slim response)",
      description = "Updates a crew member and returns only the updated crew entry as a slim DTO.")
  public MissionCrewDto updateCrewSlim(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID missionUnitId,
      @PathVariable @NotNull UUID crewId,
      @RequestBody @jakarta.validation.Valid @NotNull UpdateCrewRequest request) {
    java.util.Set<UUID> jobTypeIds =
        request.jobTypeIds() != null ? request.jobTypeIds() : java.util.Collections.emptySet();
    var mission = missionService.updateCrewInShip(id, missionUnitId, crewId, jobTypeIds);
    return missionMapper.toDto(findCrew(findUnit(mission, missionUnitId), crewId));
  }

  @DeleteMapping("/{id}/units/{missionUnitId}/crew/{crewId}/slim")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(
      summary = "Remove crew from a mission unit (slim response)",
      description = "Removes a crew member and returns 204 No Content.")
  public ResponseEntity<Void> removeCrewSlim(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID missionUnitId,
      @PathVariable @NotNull UUID crewId) {
    missionService.removeCrewFromShip(id, missionUnitId, crewId);
    return ResponseEntity.noContent().build();
  }

  // --- Participants ---

  @PutMapping("/{id}/participants/{participantId}/slim")
  @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
  @Operation(
      summary = "Update a participant (slim response)",
      description = "Updates a participant and returns only the updated participant as a slim DTO.")
  public MissionParticipantDto updateParticipantSlim(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @RequestBody @jakarta.validation.Valid @NotNull UpdateParticipantRequest request,
      Authentication authentication) {
    var mission =
        missionService.updateParticipantAttributes(
            id,
            participantId,
            request.desiredMissionJobTypeId(),
            request.plannedMissionJobTypeId(),
            request.comment(),
            request.startTime(),
            request.endTime(),
            request.squadronId(),
            request.payoutPreference(),
            request.guestName(),
            request.version());
    return missionMapper.toDto(findParticipant(mission, participantId));
  }

  @PostMapping("/{id}/participants/{participantId}/check-in/slim")
  @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
  @Operation(
      summary = "Check in a participant (slim response)",
      description =
          "Checks in a participant and returns only the updated participant as a slim DTO.")
  public MissionParticipantDto checkInParticipantSlim(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      Authentication authentication) {
    var mission = missionService.checkIn(id, participantId);
    return missionMapper.toDto(findParticipant(mission, participantId));
  }

  @PostMapping("/{id}/participants/{participantId}/check-out/slim")
  @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
  @Operation(
      summary = "Check out a participant (slim response)",
      description =
          "Checks out a participant and returns only the updated participant as a slim DTO.")
  public MissionParticipantDto checkOutParticipantSlim(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      Authentication authentication) {
    var mission = missionService.checkOut(id, participantId);
    return missionMapper.toDto(findParticipant(mission, participantId));
  }

  @PutMapping("/{id}/participants/{participantId}/payout-preference/slim")
  @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
  @Operation(
      summary = "Update payout preference for a participant (slim response)",
      description =
          "Updates the payout preference and returns only the updated participant as a slim DTO.")
  public MissionParticipantDto updatePayoutPreferenceSlim(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @RequestBody @jakarta.validation.Valid @NotNull UpdatePayoutPreferenceRequest request,
      Authentication authentication) {
    var mission = missionService.updatePayoutPreference(id, participantId, request.preference());
    return missionMapper.toDto(findParticipant(mission, participantId));
  }

  @PostMapping("/{id}/participants/slim")
  @Operation(
      summary = "Add a participant (slim response)",
      description =
          "Adds a participant and returns the updated participant list as slim DTOs. "
              + "Mirrors the public add-participant logic: explicit userId (autocomplete) or free-text guestName "
              + "(case-insensitive resolution against registered users). Authenticated users may always add "
              + "themselves; adding other registered users is restricted to managers/officers/admins. "
              + "Anonymous users may only add guest entries.")
  @PreAuthorize("permitAll()")
  public List<MissionParticipantDto> addParticipantSlim(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid @NotNull AddParticipantPublicRequest request,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    UUID finalUserId = request.userId();
    String finalGuestName = request.guestName();

    // Default self-enroll when an authenticated caller submits an empty form.
    if (jwt != null
        && finalUserId == null
        && (finalGuestName == null || finalGuestName.isBlank())) {
      finalUserId = userService.getUserIdFromJwt(jwt);
    }

    // Anonymous callers must never add a registered user directly.
    if (jwt == null && finalUserId != null) {
      throw new AccessDeniedException("Anonymous users cannot add registered users.");
    }

    // Resolve free-text guest names against registered users (case-insensitive, exact match on
    // username or displayName). Authenticated users get their name transparently linked;
    // anonymous users trying to impersonate a registered member are rejected.
    if (finalUserId == null && finalGuestName != null && !finalGuestName.isBlank()) {
      List<User> matches = userService.findMatchesByExactName(finalGuestName);
      if (matches.size() > 1) {
        throw new BusinessConflictException("Participant name is ambiguous.");
      }
      if (matches.size() == 1) {
        if (jwt != null) {
          finalUserId = matches.get(0).getId();
          finalGuestName = null;
        } else {
          throw new BadRequestException("Guest name is already taken.");
        }
      }
    }

    // If an authenticated, non-privileged caller tries to add a *different* registered user,
    // require manage-mission privileges. Self-add always stays permitted.
    if (jwt != null && finalUserId != null) {
      UUID callerId = userService.getUserIdFromJwt(jwt);
      if ((callerId == null || !finalUserId.equals(callerId))
          && !missionSecurityService.canManageMission(id, authentication)) {
        throw new AccessDeniedException(
            "Only mission managers may add other users as participants.");
      }
    }

    var mission =
        missionService.addParticipant(
            id,
            finalUserId,
            finalGuestName,
            request.desiredJobTypeId(),
            request.comment(),
            request.squadronId());
    return mission.getParticipants().stream().map(missionMapper::toDto).toList();
  }

  @DeleteMapping("/{id}/participants/{participantId}/slim")
  @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
  @Operation(
      summary = "Remove a participant (slim response)",
      description = "Removes a participant and returns 204 No Content.")
  public ResponseEntity<Void> removeParticipantSlim(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      Authentication authentication) {
    missionService.removeParticipant(id, participantId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/participants/unassigned")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(
      summary = "Get unassigned participants",
      description =
          "Returns all participants of a mission that are not yet assigned to any unit crew.")
  public List<MissionParticipantDto> getUnassignedParticipants(@PathVariable @NotNull UUID id) {
    return missionService.getUnassignedParticipants(id).stream().map(missionMapper::toDto).toList();
  }

  // --- Frequencies ---

  @PostMapping("/{id}/frequencies/slim")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(
      summary = "Add or update a frequency for a mission (slim response)",
      description =
          "Adds or updates a frequency and returns the updated frequency list as slim DTOs.")
  public List<MissionFrequencyDto> addOrUpdateFrequencySlim(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid de.greluc.krt.iri.basetool.backend.model.dto.request.AddFrequencyRequest request) {
    var mission =
        missionService.addOrUpdateMissionFrequency(id, request.frequencyTypeId(), request.value());
    return mission.getFrequencies().stream().map(missionMapper::toDto).toList();
  }

  @DeleteMapping("/{id}/frequencies/{frequencyId}/slim")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(
      summary = "Remove a frequency from a mission (slim response)",
      description = "Removes a frequency and returns 204 No Content.")
  public ResponseEntity<Void> removeFrequencySlim(
      @PathVariable @NotNull UUID id, @PathVariable @NotNull UUID frequencyId) {
    missionService.removeMissionFrequency(id, frequencyId);
    return ResponseEntity.noContent().build();
  }

  // --- Managers ---

  @PostMapping("/{id}/managers/{userId}/slim")
  @PreAuthorize("@missionSecurityService.canManageManagers(#id, authentication)")
  @Operation(
      summary = "Add a manager to a mission (slim response)",
      description = "Adds a manager and returns the updated manager list as UserReferenceDto.")
  public List<UserReferenceDto> addManagerSlim(
      @PathVariable @NotNull UUID id, @PathVariable @NotNull UUID userId) {
    var mission = missionService.addManager(id, userId);
    return mission.getManagers().stream().map(userMapper::toReferenceDto).toList();
  }

  @DeleteMapping("/{id}/managers/{userId}/slim")
  @PreAuthorize("@missionSecurityService.canManageManagers(#id, authentication)")
  @Operation(
      summary = "Remove a manager from a mission (slim response)",
      description = "Removes a manager and returns 204 No Content.")
  public ResponseEntity<Void> removeManagerSlim(
      @PathVariable @NotNull UUID id, @PathVariable @NotNull UUID userId) {
    missionService.removeManager(id, userId);
    return ResponseEntity.noContent().build();
  }

  private <T> PageResponse<T> toPageResponse(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getSort().stream().map(o -> o.getProperty() + "," + o.getDirection()).toList());
  }
}

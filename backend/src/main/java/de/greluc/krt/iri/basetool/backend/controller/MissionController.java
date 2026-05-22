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
import de.greluc.krt.iri.basetool.backend.service.AuthHelperService;
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

/**
 * REST surface over the mission aggregate — the squadron's planning and execution view. The surface
 * is intentionally large because missions have many sub-aggregates (units, crew, participants,
 * frequencies, managers, ownership) and Option A / multi-user concurrency required a second family
 * of "slim" endpoints alongside the legacy MissionDto-returning ones.
 *
 * <p>Two endpoint families live side-by-side:
 *
 * <ul>
 *   <li><b>Section patches</b> ({@code /core}, {@code /schedule}, {@code /flags}) split the mission
 *       header into independently versioned sections so two managers editing different sections do
 *       not collide on {@code Mission.version}.
 *   <li><b>Slim sub-resource endpoints</b> ({@code .../slim}) return only the affected sub-DTO
 *       instead of the full {@link MissionDto}. Behaviour is identical to the legacy
 *       MissionDto-returning sibling; only the response shape differs. Legacy endpoints carry
 *       {@code @Deprecated(forRemoval=true)} with sunset {@value #SLIM_DEPRECATION_SUNSET}.
 * </ul>
 *
 * <p>Guest reads are heavily redacted: internal and past missions are hidden, and {@link
 * #cleanupMissionForGuest} strips names, emails, internal inventory and refinery orders before the
 * DTO leaves the controller. {@code addParticipantPublic} additionally resolves free-text guest
 * names against registered users to prevent impersonation.
 *
 * <p>Authorisation is delegated to {@link MissionSecurityService} via SpEL ({@code
 * canManageMission}, {@code canAccessParticipant}, {@code canManageManagers}, {@code
 * canChangeOwner}). Owner changes use the dedicated {@code MissionOwnership} aggregate with its own
 * version, so they do not invalidate other users' open mission forms.
 */
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
  private final AuthHelperService authHelperService;

  /** Sunset date for legacy sub-section endpoints that still return the full MissionDto. */
  private static final String SLIM_DEPRECATION_SUNSET = "2026-10-20";

  /**
   * Paged mission list. Anonymous callers are silently restricted to {@code PLANNED}+{@code ACTIVE}
   * non-internal missions; authenticated callers see everything.
   *
   * @param principal Spring Security principal (null for guests)
   * @return paged mission list DTOs
   */
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
      // Authenticated callers MUST go through searchMissions so the squadron scope (own
      // squadron OR is_internal=false cross-staffel public) is applied — getAllMissions
      // would call missionRepository.findAll() unfiltered and leak internal missions of
      // other squadrons to every authenticated user (MULTI_SQUADRON_PLAN.md section 1).
      pageResult =
          missionService
              .searchMissions(
                  null,
                  null,
                  null,
                  List.of("PLANNED", "ACTIVE", "COMPLETED", "CANCELLED"),
                  null,
                  null,
                  pageable)
              .map(missionMapper::toListDto);
    }
    return toPageResponse(pageResult);
  }

  /**
   * Lightweight projection (id + label) of active missions for typeaheads.
   *
   * @return active missions as reference DTOs
   */
  @GetMapping("/lookup")
  @Operation(
      summary = "Lookup active missions",
      description = "Returns a reference list of active missions.")
  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public List<de.greluc.krt.iri.basetool.backend.model.dto.MissionReferenceDto> lookupMissions() {
    return missionService.findAllActiveReference();
  }

  /**
   * Filtered + paged mission search. Anonymous callers are restricted to {@code PLANNED}+{@code
   * ACTIVE} non-internal missions; an unsupported status filter from a guest returns an empty page
   * (rather than 403) so the UI degrades silently.
   *
   * @param query free-text name fragment
   * @param start lower bound on planned start time
   * @param end upper bound on planned start time
   * @param status status filter (one or more)
   * @param operationId optional operation filter
   * @param principal Spring Security principal (null for guests)
   * @return paged mission list DTOs
   */
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

  /**
   * Single-mission read. Guests are blocked from internal and past missions (403) and get a
   * redacted DTO via {@link #cleanupMissionForGuest}.
   *
   * @param id mission id
   * @param principal Spring Security principal (null for guests)
   * @return the mission DTO
   */
  @GetMapping("/{id}")
  @Operation(summary = "Get mission by ID")
  @PreAuthorize("@ownerScopeService.canSeeMission(#id)")
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

  /**
   * Returns the next upcoming mission (or 204 when none). Internal missions are included only for
   * authenticated callers; guests get the same redaction pass as {@link #getMissionById}.
   *
   * @param principal Spring Security principal (null for guests)
   * @return mission DTO or 204 No Content
   */
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

  /**
   * Redacts a mission DTO for an anonymous viewer: strips owner/managers, internal inventory and
   * refinery orders, clears edit/manage flags, and recursively cleans each participant and
   * sub-mission. This is the only path that controls what leaves the API for guests — never lift
   * data into the controller layer without thinking about this method first.
   *
   * @param dto the full mission DTO
   * @return a redacted copy safe for unauthenticated callers
   */
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
        dto.coreVersion(),
        dto.scheduleVersion(),
        dto.flagsVersion(),
        dto.checkedInParticipants(),
        dto.registeredParticipants(),
        // Squadron shorthand is not sensitive (MULTI_SQUADRON_PLAN.md section 7) — forward
        // through to guests so the public detail view shows the owning-squadron badge.
        dto.owningSquadron());
  }

  /**
   * Redacts a participant DTO for guests: cleans the nested user via {@link #cleanupUserForGuest},
   * keeps the displayed fields (squadron, job-type, comment, payout preference, times) intact
   * because those are public per the squadron policy.
   *
   * @param dto the participant DTO
   * @return a redacted copy safe for unauthenticated callers
   */
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

  /**
   * Redacts a user DTO for guests: drops first/last name, email, description, roles, permissions,
   * announcement watermark and join date. Username + displayName + rank remain visible because
   * those are the public callsign tuple.
   *
   * @param dto the user DTO
   * @return a redacted copy safe for unauthenticated callers
   */
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
        null, // squadron – not exposed to guests
        dto.version(),
        null // joinDate – not exposed to guests
        );
  }

  /**
   * Creates a new mission. The caller becomes the owner via {@link MissionService#createMission}.
   * The {@link de.greluc.krt.iri.basetool.backend.model.dto.request.CreateMissionRequest} record
   * structurally excludes {@code id} / {@code version} / {@code owningSquadron} / {@code parent} /
   * {@code owner} / collections (audit finding C-3) — those are stamped server-side.
   *
   * @param request create payload
   * @return the persisted DTO
   */
  @PostMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Create a new mission")
  public MissionDto createMission(
      @RequestBody @jakarta.validation.Valid @NotNull
          de.greluc.krt.iri.basetool.backend.model.dto.request.CreateMissionRequest request) {
    return missionMapper.toDto(missionService.createMission(request));
  }

  /**
   * Attaches a new sub-mission to a parent. Sub-missions are independent missions that aggregate up
   * to the parent for finance/payout roll-ups. Uses the same {@link
   * de.greluc.krt.iri.basetool.backend.model.dto.request.CreateMissionRequest} as the top-level
   * create — {@code parent} and {@code owningSquadron} are stamped from the path-resolved parent
   * (audit finding C-3).
   *
   * @param id parent mission id
   * @param request create payload for the sub-mission
   * @return the persisted parent DTO with the new sub-mission attached
   */
  @PostMapping("/{id}/sub-missions")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(summary = "Create a sub-mission")
  public MissionDto createSubMission(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid @NotNull
          de.greluc.krt.iri.basetool.backend.model.dto.request.CreateMissionRequest request) {
    return missionMapper.toDto(missionService.addSubMission(id, request));
  }

  /**
   * Full-replace update. Bumps {@code Mission.version}, so any second user editing the mission
   * concurrently will get a 409 on their next save. Prefer the section patches ({@link
   * #patchMissionCore}, {@link #patchMissionSchedule}, {@link #patchMissionFlags}) for
   * multi-user-friendly edits.
   *
   * @param id mission id
   * @param request update payload (carries the expected version); structurally excludes server-
   *     managed fields ({@code id}, {@code owningSquadron}, {@code parent}, {@code owner}, …) to
   *     close the audit-finding-C-3 mass-assignment vector
   * @return the persisted DTO
   */
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
      @RequestBody @jakarta.validation.Valid @NotNull
          de.greluc.krt.iri.basetool.backend.model.dto.request.UpdateMissionRequest request) {
    return missionMapper.toDto(missionService.updateMission(id, request));
  }

  /**
   * Patches the core header section (name, description, calendar link, status). Uses the dedicated
   * core-section version so a parallel edit of schedule/flags does not invalidate this form.
   *
   * @param id mission id
   * @param request core patch payload (carries the expected core-section version)
   * @return the persisted DTO
   */
  @PatchMapping("/{id}/core")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(
      summary = "Patch mission core section",
      description =
          "Aktualisiert nur die Stammdaten-Sektion (Name, Beschreibung, Kalenderlink, Status) eines"
              + " Einsatzes. Andere Sektionen und Sub-Aggregate bleiben unberuehrt. Bei einem"
              + " Versionskonflikt wird HTTP 409 (application/problem+json) zurueckgegeben.")
  public MissionDto patchMissionCore(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid @NotNull
          de.greluc.krt.iri.basetool.backend.model.dto.request.PatchMissionCoreRequest request) {
    return missionMapper.toDto(
        missionService.updateCoreSection(
            id,
            request.name(),
            request.description(),
            request.calendarLink(),
            request.status(),
            request.operationId(),
            request.version()));
  }

  /**
   * Patches the schedule section (meeting/planned/actual times). All times in UTC. Schedule has its
   * own version so participants, units and finances editing in parallel does not collide.
   *
   * @param id mission id
   * @param request schedule patch payload (carries the expected schedule-section version)
   * @return the persisted DTO
   */
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
      @RequestBody @jakarta.validation.Valid @NotNull
          de.greluc.krt.iri.basetool.backend.model.dto.request.PatchMissionScheduleRequest
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

  /**
   * Patches the flags section (currently only {@code isInternal}). Independently versioned.
   *
   * @param id mission id
   * @param request flags patch payload (carries the expected flags-section version)
   * @return the persisted DTO
   */
  @PatchMapping("/{id}/flags")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(
      summary = "Patch mission flags section",
      description = "Aktualisiert nur die Flags-Sektion (z.B. isInternal) eines Einsatzes.")
  public MissionDto patchMissionFlags(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid @NotNull
          de.greluc.krt.iri.basetool.backend.model.dto.request.PatchMissionFlagsRequest request) {
    return missionMapper.toDto(
        missionService.updateFlagsSection(id, request.isInternal(), request.version()));
  }

  /**
   * ADMIN-only mission delete. Cascades through participants, units, frequencies and finance
   * entries.
   *
   * @param id mission id
   * @return 204 No Content
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Delete a mission")
  public ResponseEntity<Void> deleteMission(@PathVariable @NotNull UUID id) {
    missionService.deleteMission(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Self-enrolment shortcut — the caller adds themselves as participant. For adding others, use
   * {@link #addParticipantPublic} or the slim {@code addParticipantSlim}.
   *
   * @param jwt caller's JWT
   * @param id mission id
   * @return the persisted DTO
   */
  @PostMapping("/{id}/join")
  @Operation(summary = "Join a mission")
  // SecurityConfig falls through to `anyRequest().authenticated()` for this path, but the
  // explicit `isAuthenticated()` keeps the controller honest if the URL filter is later loosened
  // — anonymous reaches the handler with a null JWT and would NPE in `getUserIdFromJwt`.
  // `canSeeMission` enforces MULTI_SQUADRON_PLAN.md §1: members of another squadron may join
  // only non-internal missions, own-squadron members + admins may join anything.
  @PreAuthorize("isAuthenticated() and @ownerScopeService.canSeeMission(#id)")
  public MissionDto joinMission(@AuthenticationPrincipal Jwt jwt, @PathVariable @NotNull UUID id) {
    return missionMapper.toDto(
        missionService.addParticipant(id, userService.getUserIdFromJwt(jwt)));
  }

  /**
   * Legacy add-unit endpoint. Returns the full {@link MissionDto}. Replaced by {@link #addUnitSlim}
   * which avoids coupling the parent {@code Mission.version} into every AJAX round-trip.
   *
   * @param id mission id
   * @param request unit payload
   * @return the persisted parent DTO
   * @deprecated use {@link #addUnitSlim}; sunset {@value #SLIM_DEPRECATION_SUNSET}
   */
  @Deprecated(forRemoval = true)
  @PostMapping("/{id}/units")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/units/slim")
  @Operation(
      summary = "Add a unit to a mission (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer POST /api/v1/missions/{id}/units/slim which returns"
              + " only the updated list of units and avoids parent-coupled payloads (Option A /"
              + " multi-user concurrency).",
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

  /**
   * Legacy update-unit endpoint.
   *
   * @param id mission id
   * @param unitId unit id
   * @param request unit payload
   * @return the persisted parent DTO
   * @deprecated use {@link #updateUnitSlim}; sunset {@value #SLIM_DEPRECATION_SUNSET}
   */
  @Deprecated(forRemoval = true)
  @PutMapping("/{id}/units/{unitId}")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/units/{unitId}/slim")
  @Operation(
      summary = "Update a mission unit (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer PUT /api/v1/missions/{id}/units/{unitId}/slim which"
              + " returns only the updated unit.",
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

  /**
   * Legacy delete-unit endpoint.
   *
   * @param id mission id
   * @param unitId unit id
   * @return the persisted parent DTO
   * @deprecated use {@link #deleteUnitSlim}; sunset {@value #SLIM_DEPRECATION_SUNSET}
   */
  @Deprecated(forRemoval = true)
  @DeleteMapping("/{id}/units/{unitId}")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/units/{unitId}/slim")
  @Operation(
      summary = "Delete a mission unit (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer DELETE /api/v1/missions/{id}/units/{unitId}/slim"
              + " which returns 204 No Content.",
      deprecated = true)
  public MissionDto deleteUnit(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID unitId) {
    return missionMapper.toDto(missionService.removeMissionUnit(id, unitId));
  }

  /**
   * Legacy add-crew endpoint.
   *
   * @param id mission id
   * @param missionUnitId unit id
   * @param request crew payload (participant + job types)
   * @return the persisted parent DTO
   * @deprecated use {@link #addCrewSlim}; sunset {@value #SLIM_DEPRECATION_SUNSET}
   */
  @Deprecated(forRemoval = true)
  @PostMapping("/{id}/units/{missionUnitId}/crew")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/units/{missionUnitId}/crew/slim")
  @Operation(
      summary = "Add crew to a mission unit (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer POST"
              + " /api/v1/missions/{id}/units/{missionUnitId}/crew/slim which returns only the crew"
              + " list of the affected unit.",
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

  /**
   * Legacy update-crew endpoint — replaces the job-type set of a crew entry.
   *
   * @param id mission id
   * @param missionUnitId unit id
   * @param crewId crew entry id
   * @param request crew payload
   * @return the persisted parent DTO
   * @deprecated use {@link #updateCrewSlim}; sunset {@value #SLIM_DEPRECATION_SUNSET}
   */
  @Deprecated(forRemoval = true)
  @PutMapping("/{id}/units/{missionUnitId}/crew/{crewId}")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/units/{missionUnitId}/crew/{crewId}/slim")
  @Operation(
      summary = "Update crew in a mission unit (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer PUT"
              + " /api/v1/missions/{id}/units/{missionUnitId}/crew/{crewId}/slim which returns only"
              + " the updated crew entry.",
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

  /**
   * Legacy remove-crew endpoint.
   *
   * @param id mission id
   * @param missionUnitId unit id
   * @param crewId crew entry id
   * @return the persisted parent DTO
   * @deprecated use {@link #removeCrewSlim}; sunset {@value #SLIM_DEPRECATION_SUNSET}
   */
  @Deprecated(forRemoval = true)
  @DeleteMapping("/{id}/units/{missionUnitId}/crew/{crewId}")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/units/{missionUnitId}/crew/{crewId}/slim")
  @Operation(
      summary = "Remove crew from a mission unit (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer DELETE"
              + " /api/v1/missions/{id}/units/{missionUnitId}/crew/{crewId}/slim which returns 204"
              + " No Content.",
      deprecated = true)
  public MissionDto removeCrew(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID missionUnitId,
      @PathVariable @NotNull UUID crewId) {
    return missionMapper.toDto(missionService.removeCrewFromShip(id, missionUnitId, crewId));
  }

  /**
   * Legacy update-participant endpoint.
   *
   * @param id mission id
   * @param participantId participant id
   * @param request participant payload (carries the expected participant version)
   * @param jwt caller's JWT (null for anonymous)
   * @return the persisted parent DTO (redacted via {@link #cleanupMissionForGuest} for anonymous
   *     callers, who reach this endpoint when editing a guest participant per {@code
   *     MissionSecurityService#canAccessParticipant})
   * @deprecated use {@link #updateParticipantSlim}; sunset {@value #SLIM_DEPRECATION_SUNSET}
   */
  @Deprecated(forRemoval = true)
  @PutMapping("/{id}/participants/{participantId}")
  @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/participants/{participantId}/slim")
  @Operation(
      summary = "Update a participant (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer PUT"
              + " /api/v1/missions/{id}/participants/{participantId}/slim which returns only the"
              + " updated participant.",
      deprecated = true)
  public MissionDto updateParticipant(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @RequestBody @jakarta.validation.Valid @NotNull UpdateParticipantRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    MissionDto dto =
        missionMapper.toDto(
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
    if (jwt == null) {
      dto = cleanupMissionForGuest(dto);
    }
    return dto;
  }

  /**
   * Legacy check-in endpoint. Stamps {@code startTime} on the participant.
   *
   * @param id mission id
   * @param participantId participant id
   * @param jwt caller's JWT (null for anonymous)
   * @return the persisted parent DTO (redacted for anonymous callers)
   * @deprecated use {@link #checkInParticipantSlim}; sunset {@value #SLIM_DEPRECATION_SUNSET}
   */
  @Deprecated(forRemoval = true)
  @PostMapping("/{id}/participants/{participantId}/check-in")
  @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/participants/{participantId}/check-in/slim")
  @Operation(
      summary = "Check in a participant (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer POST"
              + " /api/v1/missions/{id}/participants/{participantId}/check-in/slim which returns"
              + " only the updated participant.",
      deprecated = true)
  public MissionDto checkInParticipant(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @AuthenticationPrincipal Jwt jwt) {
    MissionDto dto = missionMapper.toDto(missionService.checkIn(id, participantId));
    if (jwt == null) {
      dto = cleanupMissionForGuest(dto);
    }
    return dto;
  }

  /**
   * Legacy check-out endpoint. Stamps {@code endTime} on the participant.
   *
   * @param id mission id
   * @param participantId participant id
   * @param jwt caller's JWT (null for anonymous)
   * @return the persisted parent DTO (redacted for anonymous callers)
   * @deprecated use {@link #checkOutParticipantSlim}; sunset {@value #SLIM_DEPRECATION_SUNSET}
   */
  @Deprecated(forRemoval = true)
  @PostMapping("/{id}/participants/{participantId}/check-out")
  @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/participants/{participantId}/check-out/slim")
  @Operation(
      summary = "Check out a participant (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer POST"
              + " /api/v1/missions/{id}/participants/{participantId}/check-out/slim which returns"
              + " only the updated participant.",
      deprecated = true)
  public MissionDto checkOutParticipant(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @AuthenticationPrincipal Jwt jwt) {
    MissionDto dto = missionMapper.toDto(missionService.checkOut(id, participantId));
    if (jwt == null) {
      dto = cleanupMissionForGuest(dto);
    }
    return dto;
  }

  /**
   * Legacy payout-preference endpoint. {@code DONATE} on any participant is sticky for the whole
   * operation (handled in the service). Anonymous guests reach this path for their own guest
   * participant via {@code MissionSecurityService#canAccessParticipant} and must receive a redacted
   * response.
   *
   * @param id mission id
   * @param participantId participant id
   * @param request payout preference payload
   * @param jwt caller's JWT (null for anonymous)
   * @return the persisted parent DTO (redacted for anonymous callers)
   * @deprecated use {@link #updatePayoutPreferenceSlim}; sunset {@value #SLIM_DEPRECATION_SUNSET}
   */
  @Deprecated(forRemoval = true)
  @PutMapping("/{id}/participants/{participantId}/payout-preference")
  @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/participants/{participantId}/payout-preference/slim")
  @Operation(
      summary = "Update payout preference for a participant (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer PUT"
              + " /api/v1/missions/{id}/participants/{participantId}/payout-preference/slim which"
              + " returns only the updated participant.",
      deprecated = true)
  public MissionDto updatePayoutPreference(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @RequestBody @jakarta.validation.Valid @NotNull UpdatePayoutPreferenceRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    MissionDto dto =
        missionMapper.toDto(
            missionService.updatePayoutPreference(id, participantId, request.preference()));
    if (jwt == null) {
      dto = cleanupMissionForGuest(dto);
    }
    return dto;
  }

  /**
   * Legacy admin add-participant endpoint (registered users only). The public counterpart with
   * guest support is {@link #addParticipantPublic}.
   *
   * @param id mission id
   * @param request add-participant payload (registered user id)
   * @return the persisted parent DTO
   * @deprecated use {@link #addParticipantSlim}; sunset {@value #SLIM_DEPRECATION_SUNSET}
   */
  @Deprecated(forRemoval = true)
  @PostMapping("/{id}/participants")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/participants/slim")
  @Operation(
      summary = "Add a participant (admin, legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer POST /api/v1/missions/{id}/participants/slim which"
              + " returns only the updated participant list.",
      deprecated = true)
  public MissionDto addParticipant(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid @NotNull AddParticipantRequest request) {
    return missionMapper.toDto(missionService.addParticipant(id, request.userId()));
  }

  /**
   * Legacy remove-participant endpoint.
   *
   * @param id mission id
   * @param participantId participant id
   * @param jwt caller's JWT (null for anonymous)
   * @return the persisted parent DTO (redacted for anonymous callers)
   * @deprecated use {@link #removeParticipantSlim}; sunset {@value #SLIM_DEPRECATION_SUNSET}
   */
  @Deprecated(forRemoval = true)
  @DeleteMapping("/{id}/participants/{participantId}")
  @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/participants/{participantId}/slim")
  @Operation(
      summary = "Remove a participant (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer DELETE"
              + " /api/v1/missions/{id}/participants/{participantId}/slim which returns 204 No"
              + " Content.",
      deprecated = true)
  public MissionDto removeParticipant(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @AuthenticationPrincipal Jwt jwt) {
    MissionDto dto = missionMapper.toDto(missionService.removeParticipant(id, participantId));
    if (jwt == null) {
      dto = cleanupMissionForGuest(dto);
    }
    return dto;
  }

  /**
   * Public add-participant endpoint. Accepts either an explicit {@code userId} (autocomplete pick)
   * or a free-text {@code guestName}. Free-text names are resolved case-insensitively against the
   * user table:
   *
   * <ul>
   *   <li>unique match + authenticated caller → linked as registered participant;
   *   <li>unique match + anonymous caller → 400 (spoofing protection);
   *   <li>no match → treated as guest;
   *   <li>multiple matches → 409 (ambiguous name).
   * </ul>
   *
   * <p>Anonymous callers may never submit a {@code userId} directly.
   *
   * @param id mission id
   * @param request add-participant payload (userId XOR guestName + comment + squadron)
   * @param jwt caller's JWT (null for anonymous)
   * @return the persisted parent DTO
   */
  @PostMapping("/{id}/participants/add")
  @Operation(
      summary = "Add a participant (public)",
      description =
          "Adds a participant by explicit userId (from autocomplete) or by free-text guestName."
              + " Free-text names are resolved case-insensitively against existing users: a unique"
              + " match links the participant as a registered member; no match falls back to the"
              + " guest path; multiple matches return 409 (ambiguous name).")
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
  // MULTI_SQUADRON_PLAN.md §1: "Anmelde-Sicht" is open to anonymous + cross-staffel callers only
  // for NON-internal missions; internal missions of a foreign squadron must reject sign-ups.
  // `canSeeMission` returns true for own-squadron, admin, and non-internal-anywhere — exactly
  // the matrix we need. Without this gate, an anonymous user could create a guest participant
  // on an internal mission of any squadron (the URL is `permitAll` in SecurityConfig).
  @PreAuthorize("@ownerScopeService.canSeeMission(#id)")
  public MissionDto addParticipantPublic(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid @NotNull AddParticipantPublicRequest request,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
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
        log.debug("Participant name is ambiguous ({} matches) for mission {}", matches.size(), id);
        throw new BusinessConflictException("Participant name is ambiguous.");
      }
      if (matches.size() == 1) {
        if (jwt != null) {
          finalUserId = matches.get(0).getId();
          finalGuestName = null;
          log.debug(
              "Resolved free-text participant name to userId {} for mission {}", finalUserId, id);
        } else {
          // Anonymous user tried to add a name that belongs to a registered member -> keep spoofing
          // protection.
          throw new BadRequestException("Guest name is already taken.");
        }
      }
    }

    // H-1 (2026-05-20 audit): the legacy public add-participant let an authenticated non-manager
    // submit a foreign userId and silently add another registered member as participant. Mirror
    // the slim variant's self-vs-manager check — self-enroll always works, adding someone else
    // requires {@code canManageMission}.
    if (jwt != null && finalUserId != null) {
      UUID callerId = userService.getUserIdFromJwt(jwt);
      if ((callerId == null || !finalUserId.equals(callerId))
          && !missionSecurityService.canManageMission(id, authentication)) {
        throw new AccessDeniedException(
            "Only mission managers may add other users as participants.");
      }
    }

    MissionDto dto =
        missionMapper.toDto(
            missionService.addParticipant(
                id,
                finalUserId,
                finalGuestName,
                request.desiredJobTypeId(),
                request.comment(),
                request.squadronId()));
    // C-1 + H-2: every caller below Officer+ gets the peer-redacted shape — anonymous callers
    // would otherwise see participant emails / real names; authenticated non-Logistician callers
    // got the same leak on the legacy public endpoint until H-2. The slim variant's redaction
    // covers the same matrix; keep both consistent so the {@code .../add} legacy path can be
    // removed at sunset without behavioural surprises. The ArchUnit rule
    // {@code anonymousReadableMissionEndpointsMustRedactGuestPii} keeps this from regressing.
    if (jwt == null || !authHelperService.isLogisticianOrAbove()) {
      dto = cleanupMissionForGuest(dto);
    }
    return dto;
  }

  /**
   * Legacy add/update frequency endpoint — upsert by frequency-type.
   *
   * @param id mission id
   * @param request frequency payload (type + value)
   * @return the persisted parent DTO
   * @deprecated use {@link #addOrUpdateFrequencySlim}; sunset {@value #SLIM_DEPRECATION_SUNSET}
   */
  @Deprecated(forRemoval = true)
  @PostMapping("/{id}/frequencies")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/frequencies/slim")
  @Operation(
      summary = "Add or update a frequency for a mission (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer POST /api/v1/missions/{id}/frequencies/slim which"
              + " returns only the updated frequency list.",
      deprecated = true)
  public MissionDto addOrUpdateFrequency(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid
          de.greluc.krt.iri.basetool.backend.model.dto.request.AddFrequencyRequest request) {
    return missionMapper.toDto(
        missionService.addOrUpdateMissionFrequency(id, request.frequencyTypeId(), request.value()));
  }

  /**
   * Legacy remove-frequency endpoint.
   *
   * @param id mission id
   * @param frequencyId frequency id
   * @return the persisted parent DTO
   * @deprecated use {@link #removeFrequencySlim}; sunset {@value #SLIM_DEPRECATION_SUNSET}
   */
  @Deprecated(forRemoval = true)
  @DeleteMapping("/{id}/frequencies/{frequencyId}")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/frequencies/{frequencyId}/slim")
  @Operation(
      summary = "Remove a frequency from a mission (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer DELETE"
              + " /api/v1/missions/{id}/frequencies/{frequencyId}/slim which returns 204 No"
              + " Content.",
      deprecated = true)
  public MissionDto removeFrequency(
      @PathVariable @NotNull UUID id, @PathVariable @NotNull UUID frequencyId) {
    return missionMapper.toDto(missionService.removeMissionFrequency(id, frequencyId));
  }

  /**
   * Legacy add-manager endpoint. Wraps the service call in try/catch with debug-level tracing to
   * aid diagnosis of intermittent test-environment failures — kept until the slim replacement
   * absorbs production load.
   *
   * @param id mission id
   * @param userId user id to add as manager
   * @return the persisted parent DTO
   * @deprecated use {@link #addManagerSlim}; sunset {@value #SLIM_DEPRECATION_SUNSET}
   */
  @Deprecated(forRemoval = true)
  @PostMapping("/{id}/managers/{userId}")
  @PreAuthorize("@missionSecurityService.canManageManagers(#id, authentication)")
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/managers/{userId}/slim")
  @Operation(
      summary = "Add a manager to a mission (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer POST /api/v1/missions/{id}/managers/{userId}/slim"
              + " which returns only the updated manager list.",
      deprecated = true)
  public MissionDto addManager(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID userId) {
    log.debug("MissionController.addManager START - id: {}, userId: {}", id, userId);
    try {
      var mission = missionService.addManager(id, userId);
      log.debug("MissionController.addManager SUCCESS - id: {}, userId: {}", id, userId);
      return missionMapper.toDto(mission);
    } catch (Exception e) {
      log.debug(
          "MissionController.addManager ERROR - id: {}, userId: {}, error: {}",
          id,
          userId,
          e.getMessage(),
          e);
      throw e;
    }
  }

  /**
   * Legacy remove-manager endpoint.
   *
   * @param id mission id
   * @param userId user id to remove from managers
   * @return the persisted parent DTO
   * @deprecated use {@link #removeManagerSlim}; sunset {@value #SLIM_DEPRECATION_SUNSET}
   */
  @Deprecated(forRemoval = true)
  @DeleteMapping("/{id}/managers/{userId}")
  @PreAuthorize("@missionSecurityService.canManageManagers(#id, authentication)")
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = SLIM_DEPRECATION_SUNSET,
      replacement = "/api/v1/missions/{id}/managers/{userId}/slim")
  @Operation(
      summary = "Remove a manager from a mission (legacy, deprecated)",
      description =
          "Returns the full MissionDto. Prefer DELETE /api/v1/missions/{id}/managers/{userId}/slim"
              + " which returns 204 No Content.",
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

  /**
   * Legacy owner-change endpoint without optimistic lock on the ownership aggregate. Replaced by
   * {@link #updateMissionOwner} which carries a version field and does not bump {@code
   * Mission.version}.
   *
   * @param id mission id
   * @param userId new owner id
   * @return the persisted parent DTO
   * @deprecated use {@link #updateMissionOwner}; sunset 2026-10-20
   */
  @Deprecated(forRemoval = true)
  @PutMapping("/{id}/owner/{userId}")
  @PreAuthorize("@missionSecurityService.canChangeOwner(#id, authentication)")
  @de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation(
      sunset = "2026-10-20",
      replacement = "/api/v1/missions/{id}/owner")
  @Operation(
      summary = "Change the owner of a mission (legacy, deprecated)",
      description =
          "Legacy endpoint without optimistic lock on the ownership aggregate. Prefer PUT"
              + " /api/v1/missions/{id}/owner with UpdateMissionOwnerRequest (includes version) to"
              + " benefit from per-section optimistic locking that does not invalidate other users'"
              + " open forms on the same mission.",
      deprecated = true)
  public MissionDto setMissionOwnerLegacy(
      @PathVariable @NotNull UUID id, @PathVariable @NotNull UUID userId) {
    log.debug("MissionController.setMissionOwnerLegacy START - id: {}, userId: {}", id, userId);
    try {
      var mission = missionService.setMissionOwner(id, userId);
      log.debug("MissionController.setMissionOwnerLegacy SUCCESS - id: {}, userId: {}", id, userId);
      return missionMapper.toDto(mission);
    } catch (Exception e) {
      log.debug(
          "MissionController.setMissionOwnerLegacy ERROR - id: {}, userId: {}, error: {}",
          id,
          userId,
          e.getMessage(),
          e);
      throw e;
    }
  }

  /**
   * Owner change through the dedicated {@code MissionOwnership} aggregate. The version field in the
   * request must match the current ownership version (not {@code Mission.version}). The mission
   * version stays untouched, so concurrent edits on other sections remain valid.
   *
   * @param id mission id
   * @param request owner-change payload (new owner id + expected ownership version)
   * @return the persisted DTO
   */
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
      @RequestBody @jakarta.validation.Valid @NotNull
          de.greluc.krt.iri.basetool.backend.model.dto.request.UpdateMissionOwnerRequest request) {
    var mission = missionService.updateMissionOwner(id, request.userId(), request.version());
    return missionMapper.toDto(mission);
  }

  // =====================================================================================
  // Slim sub-resource endpoints (Option A / multi-user concurrency).

  // These endpoints are additive replacements for the legacy MissionDto-returning
  // sub-endpoints above. They return only the affected slim sub-DTO (or a slim list,
  // or 204 No Content) instead of the full MissionDto. This lets the frontend run
  // per-sub-aggregate DOM `data-version` synchronisation without coupling the
  // Mission parent version into every AJAX round-trip.

  // Behaviour and service-level concurrency semantics are IDENTICAL to the legacy
  // endpoints; only the response shape is slim. See ApiDeprecation annotations on
  // the legacy endpoints for the sunset date.
  // =====================================================================================

  /**
   * Locates a unit inside a mission aggregate by id, or throws {@link NotFoundException}. Used by
   * the slim endpoints to project a single sub-aggregate without re-fetching from the database.
   *
   * @param mission mission aggregate
   * @param unitId unit id to find
   * @return the matching unit
   */
  private de.greluc.krt.iri.basetool.backend.model.MissionUnit findUnit(
      de.greluc.krt.iri.basetool.backend.model.Mission mission, UUID unitId) {
    return mission.getAssignedUnits().stream()
        .filter(u -> unitId.equals(u.getId()))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Mission unit not found"));
  }

  /**
   * Locates a participant inside a mission aggregate by id, or throws {@link NotFoundException}.
   *
   * @param mission mission aggregate
   * @param participantId participant id to find
   * @return the matching participant
   */
  private de.greluc.krt.iri.basetool.backend.model.MissionParticipant findParticipant(
      de.greluc.krt.iri.basetool.backend.model.Mission mission, UUID participantId) {
    return mission.getParticipants().stream()
        .filter(p -> participantId.equals(p.getId()))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Participant not found"));
  }

  /**
   * Locates a crew entry inside a unit by id, or throws {@link NotFoundException}.
   *
   * @param unit unit aggregate
   * @param crewId crew entry id to find
   * @return the matching crew entry
   */
  private de.greluc.krt.iri.basetool.backend.model.MissionCrew findCrew(
      de.greluc.krt.iri.basetool.backend.model.MissionUnit unit, UUID crewId) {
    return unit.getCrew().stream()
        .filter(c -> crewId.equals(c.getId()))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Crew member not found"));
  }

  // --- Units ---

  /**
   * Adds a unit and returns only the updated unit list (slim). Preferred over {@link #addUnit} —
   * doesn't drag the {@code Mission.version} into the round-trip.
   *
   * @param id mission id
   * @param request unit payload
   * @return the updated unit list
   */
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

  /**
   * Updates a unit and returns only the updated unit (slim).
   *
   * @param id mission id
   * @param unitId unit id
   * @param request unit payload
   * @return the updated unit DTO
   */
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

  /**
   * Deletes a unit; returns 204.
   *
   * @param id mission id
   * @param unitId unit id
   * @return 204 No Content
   */
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

  /**
   * Adds crew and returns only the affected unit's crew list (slim).
   *
   * @param id mission id
   * @param missionUnitId unit id
   * @param request crew payload (participant + job types)
   * @return the updated crew list of the unit
   */
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

  /**
   * Updates a crew entry and returns only the updated entry (slim).
   *
   * @param id mission id
   * @param missionUnitId unit id
   * @param crewId crew entry id
   * @param request crew payload (job-type set)
   * @return the updated crew DTO
   */
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

  /**
   * Removes a crew entry; returns 204.
   *
   * @param id mission id
   * @param missionUnitId unit id
   * @param crewId crew entry id
   * @return 204 No Content
   */
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

  /**
   * Updates a participant and returns only the updated participant (slim).
   *
   * @param id mission id
   * @param participantId participant id
   * @param request participant payload (carries the expected participant version)
   * @param jwt caller's JWT (null for anonymous)
   * @return the updated participant DTO
   */
  @PutMapping("/{id}/participants/{participantId}/slim")
  @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
  @Operation(
      summary = "Update a participant (slim response)",
      description = "Updates a participant and returns only the updated participant as a slim DTO.")
  public MissionParticipantDto updateParticipantSlim(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @RequestBody @jakarta.validation.Valid @NotNull UpdateParticipantRequest request,
      @AuthenticationPrincipal Jwt jwt) {
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
    MissionParticipantDto dto = missionMapper.toDto(findParticipant(mission, participantId));
    // The {@code cleanupParticipantForGuest} call here satisfies the ArchUnit rule {@code
    // anonymousReadableMissionEndpointsMustRedactGuestPii} (audit finding C-1): the participant
    // {@code canAccessParticipant} lets anonymous reach this endpoint reaches only guest entries
    // anyway ({@code participant.user == null}), so the redaction is a no-op for the data — but
    // calling it directly is the structural guarantee that a future mapping change which surfaces
    // a non-null {@code UserDto} on a guest participant cannot leak through.
    if (jwt == null) {
      dto = cleanupParticipantForGuest(dto);
    }
    return dto;
  }

  /**
   * Slim check-in. Stamps {@code startTime} on the participant.
   *
   * @param id mission id
   * @param participantId participant id
   * @param jwt caller's JWT (null for anonymous)
   * @return the updated participant DTO (redacted for anonymous callers)
   */
  @PostMapping("/{id}/participants/{participantId}/check-in/slim")
  @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
  @Operation(
      summary = "Check in a participant (slim response)",
      description =
          "Checks in a participant and returns only the updated participant as a slim DTO.")
  public MissionParticipantDto checkInParticipantSlim(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @AuthenticationPrincipal Jwt jwt) {
    var mission = missionService.checkIn(id, participantId);
    MissionParticipantDto dto = missionMapper.toDto(findParticipant(mission, participantId));
    if (jwt == null) {
      dto = cleanupParticipantForGuest(dto);
    }
    return dto;
  }

  /**
   * Slim check-out. Stamps {@code endTime} on the participant.
   *
   * @param id mission id
   * @param participantId participant id
   * @param jwt caller's JWT (null for anonymous)
   * @return the updated participant DTO (redacted for anonymous callers)
   */
  @PostMapping("/{id}/participants/{participantId}/check-out/slim")
  @PreAuthorize("@missionSecurityService.canAccessParticipant(#id, #participantId, authentication)")
  @Operation(
      summary = "Check out a participant (slim response)",
      description =
          "Checks out a participant and returns only the updated participant as a slim DTO.")
  public MissionParticipantDto checkOutParticipantSlim(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @AuthenticationPrincipal Jwt jwt) {
    var mission = missionService.checkOut(id, participantId);
    MissionParticipantDto dto = missionMapper.toDto(findParticipant(mission, participantId));
    if (jwt == null) {
      dto = cleanupParticipantForGuest(dto);
    }
    return dto;
  }

  /**
   * Slim payout-preference update. {@code DONATE} stays sticky for the whole operation.
   *
   * @param id mission id
   * @param participantId participant id
   * @param request payout preference payload
   * @param jwt caller's JWT (null for anonymous)
   * @return the updated participant DTO (redacted for anonymous callers)
   */
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
      @AuthenticationPrincipal Jwt jwt) {
    var mission = missionService.updatePayoutPreference(id, participantId, request.preference());
    MissionParticipantDto dto = missionMapper.toDto(findParticipant(mission, participantId));
    if (jwt == null) {
      dto = cleanupParticipantForGuest(dto);
    }
    return dto;
  }

  /**
   * Slim add-participant — mirrors {@link #addParticipantPublic} logic with one extra access tier:
   * an authenticated, non-manager caller may always self-enroll but needs {@code canManageMission}
   * to add anyone else (raised at the HTTP boundary, not in the service). Returns only the updated
   * participant list.
   *
   * @param id mission id
   * @param request add-participant payload (userId XOR guestName + meta)
   * @param jwt caller's JWT (null for anonymous)
   * @param authentication current Spring Security authentication
   * @return the updated participant list
   */
  @PostMapping("/{id}/participants/slim")
  @Operation(
      summary = "Add a participant (slim response)",
      description =
          "Adds a participant and returns the updated participant list as slim DTOs. Mirrors the"
              + " public add-participant logic: explicit userId (autocomplete) or free-text"
              + " guestName (case-insensitive resolution against registered users). Authenticated"
              + " users may always add themselves; adding other registered users is restricted to"
              + " managers/officers/admins. Anonymous users may only add guest entries.")
  // MULTI_SQUADRON_PLAN.md §1: same gate as the legacy `/participants/add` endpoint — only
  // non-internal missions accept cross-staffel / anonymous sign-ups. Internal missions are
  // gated even though the URL is `permitAll` in SecurityConfig.
  @PreAuthorize("@ownerScopeService.canSeeMission(#id)")
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
    java.util.stream.Stream<MissionParticipantDto> participants =
        mission.getParticipants().stream().map(missionMapper::toDto);
    // C-1 (anonymous) + H-5 (authenticated non-Officer): every caller below Officer+ gets the
    // peer-redacted user shape. The mission roster UI works fine with the slim fields (callsign,
    // displayName, rank); only Officer / Admin (moderation) and {@code /users/me} (the caller's
    // own row) need full PII. The ArchUnit rule
    // {@code anonymousReadableMissionEndpointsMustRedactGuestPii} statically enforces this for any
    // future endpoint added with the same {@code @ownerScopeService.canSeeMission(#id)} gate.
    if (jwt == null || !authHelperService.isLogisticianOrAbove()) {
      participants = participants.map(this::cleanupParticipantForGuest);
    }
    return participants.toList();
  }

  /**
   * Removes a participant; returns 204.
   *
   * @param id mission id
   * @param participantId participant id
   * @param authentication current Spring Security authentication
   * @return 204 No Content
   */
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

  /**
   * Participants of a mission that are not yet on any unit's crew roster — drives the "unassigned"
   * tray on the mission detail page.
   *
   * @param id mission id
   * @return unassigned participant DTOs
   */
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

  /**
   * Upserts a frequency by type; returns only the updated frequency list (slim).
   *
   * @param id mission id
   * @param request frequency payload (type + value)
   * @return the updated frequency list
   */
  @PostMapping("/{id}/frequencies/slim")
  @PreAuthorize("@missionSecurityService.canManageMission(#id, authentication)")
  @Operation(
      summary = "Add or update a frequency for a mission (slim response)",
      description =
          "Adds or updates a frequency and returns the updated frequency list as slim DTOs.")
  public List<MissionFrequencyDto> addOrUpdateFrequencySlim(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid
          de.greluc.krt.iri.basetool.backend.model.dto.request.AddFrequencyRequest request) {
    var mission =
        missionService.addOrUpdateMissionFrequency(id, request.frequencyTypeId(), request.value());
    return mission.getFrequencies().stream().map(missionMapper::toDto).toList();
  }

  /**
   * Removes a frequency; returns 204.
   *
   * @param id mission id
   * @param frequencyId frequency id
   * @return 204 No Content
   */
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

  /**
   * Adds a manager; returns the updated manager list as {@link UserReferenceDto}s (id + label
   * only).
   *
   * @param id mission id
   * @param userId user id to add as manager
   * @return the updated manager list
   */
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

  /**
   * Removes a manager; returns 204.
   *
   * @param id mission id
   * @param userId user id to remove from managers
   * @return 204 No Content
   */
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

  /**
   * Wraps a {@link Page} into the API-friendly {@link PageResponse} envelope (current page, page
   * size, totals, sort tokens).
   *
   * @param page Spring Data page
   * @param <T> element type
   * @return the page response DTO
   */
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

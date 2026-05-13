package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.JobTypeDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionCrewDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionFinanceEntryDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionFrequencyDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionListDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionUnitDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OperationDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.RefineryOrderListDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.ShipDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.ShipTypeDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.UpdatePayoutPreferenceRequest;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserReferenceDto;
import de.greluc.krt.iri.basetool.frontend.model.form.CrewForm;
import de.greluc.krt.iri.basetool.frontend.model.form.MissionForm;
import de.greluc.krt.iri.basetool.frontend.model.form.ParticipantForm;
import de.greluc.krt.iri.basetool.frontend.model.form.UnitForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/missions")
@RequiredArgsConstructor
@Slf4j
public class MissionPageController {

  private final BackendApiClient backendApiClient;

  private void addOperationsToModel(Model model, boolean isPublic) {
    if (isPublic) {
      model.addAttribute("operationsList", List.of());
      return;
    }
    try {
      PageResponse<OperationDto> operationsPage =
          backendApiClient.get(
              "/api/v1/operations?page=0&size=1000",
              new ParameterizedTypeReference<PageResponse<OperationDto>>() {},
              false);
      model.addAttribute("operationsList", operationsPage.content());
    } catch (Exception e) {
      log.warn("Could not load operations", e);
      model.addAttribute("operationsList", List.of());
    }
  }

  public void addFormsToModel(Model model, OidcUser principal) {
    if (!model.containsAttribute("participantForm")) {
      ParticipantForm form =
          new ParticipantForm(null, "", null, null, "", null, null, null, null, null);
      if (principal != null) {
        try {
          UserDto me = backendApiClient.get("/api/v1/users/me", UserDto.class);
          if (me != null) {
            String name =
                (me.displayName() != null && !me.displayName().isBlank())
                    ? me.displayName()
                    : me.username();
            UUID squadronId = null;
            if (me.roles() != null
                && (me.roles().contains("MEMBER")
                    || me.roles().contains("ROLE_MEMBER")
                    || me.roles().contains("SQUADRON_MEMBER")
                    || me.roles().contains("ROLE_SQUADRON_MEMBER")
                    || me.roles().contains("ADMIN")
                    || me.roles().contains("ROLE_ADMIN")
                    || me.roles().contains("OFFICER")
                    || me.roles().contains("ROLE_OFFICER"))) {
              PageResponse<Map<String, Object>> squadronsPage =
                  backendApiClient.getCached(
                      "/api/v1/squadrons?size=1000",
                      new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {},
                      true);
              if (squadronsPage != null && squadronsPage.content() != null) {
                for (Map<String, Object> sq : squadronsPage.content()) {
                  if ("Iridium".equalsIgnoreCase(String.valueOf(sq.get("name")))) {
                    squadronId = UUID.fromString(String.valueOf(sq.get("id")));
                    break;
                  }
                }
              }
            }
            form =
                new ParticipantForm(
                    me.id(), name, squadronId, null, "", null, null, null, null, null);
          }
        } catch (Exception e) {
          log.warn("Could not prefill participant form", e);
        }
      }
      model.addAttribute("participantForm", form);
    }
    if (!model.containsAttribute("unitForm")) {
      model.addAttribute("unitForm", new UnitForm("", null, null, false, null));
    }
    if (!model.containsAttribute("crewForm")) {
      model.addAttribute("crewForm", new CrewForm(null, null));
    }
    if (!model.containsAttribute("financeForm")) {
      model.addAttribute(
          "financeForm",
          new de.greluc.krt.iri.basetool.frontend.model.form.MissionFinanceEntryForm());
    }
  }

  @InitBinder
  public void initBinder(WebDataBinder binder) {
    binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
  }

  @GetMapping
  public String listMissions(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String start,
      @RequestParam(required = false) String end,
      @RequestParam(required = false) List<String> status,
      @RequestParam(required = false, defaultValue = "false") boolean showPast,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String fragment,
      Model model,
      @AuthenticationPrincipal OidcUser principal) {
    StringBuilder uri = new StringBuilder("/api/v1/missions/search?");
    if (search != null && !search.isBlank()) {
      uri.append("query=").append(search).append("&");
    }
    if (start != null && !start.isBlank()) {
      uri.append("start=").append(start).append("&");
    }
    if (end != null && !end.isBlank()) {
      uri.append("end=").append(end).append("&");
    }
    if (page != null) {
      uri.append("page=").append(page).append("&");
    }
    if (size != null) {
      uri.append("size=").append(size).append("&");
    }

    if ((status == null || status.isEmpty())) {
      if (showPast && principal != null) {
        // Explicitly request all statuses ONLY if authenticated
        uri.append("status=PLANNED&status=ACTIVE&status=COMPLETED&status=CANCELLED&");
      } else {
        uri.append("status=PLANNED&status=ACTIVE&");
      }
    } else {
      for (String s : status) {
        uri.append("status=").append(s).append("&");
      }
    }

    try {
      boolean isPublic = (principal == null);

      PageResponse<MissionListDto> missionsPage =
          backendApiClient.get(
              uri.toString(),
              new ParameterizedTypeReference<PageResponse<MissionListDto>>() {},
              isPublic);
      model.addAttribute("missions", missionsPage.content());
      model.addAttribute("missionsPage", missionsPage);
      model.addAttribute("search", search);
      model.addAttribute("start", start);
      model.addAttribute("end", end);
      model.addAttribute("showPast", showPast && principal != null);
    } catch (Exception e) {
      log.error("Error loading missions", e);
      model.addAttribute("error", "error.missions.load");
    }
    // AJAX live-filter requests only need the results fragment.
    if (fragment != null && "results".equalsIgnoreCase(fragment)) {
      return "missions :: missionsResults";
    }
    return "missions";
  }

  @GetMapping("/{id}")
  public String missionDetail(
      @PathVariable @NotNull UUID id, Model model, @AuthenticationPrincipal OidcUser principal) {
    try {
      MissionDto mission =
          backendApiClient.get(
              "/api/v1/missions/" + id,
              new ParameterizedTypeReference<MissionDto>() {},
              principal == null);

      // Sort participants and build groupings
      List<MissionParticipantDto> participants = new java.util.ArrayList<>(mission.participants());
      participants.sort(
          (p1, p2) -> {
            String name1 = extractParticipantName(p1);
            String name2 = extractParticipantName(p2);
            return name1.compareToIgnoreCase(name2);
          });

      Map<String, List<MissionParticipantDto>> participantsByLeadType = new java.util.HashMap<>();
      List<JobTypeDto> missionLeadTypes = new java.util.ArrayList<>();
      java.util.Set<UUID> addedLeadTypes = new java.util.HashSet<>();
      for (MissionParticipantDto p : participants) {
        JobTypeDto job = p.plannedMissionJobType();
        if (job != null && job.isLeadershipRole()) {
          UUID jobId = job.id();
          participantsByLeadType
              .computeIfAbsent(jobId.toString(), k -> new java.util.ArrayList<>())
              .add(p);
          if (addedLeadTypes.add(jobId)) {
            missionLeadTypes.add(job);
          }
        }
      }
      model.addAttribute("mission", mission);
      model.addAttribute("participants", participants);
      model.addAttribute("participantsByLeadType", participantsByLeadType);
      model.addAttribute("missionLeadTypes", missionLeadTypes);

      // Sort crew members and build groupings
      Map<UUID, String> assignedUnitByParticipantId = new java.util.HashMap<>();
      if (mission.assignedUnits() != null) {
        for (MissionUnitDto unit : mission.assignedUnits()) {
          String unitName = unit.name() != null ? unit.name() : "";
          if (unit.crew() != null) {
            for (MissionCrewDto c : unit.crew()) {
              if (c.participantId() != null) {
                assignedUnitByParticipantId.merge(
                    c.participantId(), unitName, (oldVal, newVal) -> oldVal + " " + newVal);
              }
            }
          }
        }
      }
      model.addAttribute("assignedUnitByParticipantId", assignedUnitByParticipantId);

      // Calculate participation percentages
      Map<UUID, Double> participationPercentages = new java.util.HashMap<>();
      for (MissionParticipantDto p : participants) {
        participationPercentages.put(p.id(), 0.0);
      }

      java.time.Instant missionStart = mission.actualStartTime();
      java.time.Instant missionEnd = mission.actualEndTime();

      if (missionStart != null) {
        long totalDurationSeconds = 0;

        Map<UUID, Long> participantDurations = new java.util.HashMap<>();

        for (MissionParticipantDto p : participants) {
          java.time.Instant pStart = p.startTime();
          java.time.Instant pEnd = p.endTime();

          if (pStart != null) {
            java.time.Instant effectiveStart =
                pStart.isBefore(missionStart) ? missionStart : pStart;
            java.time.Instant effectiveEnd;
            if (pEnd != null) {
              effectiveEnd = (missionEnd != null && pEnd.isAfter(missionEnd)) ? missionEnd : pEnd;
            } else {
              effectiveEnd = (missionEnd != null) ? missionEnd : java.time.Instant.now();
            }

            if (effectiveEnd.isAfter(effectiveStart)) {
              long duration = java.time.Duration.between(effectiveStart, effectiveEnd).getSeconds();
              participantDurations.put(p.id(), duration);
              totalDurationSeconds += duration;
            }
          }
        }

        if (totalDurationSeconds > 0) {
          for (MissionParticipantDto p : participants) {
            Long duration = participantDurations.get(p.id());
            if (duration != null) {
              double percentage = (double) duration / totalDurationSeconds * 100.0;
              participationPercentages.put(p.id(), percentage);
            }
          }
        }
      }
      model.addAttribute("participationPercentages", participationPercentages);

      // Build frequency lookup
      Map<String, MissionFrequencyDto> frequencyByTypeId = new java.util.HashMap<>();
      if (mission.frequencies() != null) {
        for (MissionFrequencyDto f : mission.frequencies()) {
          if (f.frequencyTypeId() != null) {
            frequencyByTypeId.put(f.frequencyTypeId().toString(), f);
          }
        }
      }
      model.addAttribute("frequencyByTypeId", frequencyByTypeId);

      // Fetch all users for manager selection
      if (principal != null) {
        try {
          List<UserReferenceDto> allUsers =
              backendApiClient.get(
                  "/api/v1/users/lookup",
                  new ParameterizedTypeReference<List<UserReferenceDto>>() {},
                  false);
          model.addAttribute("allUsers", allUsers);
        } catch (Exception e) {
          log.warn("Could not load users for manager selection", e);
        }
      }

      if (!model.containsAttribute("missionForm")) {
        model.addAttribute(
            "missionForm",
            new MissionForm(
                mission.name() != null ? mission.name() : "",
                mission.description() != null ? mission.description() : "",
                mission.calendarLink() != null ? mission.calendarLink() : "",
                mission.status() != null ? mission.status() : "",
                formatInstant(mission.meetingTime()),
                formatInstant(mission.plannedStartTime()),
                formatInstant(mission.plannedEndTime()),
                formatInstant(mission.actualStartTime()),
                formatInstant(mission.actualEndTime()),
                mission.isInternal() != null && mission.isInternal(),
                mission.operation() != null ? String.valueOf(mission.operation().id()) : null,
                mission.version()));
      }
      model.addAttribute("isNew", false);
      model.addAttribute("authUserId", principal != null ? principal.getSubject() : null);
      addFormsToModel(model, principal);
      addOperationsToModel(model, principal == null);

      model.addAttribute("roundingMode", fetchRoundingMode(principal == null));

      // Fetch Mission JobTypes
      try {
        PageResponse<Map<String, Object>> jobTypesPage =
            backendApiClient.getCached(
                "/api/v1/job-types?archetype=MISSION&size=1000",
                new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {},
                true);
        model.addAttribute("jobTypes", jobTypesPage.content());
      } catch (Exception e) {
        // Ignore if job types fail
      }

      // Fetch Crew JobTypes
      try {
        PageResponse<Map<String, Object>> crewJobTypesPage =
            backendApiClient.getCached(
                "/api/v1/job-types?archetype=CREW&size=1000",
                new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {},
                true);
        model.addAttribute("crewJobTypes", crewJobTypesPage.content());
      } catch (Exception e) {
        // Ignore
      }

      // Fetch Squadrons
      try {
        PageResponse<Map<String, Object>> squadronsPage =
            backendApiClient.getCached(
                "/api/v1/squadrons?size=1000",
                new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {},
                true);
        model.addAttribute("squadrons", squadronsPage.content());
      } catch (Exception e) {
        // Ignore
      }

      // Fetch FrequencyTypes
      try {
        PageResponse<Map<String, Object>> freqTypesPage =
            backendApiClient.getCached(
                "/api/v1/frequency-types?size=1000&active=true&sort=sortIndex,asc",
                new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {},
                true);
        model.addAttribute("frequencyTypes", freqTypesPage.content());
      } catch (Exception e) {
        // Ignore
      }

      // Fetch Ships (Only if authenticated)
      if (principal != null) {
        try {
          PageResponse<ShipDto> allShipsPage =
              backendApiClient.getCached(
                  "/api/v1/hangar/ships?size=1000",
                  new ParameterizedTypeReference<PageResponse<ShipDto>>() {});
          model.addAttribute("allShips", allShipsPage.content());
        } catch (Exception e) {
          // Ignore, e.g. if user has no HANGAR_READ or other issue
        }

        try {
          PageResponse<ShipTypeDto> allShipTypesPage =
              backendApiClient.getCached(
                  "/api/v1/ship-types?size=1000",
                  new ParameterizedTypeReference<PageResponse<ShipTypeDto>>() {});
          model.addAttribute("allShipTypes", allShipTypesPage.content());
        } catch (Exception e) {
          // Ignore, e.g. if user has no HANGAR_READ or other issue
        }
      }

      // Fetch Finance Entries and Refinery Orders
      if (principal != null) {
        try {
          PageResponse<MissionFinanceEntryDto> financesPage =
              backendApiClient.get(
                  "/api/v1/missions/" + id + "/finance-entries?size=1000",
                  new ParameterizedTypeReference<PageResponse<MissionFinanceEntryDto>>() {},
                  false);
          model.addAttribute("financeEntries", financesPage.content());

          java.math.BigDecimal financeSum =
              backendApiClient.get(
                  "/api/v1/missions/" + id + "/finance-entries/sum",
                  java.math.BigDecimal.class,
                  false);
          model.addAttribute("financeSum", financeSum);

          List<RefineryOrderListDto> refineryOrders =
              backendApiClient.get(
                  "/api/v1/refinery-orders/mission/" + id,
                  new ParameterizedTypeReference<List<RefineryOrderListDto>>() {},
                  false);
          model.addAttribute("refineryOrders", refineryOrders);
        } catch (Exception e) {
          log.error("Error loading finance entries or refinery orders", e);
        }
      }

    } catch (Exception e) {
      log.error("Error loading mission details", e);
      model.addAttribute("error", "error.mission.details.load");
      return "redirect:/missions?error=error.mission.details.load";
    }
    // Expose the authenticated user's JWT sub (Keycloak UUID) so Thymeleaf can
    // robustly decide whether a participant row belongs to the current user
    // and enable self-edit on the member's own entry.
    // NOTE: currentAuth.getName() returns the preferred_username (configured via
    // user-name-attribute),
    // NOT the Keycloak UUID. We must use principal.getSubject() to get the sub (UUID) that matches
    // p.user.id in the participant list.
    model.addAttribute("authUserId", principal != null ? principal.getSubject() : null);
    return "mission-detail";
  }

  @PostMapping("/{id}/participant")
  public String addParticipant(
      @PathVariable @NotNull UUID id,
      @Valid @ModelAttribute("participantForm") ParticipantForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes,
      @AuthenticationPrincipal OidcUser principal) {
    if (bindingResult.hasErrors()) {
      // Render directly; BindingResult stays request-scoped (see RedisSessionConfig).
      model.addAttribute("openModal", "participant-modal");
      return missionDetail(id, model, principal);
    }
    try {
      Map<String, Object> body = new HashMap<>();
      if (form.userId() != null) {
        body.put("userId", form.userId());
      }
      if (form.guestName() != null && !form.guestName().isBlank()) {
        body.put("guestName", form.guestName());
      }
      if (form.desiredJobTypeId() != null) {
        body.put("desiredJobTypeId", form.desiredJobTypeId());
      }
      if (form.squadronId() != null) {
        body.put("squadronId", form.squadronId());
      }
      body.put("comment", form.comment());

      boolean isPublic = (principal == null);
      backendApiClient.post(
          "/api/v1/missions/" + id + "/participants/add", body, Void.class, isPublic);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error("Add participant failed with status {}: {}", e.getStatusCode(), e.getMessage());
      // 409 Conflict = backend found more than one registered member matching the free-text name
      // -> show a dedicated, localized hint that the user should pick an entry from the
      // autocomplete.
      String toastKey =
          (e.getStatusCode() == 409)
              ? "error.mission.participant.ambiguous"
              : "error.mission.participant.add";
      redirectAttributes.addFlashAttribute("errorToast", toastKey);
    } catch (Exception e) {
      log.error("Add participant failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.mission.participant.add");
    }
    return "redirect:/missions/" + id;
  }

  @PostMapping("/{id}/participants/{participantId}/check-in")
  public String checkInParticipant(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @AuthenticationPrincipal OidcUser principal,
      RedirectAttributes redirectAttributes) {
    try {
      boolean isPublic = (principal == null);
      backendApiClient.post(
          "/api/v1/missions/" + id + "/participants/" + participantId + "/check-in",
          null,
          Void.class,
          isPublic);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Check-in participant failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.mission.participant.update");
    }
    return "redirect:/missions/" + id;
  }

  @PostMapping("/{id}/participants/{participantId}/check-out")
  public String checkOutParticipant(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @AuthenticationPrincipal OidcUser principal,
      RedirectAttributes redirectAttributes) {
    try {
      boolean isPublic = (principal == null);
      backendApiClient.post(
          "/api/v1/missions/" + id + "/participants/" + participantId + "/check-out",
          null,
          Void.class,
          isPublic);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Check-out participant failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.mission.participant.update");
    }
    return "redirect:/missions/" + id;
  }

  @PostMapping("/{id}/participants/{participantId}/payout-preference")
  @ResponseBody
  public org.springframework.http.ResponseEntity<MissionDto> updatePayoutPreference(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @RequestBody UpdatePayoutPreferenceRequest request,
      @AuthenticationPrincipal OidcUser principal) {
    try {
      boolean isPublic = (principal == null);
      MissionDto updatedMission =
          backendApiClient.put(
              "/api/v1/missions/" + id + "/participants/" + participantId + "/payout-preference",
              request,
              MissionDto.class,
              isPublic);
      return org.springframework.http.ResponseEntity.ok(updatedMission);
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "Update payout preference failed with status {}: {}", e.getStatusCode(), e.getMessage());
      if (e.getStatusCode() == 403 || e.getStatusCode() == 401) {
        return org.springframework.http.ResponseEntity.status(
                org.springframework.http.HttpStatus.FORBIDDEN)
            .build();
      }
      return org.springframework.http.ResponseEntity.status(
              org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
          .build();
    } catch (Exception e) {
      log.error("Update payout preference failed", e);
      return org.springframework.http.ResponseEntity.status(
              org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
          .build();
    }
  }

  /**
   * Sets the actual start or end time of a mission to the supplied UTC instant and saves
   * immediately. The client sends the current {@code version} from the DOM; this engages optimistic
   * locking in the backend, which returns HTTP 409 on concurrent changes.
   *
   * <p>Why this dedicated endpoint? The regular mission update goes through a form submit with
   * full-page reload, which is undesired when clicking "Now". This endpoint therefore fetches the
   * mission from the backend, overwrites only the requested time field and forwards the client's
   * version so that lost updates are prevented (see AGENTS.md: CRITICAL JUNIE RULE - CONCURRENCY
   * AND OPTIMISTIC LOCKING).
   */
  @PostMapping("/{id}/actual-time")
  @ResponseBody
  @PreAuthorize("isAuthenticated()")
  public org.springframework.http.ResponseEntity<MissionDto> updateActualTime(
      @PathVariable @NotNull UUID id,
      @Valid @RequestBody
          de.greluc.krt.iri.basetool.frontend.model.dto.MissionActualTimeUpdateRequest request,
      @AuthenticationPrincipal OidcUser principal) {
    if (request == null
        || request.version() == null
        || (!"actualStartTime".equals(request.field())
            && !"actualEndTime".equals(request.field()))) {
      return org.springframework.http.ResponseEntity.badRequest().build();
    }
    try {
      MissionDto current = backendApiClient.get("/api/v1/missions/" + id, MissionDto.class);
      if (current == null) {
        return org.springframework.http.ResponseEntity.status(
                org.springframework.http.HttpStatus.NOT_FOUND)
            .build();
      }

      Instant newStart =
          "actualStartTime".equals(request.field()) ? request.value() : current.actualStartTime();
      Instant newEnd =
          "actualEndTime".equals(request.field()) ? request.value() : current.actualEndTime();

      MissionDto updated =
          new MissionDto(
              current.id(),
              current.name(),
              current.description(),
              current.calendarLink(),
              current.status(),
              current.meetingTime(),
              current.plannedStartTime(),
              newStart,
              current.plannedEndTime(),
              newEnd,
              current.isInternal(),
              null,
              null,
              null,
              null,
              null,
              null,
              current.operation(),
              null,
              null,
              null,
              null,
              request.version(), // Optimistic-lock version from the DOM
              current.checkedInParticipants(),
              current.registeredParticipants());

      backendApiClient.put("/api/v1/missions/" + id, updated, Void.class);
      MissionDto refreshed = backendApiClient.get("/api/v1/missions/" + id, MissionDto.class);
      return org.springframework.http.ResponseEntity.ok(refreshed);
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error("Update actual time failed with status {}: {}", e.getStatusCode(), e.getMessage());
      org.springframework.http.HttpStatus status;
      switch (e.getStatusCode()) {
        case 409 -> status = org.springframework.http.HttpStatus.CONFLICT;
        case 403, 401 -> status = org.springframework.http.HttpStatus.FORBIDDEN;
        case 404 -> status = org.springframework.http.HttpStatus.NOT_FOUND;
        default -> status = org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
      }
      return org.springframework.http.ResponseEntity.status(status).build();
    } catch (Exception e) {
      log.error("Update actual time failed", e);
      return org.springframework.http.ResponseEntity.status(
              org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
          .build();
    }
  }

  @PostMapping("/{id}/participants/{participantId}/delete")
  public String deleteParticipant(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @AuthenticationPrincipal OidcUser principal,
      RedirectAttributes redirectAttributes) {
    try {
      boolean isPublic = (principal == null);
      backendApiClient.delete(
          "/api/v1/missions/" + id + "/participants/" + participantId, Void.class, isPublic);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.delete");
    } catch (Exception e) {
      log.error("Delete participant failed", e);
      return "redirect:/missions/" + id + "?error=error.mission.participant.delete";
    }
    return "redirect:/missions/" + id;
  }

  @PostMapping("/{id}/participants/{participantId}/update")
  public String updateParticipant(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @Valid @ModelAttribute("participantForm") ParticipantForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes,
      @AuthenticationPrincipal OidcUser principal) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("openModal", "edit-participant-modal");
      model.addAttribute(
          "modalAction", "/missions/" + id + "/participants/" + participantId + "/update");
      return missionDetail(id, model, principal);
    }
    try {
      Map<String, Object> body = new HashMap<>();
      if (form.desiredJobTypeId() != null) {
        body.put("desiredMissionJobTypeId", form.desiredJobTypeId());
      }
      if (form.plannedMissionJobTypeId() != null) {
        body.put("plannedMissionJobTypeId", form.plannedMissionJobTypeId());
      }
      if (form.squadronId() != null) {
        body.put("squadronId", form.squadronId());
      }
      body.put("comment", form.comment());
      if (form.startTime() != null && !form.startTime().isBlank()) {
        java.time.Instant parsed = parseToInstant(form.startTime());
        if (parsed != null) {
          body.put("startTime", parsed.toString());
        }
      }
      if (form.endTime() != null && !form.endTime().isBlank()) {
        java.time.Instant parsed = parseToInstant(form.endTime());
        if (parsed != null) {
          body.put("endTime", parsed.toString());
        }
      }
      if (form.payoutPreference() != null) {
        body.put("payoutPreference", form.payoutPreference().name());
      }
      if (form.version() != null) {
        body.put("version", form.version());
      }

      boolean isPublic = (principal == null);
      backendApiClient.put(
          "/api/v1/missions/" + id + "/participants/" + participantId, body, Void.class, isPublic);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Update participant failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.mission.participant.update");
    }
    return "redirect:/missions/" + id;
  }

  @PostMapping("/{id}/units")
  @PreAuthorize("isAuthenticated()")
  public String addUnit(
      @PathVariable @NotNull UUID id,
      @RequestParam String name,
      @RequestParam(required = false) UUID shipTypeId,
      @RequestParam(required = false) UUID shipId,
      @RequestParam(required = false, defaultValue = "false") boolean highValueUnit,
      @RequestParam(required = false) Double frequency,
      RedirectAttributes redirectAttributes) {
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("name", name);
      body.put("shipTypeId", shipTypeId);
      if (shipId != null) {
        body.put("shipId", shipId);
      }
      body.put("highValueUnit", highValueUnit);
      body.put("frequency", frequency);

      backendApiClient.post("/api/v1/missions/" + id + "/units", body, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Add unit failed", e);
      return "redirect:/missions/" + id + "?error=error.mission.unit.add";
    }
    return "redirect:/missions/" + id;
  }

  @PostMapping("/{id}/units/{unitId}/update")
  @PreAuthorize("isAuthenticated()")
  public String updateUnit(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID unitId,
      @RequestParam String name,
      @RequestParam(required = false) UUID shipTypeId,
      @RequestParam(required = false) UUID shipId,
      @RequestParam(required = false, defaultValue = "false") boolean highValueUnit,
      @RequestParam(required = false) Double frequency,
      RedirectAttributes redirectAttributes) {
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("name", name);
      body.put("shipTypeId", shipTypeId);
      if (shipId != null) {
        body.put("shipId", shipId);
      }
      body.put("highValueUnit", highValueUnit);
      body.put("frequency", frequency);

      backendApiClient.put("/api/v1/missions/" + id + "/units/" + unitId, body, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Update unit failed", e);
      return "redirect:/missions/" + id + "?error=error.mission.unit.update";
    }
    return "redirect:/missions/" + id;
  }

  @PostMapping("/{id}/units/{unitId}/delete")
  @PreAuthorize("isAuthenticated()")
  public String deleteUnit(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID unitId,
      RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/missions/" + id + "/units/" + unitId, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.delete");
    } catch (Exception e) {
      log.error("Delete unit failed", e);
      return "redirect:/missions/" + id + "?error=error.mission.unit.delete";
    }
    return "redirect:/missions/" + id;
  }

  @PostMapping("/{id}/units/{unitId}/crew")
  @PreAuthorize("isAuthenticated()")
  public String addCrew(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID unitId,
      @Valid @ModelAttribute("crewForm") CrewForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes,
      @AuthenticationPrincipal OidcUser principal) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("openModal", "assign-crew-modal");
      model.addAttribute("modalAction", "/missions/" + id + "/units/" + unitId + "/crew");
      return missionDetail(id, model, principal);
    }
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("participantId", form.participantId());
      if (form.jobTypeIds() != null && !form.jobTypeIds().isEmpty()) {
        body.put("jobTypeIds", form.jobTypeIds());
      }

      backendApiClient.post(
          "/api/v1/missions/" + id + "/units/" + unitId + "/crew", body, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Add crew failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.mission.crew.add");
    }
    return "redirect:/missions/" + id;
  }

  @PostMapping("/{id}/units/{unitId}/crew/{crewId}/update")
  @PreAuthorize("isAuthenticated()")
  public String updateCrew(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID unitId,
      @PathVariable @NotNull UUID crewId,
      @Valid @ModelAttribute("crewForm") CrewForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes,
      @AuthenticationPrincipal OidcUser principal) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("openModal", "edit-crew-modal");
      model.addAttribute(
          "modalAction", "/missions/" + id + "/units/" + unitId + "/crew/" + crewId + "/update");
      return missionDetail(id, model, principal);
    }
    try {
      Map<String, Object> body = new HashMap<>();
      if (form.jobTypeIds() != null && !form.jobTypeIds().isEmpty()) {
        body.put("jobTypeIds", form.jobTypeIds());
      } else {
        body.put("jobTypeIds", List.of());
      }

      backendApiClient.put(
          "/api/v1/missions/" + id + "/units/" + unitId + "/crew/" + crewId, body, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Update crew failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.mission.crew.update");
    }
    return "redirect:/missions/" + id;
  }

  @PostMapping("/{id}/units/{unitId}/crew/{crewId}/delete")
  @PreAuthorize("isAuthenticated()")
  public String deleteCrew(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID unitId,
      @PathVariable @NotNull UUID crewId,
      RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete(
          "/api/v1/missions/" + id + "/units/" + unitId + "/crew/" + crewId, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.delete");
    } catch (Exception e) {
      log.error("Delete crew failed", e);
      return "redirect:/missions/" + id + "?error=error.mission.crew.delete";
    }
    return "redirect:/missions/" + id;
  }

  @GetMapping("/new")
  @PreAuthorize("isAuthenticated()")
  public String createMissionForm(Model model, @AuthenticationPrincipal OidcUser principal) {
    if (!model.containsAttribute("missionForm")) {
      model.addAttribute(
          "missionForm",
          new MissionForm("", "", "", "PLANNED", "", "", "", "", "", false, null, null));
    }
    model.addAttribute("isNew", true);
    model.addAttribute(
        "mission",
        new MissionDto(
            null, "", null, null, "PLANNED", null, null, null, null, null, false, null, null, null,
            null, null, null, null, null, null, true, true, null, 0, 0));
    addFormsToModel(model, principal);
    addOperationsToModel(model, false);
    return "mission-detail";
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public String createMission(
      @Valid @ModelAttribute("missionForm") MissionForm form,
      BindingResult bindingResult,
      Model model,
      @AuthenticationPrincipal OidcUser principal,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      // Render the create form directly; BindingResult stays request-scoped.
      return createMissionForm(model, principal);
    }
    try {
      Instant meetingTime =
          (form.meetingTime() != null && !form.meetingTime().isBlank())
              ? parseToInstant(form.meetingTime())
              : null;
      Instant plannedStartTime =
          (form.plannedStartTime() != null && !form.plannedStartTime().isBlank())
              ? parseToInstant(form.plannedStartTime())
              : null;
      Instant plannedEndTime =
          (form.plannedEndTime() != null && !form.plannedEndTime().isBlank())
              ? parseToInstant(form.plannedEndTime())
              : null;

      OperationDto operation =
          (form.operationId() != null && !form.operationId().isBlank())
              ? new OperationDto(UUID.fromString(form.operationId()), null, null, null, null)
              : null;

      MissionDto missionDto =
          new MissionDto(
              null, // id
              form.name(), // name
              form.description(), // description
              form.calendarLink(), // calendarLink
              form.status(), // status
              meetingTime, // meetingTime
              plannedStartTime, // plannedStartTime
              null, // actualStartTime
              plannedEndTime, // plannedEndTime
              null, // actualEndTime
              form.isInternal(), // isInternal
              null, // participants
              null, // assignedUnits
              null, // frequencies
              null, // subMissions
              null, // inventoryEntries
              null, // refineryOrders
              operation, // operation
              null, // owner
              null, // managers
              null, // canEdit
              null, // canManageManagers
              null, // version
              0, // checkedInParticipants
              0 // registeredParticipants
              );

      backendApiClient.post("/api/v1/missions", missionDto, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Create mission failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.mission.create");
      redirectAttributes.addFlashAttribute("missionForm", form);
      return "redirect:/missions/new";
    }
    return "redirect:/missions";
  }

  @PostMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public String updateMission(
      @PathVariable @NotNull UUID id,
      @Valid @ModelAttribute("missionForm") MissionForm form,
      BindingResult bindingResult,
      Model model,
      @AuthenticationPrincipal OidcUser principal,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      return missionDetail(id, model, principal);
    }
    try {
      Instant meetingTime =
          (form.meetingTime() != null && !form.meetingTime().isBlank())
              ? parseToInstant(form.meetingTime())
              : null;
      Instant plannedStartTime =
          (form.plannedStartTime() != null && !form.plannedStartTime().isBlank())
              ? parseToInstant(form.plannedStartTime())
              : null;
      Instant plannedEndTime =
          (form.plannedEndTime() != null && !form.plannedEndTime().isBlank())
              ? parseToInstant(form.plannedEndTime())
              : null;
      Instant actualStartTime =
          (form.actualStartTime() != null && !form.actualStartTime().isBlank())
              ? parseToInstant(form.actualStartTime())
              : null;
      Instant actualEndTime =
          (form.actualEndTime() != null && !form.actualEndTime().isBlank())
              ? parseToInstant(form.actualEndTime())
              : null;

      OperationDto operation =
          (form.operationId() != null && !form.operationId().isBlank())
              ? new OperationDto(UUID.fromString(form.operationId()), null, null, null, null)
              : null;

      MissionDto missionDto =
          new MissionDto(
              id, // id
              form.name(), // name
              form.description(), // description
              form.calendarLink(), // calendarLink
              form.status(), // status
              meetingTime, // meetingTime
              plannedStartTime, // plannedStartTime
              actualStartTime, // actualStartTime
              plannedEndTime, // plannedEndTime
              actualEndTime, // actualEndTime
              form.isInternal(), // isInternal
              null, // participants
              null, // assignedUnits
              null, // frequencies
              null, // subMissions
              null, // inventoryEntries
              null, // refineryOrders
              operation, // operation
              null, // owner
              null, // managers
              null, // canEdit
              null, // canManageManagers
              form.version(), // version
              0, // checkedInParticipants
              0 // registeredParticipants
              );

      backendApiClient.put("/api/v1/missions/" + id, missionDto, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Update mission failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.mission.update");
      redirectAttributes.addFlashAttribute("missionForm", form);
      return "redirect:/missions/" + id;
    }
    return "redirect:/missions/" + id;
  }

  @PostMapping("/{id}/delete")
  @PreAuthorize("hasRole('ADMIN')")
  public String deleteMission(
      @PathVariable @NotNull UUID id, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/missions/" + id, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.mission_delete");
    } catch (Exception e) {
      log.error("Delete mission failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.mission.delete");
      return "redirect:/missions/" + id;
    }
    return "redirect:/missions";
  }

  @PostMapping("/{id}/managers/{userId}")
  @ResponseBody
  public org.springframework.http.ResponseEntity<Void> addManager(
      @PathVariable String id, @PathVariable String userId) {
    log.info("[DEBUG_LOG] START addManager - id: '{}', userId: '{}'", id, userId);
    try {
      if (id == null || id.isBlank() || userId == null || userId.isBlank()) {
        log.error("[DEBUG_LOG] MISSING PARAMETERS - id: '{}', userId: '{}'", id, userId);
        return org.springframework.http.ResponseEntity.badRequest().build();
      }
      java.util.UUID missionUuid;
      java.util.UUID userUuid;
      try {
        missionUuid = java.util.UUID.fromString(id.trim());
      } catch (IllegalArgumentException e) {
        log.error(
            "[DEBUG_LOG] INVALID MISSION ID FORMAT - id: '{}', Error: {}", id, e.getMessage());
        return org.springframework.http.ResponseEntity.badRequest().build();
      }
      try {
        userUuid = java.util.UUID.fromString(userId.trim());
      } catch (IllegalArgumentException e) {
        log.error(
            "[DEBUG_LOG] INVALID USER ID FORMAT - userId: '{}', Error: {}", userId, e.getMessage());
        return org.springframework.http.ResponseEntity.badRequest().build();
      }

      log.info("[DEBUG_LOG] CALLING BACKEND - Mission: {}, User: {}", missionUuid, userUuid);
      try {
        backendApiClient.post(
            "/api/v1/missions/" + missionUuid + "/managers/" + userUuid + "/slim",
            null,
            String.class,
            false);
        log.info("[DEBUG_LOG] SUCCESS - Manager {} added to mission {}", userUuid, missionUuid);
        return org.springframework.http.ResponseEntity.ok().build();
      } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
        log.error(
            "[DEBUG_LOG] BACKEND ERROR adding manager for mission {} and user {}: Status={},"
                + " Message={}, Readable={}",
            missionUuid,
            userUuid,
            e.getStatusCode(),
            e.getMessage(),
            e.getReadableErrorMessage());
        return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
      }
    } catch (Exception e) {
      log.error(
          "[DEBUG_LOG] UNEXPECTED ERROR in addManager: id='{}', userId='{}', error={}",
          id,
          userId,
          e.getMessage(),
          e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  @DeleteMapping("/{id}/managers/{userId}")
  @ResponseBody
  public org.springframework.http.ResponseEntity<Void> removeManager(
      @PathVariable String id, @PathVariable String userId) {
    log.info("[DEBUG_LOG] START removeManager - id: '{}', userId: '{}'", id, userId);
    try {
      if (id == null || id.isBlank() || userId == null || userId.isBlank()) {
        log.error(
            "[DEBUG_LOG] MISSING PARAMETERS in removeManager - id: '{}', userId: '{}'", id, userId);
        return org.springframework.http.ResponseEntity.badRequest().build();
      }
      java.util.UUID missionUuid;
      java.util.UUID userUuid;
      try {
        missionUuid = java.util.UUID.fromString(id.trim());
      } catch (IllegalArgumentException e) {
        log.error(
            "[DEBUG_LOG] INVALID MISSION ID FORMAT in removeManager - id: '{}', Error: {}",
            id,
            e.getMessage());
        return org.springframework.http.ResponseEntity.badRequest().build();
      }
      try {
        userUuid = java.util.UUID.fromString(userId.trim());
      } catch (IllegalArgumentException e) {
        log.error(
            "[DEBUG_LOG] INVALID USER ID FORMAT in removeManager - userId: '{}', Error: {}",
            userId,
            e.getMessage());
        return org.springframework.http.ResponseEntity.badRequest().build();
      }

      log.info("[DEBUG_LOG] CALLING BACKEND DELETE - Mission: {}, User: {}", missionUuid, userUuid);
      backendApiClient.delete(
          "/api/v1/missions/" + missionUuid + "/managers/" + userUuid + "/slim",
          Object.class,
          false);
      log.info(
          "[DEBUG_LOG] SUCCESS DELETE - Manager {} removed from mission {}", userUuid, missionUuid);
      return org.springframework.http.ResponseEntity.ok().build();
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "[DEBUG_LOG] BACKEND ERROR removing manager: Status={}, Message={}, Readable={}",
          e.getStatusCode(),
          e.getMessage(),
          e.getReadableErrorMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error(
          "[DEBUG_LOG] UNEXPECTED ERROR in removeManager: id='{}', userId='{}', error={}",
          id,
          userId,
          e.getMessage(),
          e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  @PutMapping("/{id}/owner/{userId}")
  @ResponseBody
  public org.springframework.http.ResponseEntity<Void> setMissionOwner(
      @PathVariable String id, @PathVariable String userId) {
    log.info("[DEBUG_LOG] START setMissionOwner - id: '{}', userId: '{}'", id, userId);
    try {
      if (id == null || id.isBlank() || userId == null || userId.isBlank()) {
        log.error(
            "[DEBUG_LOG] MISSING PARAMETERS in setMissionOwner - id: '{}', userId: '{}'",
            id,
            userId);
        return org.springframework.http.ResponseEntity.badRequest().build();
      }
      java.util.UUID missionUuid;
      java.util.UUID userUuid;
      try {
        missionUuid = java.util.UUID.fromString(id.trim());
      } catch (IllegalArgumentException e) {
        log.error(
            "[DEBUG_LOG] INVALID MISSION ID FORMAT in setMissionOwner - id: '{}', Error: {}",
            id,
            e.getMessage());
        return org.springframework.http.ResponseEntity.badRequest().build();
      }
      try {
        userUuid = java.util.UUID.fromString(userId.trim());
      } catch (IllegalArgumentException e) {
        log.error(
            "[DEBUG_LOG] INVALID USER ID FORMAT in setMissionOwner - userId: '{}', Error: {}",
            userId,
            e.getMessage());
        return org.springframework.http.ResponseEntity.badRequest().build();
      }

      log.info("[DEBUG_LOG] CALLING BACKEND PUT - Mission: {}, User: {}", missionUuid, userUuid);
      try {
        backendApiClient.put(
            "/api/v1/missions/" + missionUuid + "/owner/" + userUuid, null, Void.class, false);
        log.info(
            "[DEBUG_LOG] SUCCESS - Owner of mission {} changed to user {}", missionUuid, userUuid);
        return org.springframework.http.ResponseEntity.ok().build();
      } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
        log.error(
            "[DEBUG_LOG] BACKEND ERROR changing owner: Status={}, Message={}, Readable={}",
            e.getStatusCode(),
            e.getMessage(),
            e.getReadableErrorMessage());
        return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
      }
    } catch (Exception e) {
      log.error(
          "[DEBUG_LOG] UNEXPECTED ERROR in setMissionOwner: id='{}', userId='{}', error={}",
          id,
          userId,
          e.getMessage(),
          e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  @PostMapping("/{id}/frequencies")
  @PreAuthorize("hasRole('MISSION_MANAGER')")
  public String addOrUpdateFrequency(
      @PathVariable @NotNull UUID id,
      @RequestParam @NotNull UUID frequencyTypeId,
      @RequestParam @NotNull java.math.BigDecimal value,
      RedirectAttributes redirectAttributes) {
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("frequencyTypeId", frequencyTypeId);
      body.put("value", value);

      backendApiClient.post("/api/v1/missions/" + id + "/frequencies/slim", body, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Add or update frequency failed", e);
      return "redirect:/missions/" + id + "?error=error.mission.frequency.update";
    }
    return "redirect:/missions/" + id;
  }

  @PostMapping("/{id}/frequencies/{frequencyId}/delete")
  @PreAuthorize("hasRole('MISSION_MANAGER')")
  public String deleteFrequency(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID frequencyId,
      RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete(
          "/api/v1/missions/" + id + "/frequencies/" + frequencyId + "/slim", Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.delete");
    } catch (Exception e) {
      log.error("Delete frequency failed", e);
      return "redirect:/missions/" + id + "?error=error.mission.frequency.delete";
    }
    return "redirect:/missions/" + id;
  }

  /**
   * AJAX endpoint for Paket 3B: submits a frequency add/update via the Slim backend endpoint and
   * returns the resulting slim list as JSON so that the mission detail page can update the DOM in
   * place without a full reload. Enables concurrent editing of the frequencies sub-panel without
   * forcing other users to re-enter their pending changes (Option A).
   */
  @org.springframework.web.bind.annotation.PutMapping(
      value = "/{id}/frequencies/ajax",
      produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  @PreAuthorize("hasRole('MISSION_MANAGER')")
  public org.springframework.http.ResponseEntity<Object> addOrUpdateFrequencyAjax(
      @PathVariable @NotNull UUID id,
      @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
    try {
      Object result =
          backendApiClient.post(
              "/api/v1/missions/" + id + "/frequencies/slim", body, Object.class, false);
      return org.springframework.http.ResponseEntity.ok(result);
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "[DEBUG_LOG] Add/update frequency (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error("[DEBUG_LOG] UNEXPECTED ERROR in addOrUpdateFrequencyAjax for mission {}", id, e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * AJAX endpoint for Paket 3B: deletes a frequency via the Slim backend endpoint and returns the
   * resulting slim list as JSON.
   */
  @DeleteMapping(
      value = "/{id}/frequencies/{frequencyId}/ajax",
      produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  @PreAuthorize("hasRole('MISSION_MANAGER')")
  public org.springframework.http.ResponseEntity<Object> deleteFrequencyAjax(
      @PathVariable @NotNull UUID id, @PathVariable @NotNull UUID frequencyId) {
    try {
      Object result =
          backendApiClient.delete(
              "/api/v1/missions/" + id + "/frequencies/" + frequencyId + "/slim",
              Object.class,
              false);
      return org.springframework.http.ResponseEntity.ok(result);
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "[DEBUG_LOG] Delete frequency (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error(
          "[DEBUG_LOG] UNEXPECTED ERROR in deleteFrequencyAjax for mission {} freq {}",
          id,
          frequencyId,
          e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * AJAX endpoint for Paket 3C: adds a unit via the Slim backend endpoint and returns the resulting
   * slim unit list so the mission detail page can refresh without losing pending input in other
   * sub-panels (Option A).
   */
  @PostMapping(
      value = "/{id}/units/ajax",
      produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  @PreAuthorize("isAuthenticated()")
  public org.springframework.http.ResponseEntity<Object> addUnitAjax(
      @PathVariable @NotNull UUID id,
      @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
    try {
      Object result =
          backendApiClient.post(
              "/api/v1/missions/" + id + "/units/slim", body, Object.class, false);
      return org.springframework.http.ResponseEntity.ok(result);
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "[DEBUG_LOG] Add unit (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error("[DEBUG_LOG] UNEXPECTED ERROR in addUnitAjax for mission {}", id, e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * AJAX endpoint for Paket 3C: updates a unit via the Slim backend endpoint and returns the
   * updated slim unit as JSON.
   */
  @org.springframework.web.bind.annotation.PutMapping(
      value = "/{id}/units/{unitId}/ajax",
      produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  @PreAuthorize("isAuthenticated()")
  public org.springframework.http.ResponseEntity<Object> updateUnitAjax(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID unitId,
      @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
    try {
      Object result =
          backendApiClient.put(
              "/api/v1/missions/" + id + "/units/" + unitId + "/slim", body, Object.class, false);
      return org.springframework.http.ResponseEntity.ok(result);
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "[DEBUG_LOG] Update unit (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error(
          "[DEBUG_LOG] UNEXPECTED ERROR in updateUnitAjax for mission {} unit {}", id, unitId, e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /** AJAX endpoint for Paket 3C: deletes a unit via the Slim backend endpoint. */
  @DeleteMapping(
      value = "/{id}/units/{unitId}/ajax",
      produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  @PreAuthorize("isAuthenticated()")
  public org.springframework.http.ResponseEntity<Object> deleteUnitAjax(
      @PathVariable @NotNull UUID id, @PathVariable @NotNull UUID unitId) {
    try {
      backendApiClient.delete(
          "/api/v1/missions/" + id + "/units/" + unitId + "/slim", Void.class, false);
      return org.springframework.http.ResponseEntity.noContent().build();
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "[DEBUG_LOG] Delete unit (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error(
          "[DEBUG_LOG] UNEXPECTED ERROR in deleteUnitAjax for mission {} unit {}", id, unitId, e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * AJAX endpoint for Paket 3C (Option b - Participants): adds a participant via the Slim backend
   * endpoint and returns the resulting slim participant list so the mission detail page can refresh
   * without losing pending input in other sub-panels (Option A: sub-section writes must not bump
   * Mission.version).
   */
  @PostMapping(
      value = "/{id}/participants/ajax",
      produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public org.springframework.http.ResponseEntity<Object> addParticipantAjax(
      @PathVariable @NotNull UUID id,
      @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body,
      @AuthenticationPrincipal OidcUser principal) {
    try {
      // Mirror the classical /missions/{id}/participant handler: anonymous guests
      // hit the backend's slim endpoint via the public WebClient (no JWT) so the
      // backend can apply its guest-signup branch (jwt == null + guestName).
      // Previously this method was annotated with @PreAuthorize("isAuthenticated()")
      // and always passed isPublic=false, which produced the AccessDeniedException
      // observed in live-log/log.txt for anonymous mission signups.
      boolean isPublic = (principal == null);
      Object result =
          backendApiClient.post(
              "/api/v1/missions/" + id + "/participants/slim", body, Object.class, isPublic);
      return org.springframework.http.ResponseEntity.ok(result);
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "[DEBUG_LOG] Add participant (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error("[DEBUG_LOG] UNEXPECTED ERROR in addParticipantAjax for mission {}", id, e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * AJAX endpoint for Paket 3C (Option b - Participants): updates a participant via the Slim
   * backend endpoint and returns the updated slim participant as JSON.
   */
  @org.springframework.web.bind.annotation.PutMapping(
      value = "/{id}/participants/{participantId}/ajax",
      produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public org.springframework.http.ResponseEntity<Object> updateParticipantAjax(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body,
      @AuthenticationPrincipal OidcUser principal) {
    try {
      // Anonymous guests are allowed to edit their own guest participant entries
      // (see backend MissionSecurityService#canAccessParticipant: guest entries
      // with user == null are editable). Route via the public WebClient when no
      // OIDC principal is present, mirroring addParticipantAjax.
      boolean isPublic = (principal == null);
      Object result =
          backendApiClient.put(
              "/api/v1/missions/" + id + "/participants/" + participantId + "/slim",
              body,
              Object.class,
              isPublic);
      return org.springframework.http.ResponseEntity.ok(result);
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "[DEBUG_LOG] Update participant (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error(
          "[DEBUG_LOG] UNEXPECTED ERROR in updateParticipantAjax for mission {} participant {}",
          id,
          participantId,
          e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * AJAX endpoint for Paket 3C (Option b - Participants): deletes a participant via the Slim
   * backend endpoint.
   */
  @DeleteMapping(
      value = "/{id}/participants/{participantId}/ajax",
      produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public org.springframework.http.ResponseEntity<Object> deleteParticipantAjax(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @AuthenticationPrincipal OidcUser principal) {
    try {
      boolean isPublic = (principal == null);
      backendApiClient.delete(
          "/api/v1/missions/" + id + "/participants/" + participantId + "/slim",
          Void.class,
          isPublic);
      return org.springframework.http.ResponseEntity.noContent().build();
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "[DEBUG_LOG] Delete participant (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error(
          "[DEBUG_LOG] UNEXPECTED ERROR in deleteParticipantAjax for mission {} participant {}",
          id,
          participantId,
          e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * AJAX endpoint for Paket 3C (Option b - Participants): checks a participant in via the Slim
   * backend endpoint.
   */
  @PostMapping(
      value = "/{id}/participants/{participantId}/check-in/ajax",
      produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public org.springframework.http.ResponseEntity<Object> checkInParticipantAjax(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @AuthenticationPrincipal OidcUser principal) {
    try {
      boolean isPublic = (principal == null);
      Object result =
          backendApiClient.post(
              "/api/v1/missions/" + id + "/participants/" + participantId + "/check-in/slim",
              null,
              Object.class,
              isPublic);
      return org.springframework.http.ResponseEntity.ok(result);
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "[DEBUG_LOG] Check-in participant (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error(
          "[DEBUG_LOG] UNEXPECTED ERROR in checkInParticipantAjax for mission {} participant {}",
          id,
          participantId,
          e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * AJAX endpoint for Paket 3C (Option b - Participants): checks a participant out via the Slim
   * backend endpoint.
   */
  @PostMapping(
      value = "/{id}/participants/{participantId}/check-out/ajax",
      produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public org.springframework.http.ResponseEntity<Object> checkOutParticipantAjax(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID participantId,
      @AuthenticationPrincipal OidcUser principal) {
    try {
      boolean isPublic = (principal == null);
      Object result =
          backendApiClient.post(
              "/api/v1/missions/" + id + "/participants/" + participantId + "/check-out/slim",
              null,
              Object.class,
              isPublic);
      return org.springframework.http.ResponseEntity.ok(result);
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "[DEBUG_LOG] Check-out participant (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error(
          "[DEBUG_LOG] UNEXPECTED ERROR in checkOutParticipantAjax for mission {} participant {}",
          id,
          participantId,
          e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * AJAX endpoint for Paket 3C (Option c - Crew): adds a crew member to a unit via the Slim backend
   * endpoint and returns the resulting slim crew list.
   */
  @PostMapping(
      value = "/{id}/units/{unitId}/crew/ajax",
      produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  @PreAuthorize("isAuthenticated()")
  public org.springframework.http.ResponseEntity<Object> addCrewAjax(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID unitId,
      @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
    try {
      Object result =
          backendApiClient.post(
              "/api/v1/missions/" + id + "/units/" + unitId + "/crew/slim",
              body,
              Object.class,
              false);
      return org.springframework.http.ResponseEntity.ok(result);
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "[DEBUG_LOG] Add crew (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error(
          "[DEBUG_LOG] UNEXPECTED ERROR in addCrewAjax for mission {} unit {}", id, unitId, e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * AJAX endpoint for Paket 3C (Option c - Crew): updates a crew member via the Slim backend
   * endpoint and returns the updated slim crew entry.
   */
  @org.springframework.web.bind.annotation.PutMapping(
      value = "/{id}/units/{unitId}/crew/{crewId}/ajax",
      produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  @PreAuthorize("isAuthenticated()")
  public org.springframework.http.ResponseEntity<Object> updateCrewAjax(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID unitId,
      @PathVariable @NotNull UUID crewId,
      @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
    try {
      Object result =
          backendApiClient.put(
              "/api/v1/missions/" + id + "/units/" + unitId + "/crew/" + crewId + "/slim",
              body,
              Object.class,
              false);
      return org.springframework.http.ResponseEntity.ok(result);
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "[DEBUG_LOG] Update crew (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error(
          "[DEBUG_LOG] UNEXPECTED ERROR in updateCrewAjax for mission {} unit {} crew {}",
          id,
          unitId,
          crewId,
          e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * AJAX endpoint for Paket 3C (Option c - Crew): deletes a crew member via the Slim backend
   * endpoint.
   */
  @DeleteMapping(
      value = "/{id}/units/{unitId}/crew/{crewId}/ajax",
      produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  @PreAuthorize("isAuthenticated()")
  public org.springframework.http.ResponseEntity<Object> deleteCrewAjax(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID unitId,
      @PathVariable @NotNull UUID crewId) {
    try {
      backendApiClient.delete(
          "/api/v1/missions/" + id + "/units/" + unitId + "/crew/" + crewId + "/slim",
          Void.class,
          false);
      return org.springframework.http.ResponseEntity.noContent().build();
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "[DEBUG_LOG] Delete crew (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error(
          "[DEBUG_LOG] UNEXPECTED ERROR in deleteCrewAjax for mission {} unit {} crew {}",
          id,
          unitId,
          crewId,
          e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * AJAX endpoint: returns all participants of a mission that are not yet assigned to any unit
   * crew. Used to populate the "Crew zuweisen" dropdown with only unassigned participants.
   */
  @GetMapping(
      value = "/{id}/participants/unassigned/ajax",
      produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  @PreAuthorize("isAuthenticated()")
  public org.springframework.http.ResponseEntity<Object> getUnassignedParticipantsAjax(
      @PathVariable @NotNull UUID id) {
    try {
      Object result =
          backendApiClient.get(
              "/api/v1/missions/" + id + "/participants/unassigned",
              new ParameterizedTypeReference<Object>() {},
              false);
      return org.springframework.http.ResponseEntity.ok(result);
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "[DEBUG_LOG] Get unassigned participants (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error(
          "[DEBUG_LOG] UNEXPECTED ERROR in getUnassignedParticipantsAjax for mission {}", id, e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  private String extractParticipantName(MissionParticipantDto participant) {
    if (participant == null) {
      return "";
    }
    if (participant.user() != null) {
      if (participant.user().effectiveName() != null
          && !participant.user().effectiveName().isBlank()) {
        return participant.user().effectiveName();
      }
      if (participant.user().displayName() != null && !participant.user().displayName().isBlank()) {
        return participant.user().displayName();
      }
      if (participant.user().username() != null && !participant.user().username().isBlank()) {
        return participant.user().username();
      }
    }
    return participant.guestName() != null ? participant.guestName() : "";
  }

  private String fetchRoundingMode(boolean isPublic) {
    try {
      Map<String, Object> setting =
          backendApiClient.get(
              "/api/v1/settings/refinery.rounding.mode",
              new ParameterizedTypeReference<Map<String, Object>>() {},
              isPublic);
      if (setting != null && setting.get("value") != null) {
        return String.valueOf(setting.get("value"));
      }
    } catch (Exception e) {
      log.warn("Failed to fetch refinery rounding mode, using default UP");
    }
    return "UP";
  }

  private java.time.Instant parseToInstant(String dateTimeStr) {
    if (dateTimeStr == null || dateTimeStr.isBlank()) {
      return null;
    }
    try {
      if (dateTimeStr.endsWith("Z")) {
        return java.time.Instant.parse(dateTimeStr);
      }
      if (dateTimeStr.length() == 19) { // YYYY-MM-DDThh:mm:ss
        java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(dateTimeStr);
        return ldt.atZone(java.time.ZoneId.of("Europe/Berlin")).toInstant();
      }
      if (dateTimeStr.length() == 16) { // YYYY-MM-DDThh:mm
        java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(dateTimeStr);
        return ldt.atZone(java.time.ZoneId.of("Europe/Berlin")).toInstant();
      }
      return java.time.Instant.parse(dateTimeStr);
    } catch (Exception e) {
      log.warn("Failed to parse datetime string: {}", dateTimeStr, e);
      return null;
    }
  }

  private String formatInstant(Object instantObj) {
    if (instantObj == null) {
      return "";
    }
    try {
      java.time.Instant instant;
      if (instantObj instanceof java.time.Instant i) {
        instant = i;
      } else if (instantObj instanceof String s) {
        if (s.isBlank()) {
          return "";
        }
        instant = java.time.Instant.parse(s);
      } else {
        return String.valueOf(instantObj);
      }
      java.time.ZonedDateTime zdt = instant.atZone(java.time.ZoneId.of("Europe/Berlin"));
      return zdt.toLocalDateTime().toString();
    } catch (Exception e) {
      log.warn("Failed to format instant: {}", instantObj);
      return String.valueOf(instantObj);
    }
  }
}

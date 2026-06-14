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

package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.CreateMissionRequest;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobTypeDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionCrewDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionFinanceEntryDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionFrequencyDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionListDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionUnitDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OperationDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OperationReferenceDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OrgUnitMembershipOptionDto;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.context.MessageSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
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

/**
 * Spring MVC controller for the mission pages ({@code /missions}, {@code /missions/{id}}, {@code
 * /missions/create}, plus a large surface of mutators for participants, units, crew, managers,
 * frequencies and the assorted AJAX variants).
 *
 * <p>By far the largest controller in the project; missions are the central operational entity and
 * the detail page serves as the squadron's single coordination surface. The controller exposes both
 * classic form-post endpoints (with a flash-redirect dance) and AJAX {@code @ResponseBody}
 * endpoints that mutate the same resources in place — the page uses the AJAX path for in-grid edits
 * and the form-post path for full-page form submits (modals). Public paths ({@code
 * addParticipant}/{@code checkIn}/{@code checkOut}/{@code updatePayoutPreference} etc.) accept
 * unauthenticated callers so guests can join missions; the backend then strips sensitive fields
 * from the response via the {@code cleanupForGuest} helper.
 */
@Controller
@RequestMapping("/missions")
@RequiredArgsConstructor
@Slf4j
public class MissionPageController {

  private final BackendApiClient backendApiClient;

  /**
   * Resolves Jakarta {@code @Valid} field-error messages for the {@link #updateMissionAjax} twin's
   * {@code {field: message}} JSON contract, using the same {@code
   * messageSource.getMessage(fieldError, locale)} resolution Thymeleaf's {@code th:errors} performs
   * — so an inline validation message is byte-identical to the classic full-page re-render and the
   * well-tested validation UX is preserved.
   */
  private final MessageSource messageSource;

  /**
   * Frontend mirror of the backend's "registered member or above" role set. A caller holding none
   * of these — anonymous OR an authenticated but role-less {@code GUEST} — is a mission outsider
   * and is treated like an anonymous visitor on the mission surface: the backend already redacts
   * the mission payload for them, and the page additionally skips the member-only fetches (finance
   * and refinery ledger) so the "Finanzen" panel collapses for a guest exactly as it does for an
   * anonymous visitor.
   */
  private static final Set<String> MEMBER_ROLES =
      Set.of(
          "ROLE_ADMIN",
          "ROLE_OFFICER",
          "ROLE_MISSION_MANAGER",
          "ROLE_LOGISTICIAN",
          "ROLE_SQUADRON_MEMBER",
          "ROLE_MEMBER");

  /**
   * {@code true} when the OIDC principal carries at least one {@link #MEMBER_ROLES} authority, i.e.
   * is a registered organisation member or above. Returns {@code false} for {@code null}
   * (anonymous) and for an authenticated but role-less {@code GUEST}. Used to gate the member-only
   * mission-detail fetches so a guest is treated like an anonymous visitor.
   *
   * @param principal the authenticated OIDC user, or {@code null} for anonymous callers
   * @return whether the caller is a registered member or above
   */
  private boolean isMemberOrAbove(OidcUser principal) {
    return principal != null
        && principal.getAuthorities().stream()
            .anyMatch(a -> MEMBER_ROLES.contains(a.getAuthority()));
  }

  private void addOperationsToModel(Model model, boolean isPublic) {
    if (isPublic) {
      model.addAttribute("operationsList", List.of());
      return;
    }
    try {
      List<OperationReferenceDto> operations =
          backendApiClient.get(
              "/api/v1/operations/lookup",
              new ParameterizedTypeReference<List<OperationReferenceDto>>() {},
              false);
      model.addAttribute("operationsList", operations);
    } catch (Exception e) {
      log.warn("Could not load operations", e);
      model.addAttribute("operationsList", List.of());
    }
  }

  /**
   * Seeds the various form-backing objects the mission-detail template needs (participant, crew,
   * unit, finance, manager, etc.) when they are not already present in the model. Authenticated
   * callers additionally get their own user record stuffed into the participant form so the "join
   * as me" default works without an extra fetch in the template.
   *
   * @param model Thymeleaf model populated with the seeded forms
   * @param principal authenticated OIDC user, or {@code null} for guests
   */
  public void addFormsToModel(Model model, OidcUser principal) {
    if (!model.containsAttribute("participantForm")) {
      ParticipantForm form =
          new ParticipantForm(null, "", null, null, "", List.of(), null, null, null, null);
      if (principal != null) {
        try {
          UserDto me = backendApiClient.get("/api/v1/users/me", UserDto.class);
          if (me != null) {
            String name =
                (me.displayName() != null && !me.displayName().isBlank())
                    ? me.displayName()
                    : me.username();
            // Org-unit affiliations for a registered participant are derived server-side from the
            // caller's memberships, so the form carries no org-unit prefill — the picker is
            // guest-only and hidden once a registered user is selected.
            form =
                new ParticipantForm(
                    me.id(), name, null, null, "", List.of(), null, null, null, null);
          }
        } catch (Exception e) {
          log.warn("Could not prefill participant form", e);
        }
      }
      model.addAttribute("participantForm", form);
    }
    if (!model.containsAttribute("unitForm")) {
      model.addAttribute("unitForm", new UnitForm("", null, null, false, null, null, null));
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

  /**
   * Web binder configuration scoped to this controller. Registers any custom property editors
   * needed for the mission forms (currently only the inherited default editors).
   *
   * @param binder Spring data binder for the current request
   */
  @InitBinder
  public void initBinder(WebDataBinder binder) {
    binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
  }

  /**
   * Renders the mission list ({@code /missions}). Public endpoint — guests see the full upcoming
   * mission catalog with sensitive fields stripped by the backend; authenticated callers see the
   * full record. Pagination + sort follow the standard URL-driven pattern.
   *
   * @return the {@code missions-index} view name
   */
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
    // Mission overview default: newest planned start at the top (the mission furthest in the
    // future, descending by plannedStartTime). The backend API default for /api/v1/missions/search
    // is ASC via PaginationUtil; the missions page deliberately overrides that. PaginationUtil
    // already appends `id` as a stable tiebreaker for equal plannedStartTime values, so this
    // covers the deterministic ordering contract.
    uri.append("sort=plannedStartTime,desc&");

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

  /**
   * Renders the mission-detail page ({@code /missions/{id}}). Loads the mission, the finance
   * entries, the unit/crew/participant hierarchy, the manager list and the frequencies. The heavy
   * {@code addFormsToModel} call seeds every form-backing object the template needs for the inline
   * modals so the same controller method serves both fresh renders and post-flash re-renders after
   * a validation failure.
   *
   * <p>When {@code fragment} is set the same fully populated model is rendered through a single
   * Thymeleaf fragment instead of the whole page, so an in-place AJAX swap (epic #571) can
   * re-render one section after a sub-mutation: {@code crew-board} → the crew board, {@code
   * finance} → the finance &amp; payout pane, {@code mgmt} → the owner/manager management panel.
   * The full model is still built for every fragment value, so the fragment never references a
   * missing attribute.
   *
   * @param id the mission id
   * @param model the Spring MVC model populated with the mission aggregate and form backers
   * @param principal the authenticated user, or {@code null} for an anonymous/guest visitor
   * @param fragment the optional section key selecting an in-place fragment render
   * @return the {@code mission-detail} view name, or a {@code mission-detail :: <fragment>}
   *     selector
   */
  @GetMapping("/{id}")
  public String missionDetail(
      @PathVariable @NotNull UUID id,
      Model model,
      @AuthenticationPrincipal OidcUser principal,
      @RequestParam(required = false) String fragment) {
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

      // User ids of every account-backed participant (guests have no account and thus no hangar
      // ships). The unit ADD modal offers only ships owned by these users; the EDIT modal also
      // keeps already-assigned ships (see assignedUnitShipIds) so a unit can only be crewed with a
      // ship brought by someone registered for the mission, without dropping an existing one.
      java.util.Set<UUID> participantUserIds = new java.util.HashSet<>();
      for (MissionParticipantDto p : participants) {
        if (p.user() != null && p.user().id() != null) {
          participantUserIds.add(p.user().id());
        }
      }
      model.addAttribute("participantUserIds", participantUserIds);

      // Sort crew members and build groupings
      Map<UUID, String> assignedUnitByParticipantId = new java.util.HashMap<>();
      // Ids of ships already pinned to a unit of this mission. The unit EDIT modal keeps offering
      // these even when the owner is no longer a participant, so editing an unrelated field on such
      // a unit doesn't silently drop the ship — the client-side picker pre-selects the current ship
      // by value and needs the <option> to exist.
      java.util.Set<UUID> assignedUnitShipIds = new java.util.HashSet<>();
      if (mission.assignedUnits() != null) {
        for (MissionUnitDto unit : mission.assignedUnits()) {
          if (unit.ship() != null && unit.ship().id() != null) {
            assignedUnitShipIds.add(unit.ship().id());
          }
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
      model.addAttribute("assignedUnitShipIds", assignedUnitShipIds);

      // "Crew zuweisen"-Dropdown zeigt nur Teilnehmer, die noch keiner Einheit zugewiesen sind.
      // Sortierung wird aus `participants` (oben bereits alphabetisch nach extractParticipantName)
      // geerbt — ein assignment-Status-Filter ändert die Reihenfolge der verbleibenden Einträge
      // nicht. Der server-seitige Filter ist authoritativ; nach einer Crew-Zuweisung lädt der
      // AJAX-Pfad die ganze Seite neu, sodass das Dropdown auf dem aktuellen Stand bleibt.
      List<MissionParticipantDto> unassignedParticipants =
          participants.stream()
              .filter(p -> p.id() != null && !assignedUnitByParticipantId.containsKey(p.id()))
              .toList();
      model.addAttribute("unassignedParticipants", unassignedParticipants);

      // Crew board (tab layout, Variante B): unit crew lists carry only
      // participantId/participantName, so the board's person rows resolve the full participant
      // payload (org units, desired job, comment, check-in state, version) via this id lookup.
      Map<UUID, MissionParticipantDto> participantsById = new java.util.HashMap<>();
      for (MissionParticipantDto p : participants) {
        if (p.id() != null) {
          participantsById.put(p.id(), p);
        }
      }
      model.addAttribute("participantsById", participantsById);

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
          java.time.Instant participantStart = p.startTime();
          java.time.Instant participantEnd = p.endTime();

          if (participantStart != null) {
            java.time.Instant effectiveStart =
                participantStart.isBefore(missionStart) ? missionStart : participantStart;
            java.time.Instant effectiveEnd;
            if (participantEnd != null) {
              effectiveEnd =
                  (missionEnd != null && participantEnd.isAfter(missionEnd))
                      ? missionEnd
                      : participantEnd;
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
                mission.version(),
                mission.coreVersion(),
                mission.scheduleVersion(),
                mission.flagsVersion(),
                // Edit path: owningOrgUnitId is not editable, the existing stamp survives.
                null));
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

      // Fetch all active org units (Staffel + Spezialkommandos) for the guest org-unit picker in
      // the participant add/edit modals. The backend endpoint requires a role, and an anonymous
      // guest's submitted org units are dropped server-side (H-3) anyway, so the picker is only
      // populated for authenticated callers labeling a guest.
      if (principal != null) {
        try {
          List<OrgUnitMembershipOptionDto> orgUnits =
              backendApiClient.get(
                  "/api/v1/org-units/active", new ParameterizedTypeReference<>() {});
          model.addAttribute("orgUnits", orgUnits != null ? orgUnits : List.of());
        } catch (Exception e) {
          model.addAttribute("orgUnits", List.of());
        }
      } else {
        model.addAttribute("orgUnits", List.of());
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
        // Unit ship pickers are populated from the mission-scoped endpoint, not the caller's
        // OrgUnit-scoped hangar: it returns ships of registered participants (any OrgUnit) plus
        // ships already assigned to a unit. Only fetched when the caller may edit the mission —
        // otherwise the modals don't render and the endpoint would 403.
        Boolean canEdit = mission.canEdit();
        if (canEdit != null && canEdit) {
          try {
            List<ShipDto> unitShipOptions =
                backendApiClient.get(
                    "/api/v1/missions/" + id + "/unit-ship-options",
                    new ParameterizedTypeReference<List<ShipDto>>() {},
                    false);
            model.addAttribute("unitShipOptions", unitShipOptions);
          } catch (Exception e) {
            // Ignore, e.g. if the caller cannot manage the mission
          }
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

      // Fetch Finance Entries and Refinery Orders — member-only (the finance ledger is the
      // mission's payout view). A guest is treated like an anonymous visitor here: the backend
      // would reject these reads with 403 anyway, and skipping them keeps the "Finanzen" panel
      // empty/collapsed instead of leaking refinery expenses through the shared finance table.
      if (isMemberOrAbove(principal)) {
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

          // Finance tab summary strip (Gesamtsumme / Einnahmen / Ausgaben / je Anteil): income
          // and expense buckets aggregate the manual ledger entries; refinery-order expenses are
          // the automatic "auto" rows and therefore count into the expense bucket as well.
          java.math.BigDecimal incomeSum = java.math.BigDecimal.ZERO;
          java.math.BigDecimal expenseSum = java.math.BigDecimal.ZERO;
          int incomeCount = 0;
          int expenseCount = 0;
          for (MissionFinanceEntryDto entry : financesPage.content()) {
            if (entry.type() != null && "INCOME".equals(entry.type().name())) {
              incomeSum = incomeSum.add(entry.amount());
              incomeCount++;
            } else if (entry.type() != null) {
              expenseSum = expenseSum.add(entry.amount());
              expenseCount++;
            }
          }
          if (refineryOrders != null) {
            for (RefineryOrderListDto order : refineryOrders) {
              if (order.expenses() != null && order.expenses() > 0) {
                expenseSum = expenseSum.add(java.math.BigDecimal.valueOf(order.expenses()));
                expenseCount++;
              }
            }
          }
          model.addAttribute("financeIncomeSum", incomeSum);
          model.addAttribute("financeExpenseSum", expenseSum);
          model.addAttribute("financeIncomeCount", incomeCount);
          model.addAttribute("financeExpenseCount", expenseCount);
          Integer registered = mission.registeredParticipants();
          model.addAttribute(
              "financePerShare",
              (financeSum != null && registered != null && registered > 0)
                  ? financeSum.divide(
                      java.math.BigDecimal.valueOf(registered), 0, java.math.RoundingMode.HALF_UP)
                  : null);
        } catch (Exception e) {
          log.error("Error loading finance entries or refinery orders", e);
        }
      }

    } catch (Exception e) {
      log.error("Error loading mission details", e);
      if (fragment != null) {
        // In-place fragment path (#571/#574): a redirect here would be followed by
        // krtFetch.swap and the whole /missions page painted into the small section
        // container. Answer with a section-sized inline error fragment instead — the swap
        // renders it in place. (An expired-session login redirect happens in the security
        // filter before this controller; krtFetch.swap catches that via res.redirected.)
        return "mission-detail :: fragmentError";
      }
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
    // In-place AJAX swap (epic #571): re-render only the section the caller mutated. The full model
    // is already built above, so each fragment renders with every attribute it needs.
    if (fragment != null) {
      return switch (fragment.toLowerCase(java.util.Locale.ROOT)) {
        case "crew-board" -> "mission-detail :: crewBoard";
        case "finance" -> "mission-detail :: financeSection";
        case "mgmt" -> "mission-detail :: mgmtPanels";
        default -> "mission-detail";
      };
    }
    return "mission-detail";
  }

  /**
   * Form-post endpoint that adds a participant to a mission. Public endpoint — anyone can join,
   * authenticated or not (a guest provides a handle, an authenticated user is auto-resolved).
   *
   * @return redirect to {@code /missions/{id}}
   */
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
      return missionDetail(id, model, principal, null);
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
      if (form.orgUnitIds() != null && !form.orgUnitIds().isEmpty()) {
        body.put("orgUnitIds", form.orgUnitIds());
      }
      if (form.payoutPreference() != null) {
        body.put("payoutPreference", form.payoutPreference().name());
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

  /**
   * Form-post endpoint that assigns or clears a mission's party lead (Partyleiter). Reuses the same
   * resolution mechanic as participant-add: the autocomplete fills the hidden {@code userId} when a
   * registered member is picked, otherwise the free-text {@code guestName} is submitted and
   * resolved server-side (a unique member match is linked, an unknown name is kept as a guest
   * handle). An empty submission clears the party lead. {@code version} carries the mission's
   * current {@code partyLeadVersion} for optimistic-lock validation. A full reload follows so the
   * freshly bumped version is re-rendered without manual DOM version sync.
   *
   * @param id mission id
   * @param userId resolved registered-user id from the autocomplete, or {@code null}
   * @param guestName free-text party-lead handle, or {@code null}
   * @param version expected {@code partyLeadVersion} echoed back from the rendered page
   * @param redirectAttributes flash-scoped toast carrier
   * @return redirect to {@code /missions/{id}}
   */
  @PostMapping("/{id}/party-lead")
  @PreAuthorize("isAuthenticated()")
  public String setPartyLead(
      @PathVariable @NotNull UUID id,
      @RequestParam(required = false) UUID userId,
      @RequestParam(required = false) String guestName,
      @RequestParam(required = false) Long version,
      RedirectAttributes redirectAttributes) {
    try {
      Map<String, Object> body = new HashMap<>();
      if (userId != null) {
        body.put("userId", userId);
      }
      if (guestName != null && !guestName.isBlank()) {
        body.put("guestName", guestName);
      }
      body.put("version", version != null ? version : 0L);
      backendApiClient.put("/api/v1/missions/" + id + "/party-lead", body, Void.class, false);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error("Set party lead failed with status {}: {}", e.getStatusCode(), e.getMessage());
      // 409 = either an ambiguous free-text name (matches more than one member) or a stale
      // partyLeadVersion (someone else changed it meanwhile); a single conflict toast covers both
      // and the reload below shows the current value.
      String toastKey =
          (e.getStatusCode() == 409)
              ? "error.mission.party_lead.conflict"
              : "error.mission.party_lead.update";
      redirectAttributes.addFlashAttribute("errorToast", toastKey);
    } catch (Exception e) {
      log.error("Set party lead failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.mission.party_lead.update");
    }
    return "redirect:/missions/" + id;
  }

  /**
   * AJAX variant of {@link #setPartyLead}: assigns or clears the party lead and returns the
   * refreshed mission as JSON so the mission-detail page can patch the party-lead display + bumped
   * {@code partyLeadVersion} in place without a full reload (#574). The classic form-POST above
   * stays the no-JavaScript fallback; the 409 (ambiguous name / stale version) is passed through as
   * RFC 7807 so the shared {@code krtFetch} conflict UX fires.
   *
   * @param id mission id (path)
   * @param body party-lead JSON ({@code userId} and/or {@code guestName}, plus {@code version}); an
   *     empty {@code userId}+{@code guestName} clears the lead
   * @return {@code 200} with the refreshed mission, or the upstream RFC 7807 error passed through
   */
  @PutMapping(
      value = "/{id}/party-lead/ajax",
      produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  @PreAuthorize("isAuthenticated()")
  public org.springframework.http.ResponseEntity<Object> setPartyLeadAjax(
      @PathVariable @NotNull UUID id, @RequestBody Map<String, Object> body) {
    try {
      Map<String, Object> out = new HashMap<>();
      Object userId = body.get("userId");
      if (userId != null && !String.valueOf(userId).isBlank()) {
        out.put("userId", userId);
      }
      Object guestName = body.get("guestName");
      if (guestName != null && !String.valueOf(guestName).isBlank()) {
        out.put("guestName", guestName);
      }
      out.put("version", body.get("version") != null ? body.get("version") : 0L);
      backendApiClient.put("/api/v1/missions/" + id + "/party-lead", out, Void.class, false);
      MissionDto mission =
          backendApiClient.get(
              "/api/v1/missions/" + id, new ParameterizedTypeReference<MissionDto>() {}, false);
      return org.springframework.http.ResponseEntity.ok(mission);
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.debug("Set party lead (AJAX) failed: status={}", e.getStatusCode());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.debug("UNEXPECTED ERROR in setPartyLeadAjax for mission {}", id, e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Form-post endpoint that marks a participant as checked in. Public for the guest-flow.
   *
   * @return redirect to {@code /missions/{id}}
   */
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

  /**
   * Form-post endpoint that marks a participant as checked out.
   *
   * @return redirect to {@code /missions/{id}}
   */
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

  /**
   * AJAX endpoint that updates a participant's payout preference (target wallet / split rules).
   * Public so guests can change their own preference on missions they joined unauthenticated.
   *
   * @return the updated mission, or the propagated backend status on failure
   */
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
   * immediately. The client sends the current schedule-section counter ({@code scheduleVersion})
   * from the DOM; this engages optimistic locking on the dedicated schedule section, which means a
   * concurrent edit of the core or flags section never triggers a 409 here (and vice versa).
   *
   * <p>The endpoint fetches the current schedule values from the backend, overlays the single field
   * the client requested ({@code actualStartTime} or {@code actualEndTime}) and dispatches a single
   * {@code PATCH /api/v1/missions/{id}/schedule}.
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

      Map<String, Object> schedulePatch = new java.util.LinkedHashMap<>();
      schedulePatch.put("meetingTime", current.meetingTime());
      schedulePatch.put("plannedStartTime", current.plannedStartTime());
      schedulePatch.put("plannedEndTime", current.plannedEndTime());
      schedulePatch.put("actualStartTime", newStart);
      schedulePatch.put("actualEndTime", newEnd);
      schedulePatch.put("version", request.version()); // scheduleVersion from the DOM

      backendApiClient.patch("/api/v1/missions/" + id + "/schedule", schedulePatch, Void.class);
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

  /**
   * Form-post endpoint that removes a participant from a mission.
   *
   * @return redirect to {@code /missions/{id}}
   */
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

  /**
   * Form-post endpoint that edits a participant's metadata (job type, ship type, etc.).
   *
   * @return redirect to {@code /missions/{id}}
   */
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
      return missionDetail(id, model, principal, null);
    }
    try {
      Map<String, Object> body = new HashMap<>();
      if (form.desiredJobTypeId() != null) {
        body.put("desiredMissionJobTypeId", form.desiredJobTypeId());
      }
      if (form.plannedMissionJobTypeId() != null) {
        body.put("plannedMissionJobTypeId", form.plannedMissionJobTypeId());
      }
      if (form.orgUnitIds() != null) {
        body.put("orgUnitIds", form.orgUnitIds());
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

  /**
   * Form-post endpoint that adds a unit (team grouping) to the mission.
   *
   * @return redirect to {@code /missions/{id}}
   */
  @PostMapping("/{id}/units")
  @PreAuthorize("isAuthenticated()")
  public String addUnit(
      @PathVariable @NotNull UUID id,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) UUID shipTypeId,
      @RequestParam(required = false) UUID shipId,
      @RequestParam(required = false, defaultValue = "false") boolean highValueUnit,
      @RequestParam(required = false) Double frequency,
      @RequestParam(required = false) UUID responsibleUserId,
      @RequestParam(required = false) String note,
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
      body.put("responsibleUserId", responsibleUserId);
      body.put("note", note);

      backendApiClient.post("/api/v1/missions/" + id + "/units", body, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Add unit failed", e);
      return "redirect:/missions/" + id + "?error=error.mission.unit.add";
    }
    return "redirect:/missions/" + id;
  }

  /**
   * Form-post endpoint that edits a unit's metadata.
   *
   * @return redirect to {@code /missions/{id}}
   */
  @PostMapping("/{id}/units/{unitId}/update")
  @PreAuthorize("isAuthenticated()")
  public String updateUnit(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID unitId,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) UUID shipTypeId,
      @RequestParam(required = false) UUID shipId,
      @RequestParam(required = false, defaultValue = "false") boolean highValueUnit,
      @RequestParam(required = false) Double frequency,
      @RequestParam(required = false) UUID responsibleUserId,
      @RequestParam(required = false) String note,
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
      body.put("responsibleUserId", responsibleUserId);
      body.put("note", note);

      backendApiClient.put("/api/v1/missions/" + id + "/units/" + unitId, body, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Update unit failed", e);
      return "redirect:/missions/" + id + "?error=error.mission.unit.update";
    }
    return "redirect:/missions/" + id;
  }

  /**
   * Form-post endpoint that removes a unit from the mission. The backend reassigns any participants
   * in the deleted unit to the default unit so no participant becomes orphaned.
   *
   * @return redirect to {@code /missions/{id}}
   */
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

  /**
   * Form-post endpoint that creates a crew (ship-grouping) under a unit.
   *
   * @return redirect to {@code /missions/{id}}
   */
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
      return missionDetail(id, model, principal, null);
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

  /**
   * Form-post endpoint that edits a crew (ship choice, lead participant, etc.).
   *
   * @return redirect to {@code /missions/{id}}
   */
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
      return missionDetail(id, model, principal, null);
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

  /**
   * Form-post endpoint that removes a crew. Participants assigned to the removed crew fall back to
   * the unit's default slot.
   *
   * @return redirect to {@code /missions/{id}}
   */
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

  /**
   * Renders the mission create form ({@code /missions/create}). Seeds the empty form plus the
   * reference catalogs (operations, job types, locations) so the dropdowns work.
   *
   * @param model Thymeleaf model populated with the form and reference catalogs
   * @param principal authenticated OIDC user
   * @return the {@code mission-create} view name
   */
  @GetMapping("/new")
  @PreAuthorize("isAuthenticated()")
  public String createMissionForm(Model model, @AuthenticationPrincipal OidcUser principal) {
    if (!model.containsAttribute("missionForm")) {
      model.addAttribute(
          "missionForm",
          new MissionForm(
              "", "", "", "PLANNED", "", "", "", "", "", false, null, null, null, null, null,
              null));
    }
    model.addAttribute("isNew", true);
    model.addAttribute(
        "mission",
        new MissionDto(
            null, "", null, null, "PLANNED", null, null, null, null, null, false, null, null, null,
            null, null, null, null, null, null, true, true, null, null, null, null, 0, 0, null,
            null, null, 0L));
    addFormsToModel(model, principal);
    addOperationsToModel(model, false);
    model.addAttribute("ownerOptions", fetchCallerMembershipOptions(principal));
    return "mission-detail";
  }

  /**
   * Fetches the {@link OrgUnitMembershipOptionDto} list that drives the R5.d.d owner-picker on the
   * mission-create form. Mission creation has no explicit owner selector — the caller is the
   * implicit owner — so the picker reflects the caller's own memberships, not a separately-chosen
   * owner's. Falls back to an empty list when the lookup fails (the fragment collapses to a hidden
   * state for an empty option list).
   *
   * @param principal authenticated OIDC user; the membership lookup uses {@code /api/v1/users/me}
   *     to resolve the caller's id without forcing the JWT subject through a UUID parse.
   * @return picker options or empty list; never {@code null}.
   */
  private List<OrgUnitMembershipOptionDto> fetchCallerMembershipOptions(OidcUser principal) {
    if (principal == null) {
      return List.of();
    }
    try {
      UserDto me = backendApiClient.get("/api/v1/users/me", UserDto.class);
      if (me == null || me.id() == null) {
        return List.of();
      }
      List<OrgUnitMembershipOptionDto> options =
          backendApiClient.get(
              "/api/v1/users/" + me.id() + "/memberships", new ParameterizedTypeReference<>() {});
      return options != null ? options : List.of();
    } catch (Exception e) {
      log.warn("Failed to fetch memberships for mission-create owner-picker", e);
      return List.of();
    }
  }

  /**
   * Form-post endpoint that persists a new mission. Validation failures re-render the create form
   * inline (BindingResult stays request-scoped). The page hands the freshly-created mission's id
   * back via a redirect to its detail page on success.
   *
   * @return inline create view on failure, otherwise redirect to {@code /missions/{newId}}
   */
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
              ? new OperationDto(
                  UUID.fromString(form.operationId()),
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null)
              : null;

      CreateMissionRequest createRequest =
          new CreateMissionRequest(
              form.name(),
              form.description(),
              form.calendarLink(),
              form.status(),
              meetingTime,
              plannedStartTime,
              plannedEndTime,
              form.isInternal(),
              operation != null ? operation.id() : null,
              form.owningOrgUnitId());

      backendApiClient.post("/api/v1/missions", createRequest, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Create mission failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.mission.create");
      redirectAttributes.addFlashAttribute("missionForm", form);
      return "redirect:/missions/new";
    }
    return "redirect:/missions";
  }

  /**
   * Classic form-post endpoint that persists edits to a mission ({@code !isNew}). On a Jakarta
   * {@code @Valid} failure it re-renders the whole detail page with inline {@code th:errors}; on
   * success it fans the form out into the three section PATCHes via {@link #applyMissionUpdate} and
   * flash-redirects. Stays the no-JavaScript fallback for {@link #updateMissionAjax} (#589).
   *
   * @return redirect to the mission detail page
   */
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
      return missionDetail(id, model, principal, null);
    }
    try {
      applyMissionUpdate(id, form);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Update mission failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.mission.update");
      redirectAttributes.addFlashAttribute("missionForm", form);
      return "redirect:/missions/" + id;
    }
    return "redirect:/missions/" + id;
  }

  /**
   * Fans a validated {@link MissionForm} out into the three section-scoped PATCHes (schedule → core
   * → flags), each carrying its own optimistic-lock counter ({@code scheduleVersion} / {@code
   * coreVersion} / {@code flagsVersion}) so concurrent editors of other sections don't invalidate
   * each other's saves. Shared by the classic {@link #updateMission} and its AJAX twin {@link
   * #updateMissionAjax} so the two can never drift.
   *
   * <p>Schedule is patched first because the status-driven PLANNED → ACTIVE auto-transition in the
   * core patch additionally bumps the schedule version — running schedule first lets the caller's
   * plain time edits land before the auto-stamp kicks in, avoiding an internal 409. Because that
   * auto-bump leaves {@code scheduleVersion} stale on the caller's side, the AJAX twin re-reads the
   * mission afterwards to return the four fresh versions.
   *
   * @param id the mission id
   * @param form the validated submitted form
   */
  private void applyMissionUpdate(@NotNull UUID id, MissionForm form) {
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

    Map<String, Object> schedulePatch = new java.util.LinkedHashMap<>();
    schedulePatch.put("meetingTime", meetingTime);
    schedulePatch.put("plannedStartTime", plannedStartTime);
    schedulePatch.put("plannedEndTime", plannedEndTime);
    schedulePatch.put("actualStartTime", actualStartTime);
    schedulePatch.put("actualEndTime", actualEndTime);
    schedulePatch.put("version", form.scheduleVersion());
    backendApiClient.patch("/api/v1/missions/" + id + "/schedule", schedulePatch, Void.class);

    UUID operationId =
        (form.operationId() != null && !form.operationId().isBlank())
            ? UUID.fromString(form.operationId())
            : null;
    Map<String, Object> corePatch = new java.util.LinkedHashMap<>();
    corePatch.put("name", form.name());
    corePatch.put("description", form.description());
    corePatch.put("calendarLink", form.calendarLink());
    corePatch.put("status", form.status());
    corePatch.put("operationId", operationId);
    corePatch.put("version", form.coreVersion());
    backendApiClient.patch("/api/v1/missions/" + id + "/core", corePatch, Void.class);

    Map<String, Object> flagsPatch = new java.util.LinkedHashMap<>();
    flagsPatch.put("isInternal", form.isInternal() != null && form.isInternal());
    flagsPatch.put("version", form.flagsVersion());
    backendApiClient.patch("/api/v1/missions/" + id + "/flags", flagsPatch, Void.class);
  }

  /**
   * AJAX twin of {@link #updateMission} (#589): saves the mission core-edit form in place. Routed
   * by the {@code X-Requested-With} header (the classic {@code POST /missions/{id}} stays the no-JS
   * fallback). On a Jakarta {@code @Valid} failure it returns {@code 422} with a {@code {field:
   * message}} JSON map (messages resolved exactly as {@code th:errors} via {@link #messageSource})
   * so the client renders the errors inline without a navigation. On success it runs the three
   * section PATCHes, re-reads the mission to capture the PLANNED → ACTIVE auto-bump, and returns
   * the four fresh versions {@code {version, coreVersion, scheduleVersion, flagsVersion}} so the
   * client writes them back and a second consecutive save does not 409. A backend {@code 409} /
   * domain conflict is propagated as {@code problem+json} via {@link #propagateBackendError}.
   *
   * @param id the mission id
   * @param form the bound + validated edit form
   * @param bindingResult the binding/validation result
   * @param locale the request locale for field-error message resolution
   * @return {@code 200} with the four versions, {@code 422} with the field-error map, or the
   *     propagated backend error
   */
  @PostMapping(value = "/{id}", headers = "X-Requested-With=XMLHttpRequest")
  @ResponseBody
  @PreAuthorize("isAuthenticated()")
  public org.springframework.http.ResponseEntity<Object> updateMissionAjax(
      @PathVariable @NotNull UUID id,
      @Valid @ModelAttribute("missionForm") MissionForm form,
      BindingResult bindingResult,
      Locale locale) {
    if (bindingResult.hasErrors()) {
      Map<String, String> fieldErrors = new java.util.LinkedHashMap<>();
      for (FieldError fe : bindingResult.getFieldErrors()) {
        // First error per field wins (the form's fields carry one constraint each); the message is
        // resolved exactly as th:errors does so the inline text matches the classic re-render.
        fieldErrors.putIfAbsent(fe.getField(), messageSource.getMessage(fe, locale));
      }
      return org.springframework.http.ResponseEntity.unprocessableEntity().body(fieldErrors);
    }
    try {
      applyMissionUpdate(id, form);
      MissionDto refreshed =
          backendApiClient.get("/api/v1/missions/" + id, MissionDto.class, false);
      Map<String, Object> versions = new java.util.LinkedHashMap<>();
      versions.put("version", refreshed.version());
      versions.put("coreVersion", refreshed.coreVersion());
      versions.put("scheduleVersion", refreshed.scheduleVersion());
      versions.put("flagsVersion", refreshed.flagsVersion());
      return org.springframework.http.ResponseEntity.ok(versions);
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error("Update mission (ajax) failed for {}: {}", id, e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.error("Update mission (ajax) failed for {}", id, e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Form-post endpoint that deletes (or cancels) a mission. The backend cascades by detaching
   * inventory/refinery references rather than hard-deleting them, per the CHANGELOG entry for the
   * deleteMission change.
   *
   * @return redirect to {@code /missions}
   */
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

  /**
   * AJAX endpoint that adds a co-manager to a mission. Co-managers can edit the mission like the
   * owner; the owner cannot be removed via this endpoint.
   *
   * @return 200 on success, propagated backend status on failure
   */
  @PostMapping("/{id}/managers/{userId}")
  @ResponseBody
  public org.springframework.http.ResponseEntity<Object> addManager(
      @PathVariable String id, @PathVariable String userId) {
    log.debug("START addManager - id: '{}', userId: '{}'", id, userId);
    try {
      if (id == null || id.isBlank() || userId == null || userId.isBlank()) {
        log.debug("MISSING PARAMETERS - id: '{}', userId: '{}'", id, userId);
        return org.springframework.http.ResponseEntity.badRequest().build();
      }
      java.util.UUID missionUuid;
      java.util.UUID userUuid;
      try {
        missionUuid = java.util.UUID.fromString(id.trim());
      } catch (IllegalArgumentException e) {
        log.debug("INVALID MISSION ID FORMAT - id: '{}', Error: {}", id, e.getMessage());
        return org.springframework.http.ResponseEntity.badRequest().build();
      }
      try {
        userUuid = java.util.UUID.fromString(userId.trim());
      } catch (IllegalArgumentException e) {
        log.debug("INVALID USER ID FORMAT - userId: '{}', Error: {}", userId, e.getMessage());
        return org.springframework.http.ResponseEntity.badRequest().build();
      }

      log.debug("CALLING BACKEND - Mission: {}, User: {}", missionUuid, userUuid);
      try {
        backendApiClient.post(
            "/api/v1/missions/" + missionUuid + "/managers/" + userUuid + "/slim",
            null,
            String.class,
            false);
        log.debug("SUCCESS - Manager {} added to mission {}", userUuid, missionUuid);
        return org.springframework.http.ResponseEntity.ok().build();
      } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
        log.debug(
            "BACKEND ERROR adding manager for mission {} and user {}: Status={}, Message={},"
                + " Readable={}",
            missionUuid,
            userUuid,
            e.getStatusCode(),
            e.getMessage(),
            e.getReadableErrorMessage());
        return propagateBackendError(e);
      }
    } catch (Exception e) {
      log.debug(
          "UNEXPECTED ERROR in addManager: id='{}', userId='{}', error={}",
          id,
          userId,
          e.getMessage(),
          e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * AJAX endpoint that removes a co-manager. The owner is protected — removing the owner requires
   * {@link #setMissionOwner} instead.
   *
   * @return 200 on success, propagated backend status on failure
   */
  @DeleteMapping("/{id}/managers/{userId}")
  @ResponseBody
  public org.springframework.http.ResponseEntity<Object> removeManager(
      @PathVariable String id, @PathVariable String userId) {
    log.debug("START removeManager - id: '{}', userId: '{}'", id, userId);
    try {
      if (id == null || id.isBlank() || userId == null || userId.isBlank()) {
        log.debug("MISSING PARAMETERS in removeManager - id: '{}', userId: '{}'", id, userId);
        return org.springframework.http.ResponseEntity.badRequest().build();
      }
      java.util.UUID missionUuid;
      java.util.UUID userUuid;
      try {
        missionUuid = java.util.UUID.fromString(id.trim());
      } catch (IllegalArgumentException e) {
        log.debug(
            "INVALID MISSION ID FORMAT in removeManager - id: '{}', Error: {}", id, e.getMessage());
        return org.springframework.http.ResponseEntity.badRequest().build();
      }
      try {
        userUuid = java.util.UUID.fromString(userId.trim());
      } catch (IllegalArgumentException e) {
        log.debug(
            "INVALID USER ID FORMAT in removeManager - userId: '{}', Error: {}",
            userId,
            e.getMessage());
        return org.springframework.http.ResponseEntity.badRequest().build();
      }

      log.debug("CALLING BACKEND DELETE - Mission: {}, User: {}", missionUuid, userUuid);
      backendApiClient.delete(
          "/api/v1/missions/" + missionUuid + "/managers/" + userUuid + "/slim",
          Object.class,
          false);
      log.debug("SUCCESS DELETE - Manager {} removed from mission {}", userUuid, missionUuid);
      return org.springframework.http.ResponseEntity.ok().build();
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.debug(
          "BACKEND ERROR removing manager: Status={}, Message={}, Readable={}",
          e.getStatusCode(),
          e.getMessage(),
          e.getReadableErrorMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.debug(
          "UNEXPECTED ERROR in removeManager: id='{}', userId='{}', error={}",
          id,
          userId,
          e.getMessage(),
          e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * AJAX endpoint that transfers mission ownership to another user. Backend ensures the old owner
   * stays as a co-manager so they don't lose all access in one click.
   *
   * @return 200 on success, propagated backend status on failure
   */
  @PutMapping("/{id}/owner/{userId}")
  @ResponseBody
  public org.springframework.http.ResponseEntity<Object> setMissionOwner(
      @PathVariable String id, @PathVariable String userId) {
    log.debug("START setMissionOwner - id: '{}', userId: '{}'", id, userId);
    try {
      if (id == null || id.isBlank() || userId == null || userId.isBlank()) {
        log.debug("MISSING PARAMETERS in setMissionOwner - id: '{}', userId: '{}'", id, userId);
        return org.springframework.http.ResponseEntity.badRequest().build();
      }
      java.util.UUID missionUuid;
      java.util.UUID userUuid;
      try {
        missionUuid = java.util.UUID.fromString(id.trim());
      } catch (IllegalArgumentException e) {
        log.debug(
            "INVALID MISSION ID FORMAT in setMissionOwner - id: '{}', Error: {}",
            id,
            e.getMessage());
        return org.springframework.http.ResponseEntity.badRequest().build();
      }
      try {
        userUuid = java.util.UUID.fromString(userId.trim());
      } catch (IllegalArgumentException e) {
        log.debug(
            "INVALID USER ID FORMAT in setMissionOwner - userId: '{}', Error: {}",
            userId,
            e.getMessage());
        return org.springframework.http.ResponseEntity.badRequest().build();
      }

      log.debug("CALLING BACKEND PUT - Mission: {}, User: {}", missionUuid, userUuid);
      try {
        backendApiClient.put(
            "/api/v1/missions/" + missionUuid + "/owner/" + userUuid, null, Void.class, false);
        log.debug("SUCCESS - Owner of mission {} changed to user {}", missionUuid, userUuid);
        return org.springframework.http.ResponseEntity.ok().build();
      } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
        log.debug(
            "BACKEND ERROR changing owner: Status={}, Message={}, Readable={}",
            e.getStatusCode(),
            e.getMessage(),
            e.getReadableErrorMessage());
        return propagateBackendError(e);
      }
    } catch (Exception e) {
      log.debug(
          "UNEXPECTED ERROR in setMissionOwner: id='{}', userId='{}', error={}",
          id,
          userId,
          e.getMessage(),
          e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Form-post endpoint that creates or updates a frequency entry (radio channel) for the mission.
   * Same endpoint serves both create and update — the form's id field discriminates.
   *
   * @return redirect to {@code /missions/{id}}
   */
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

  /**
   * Form-post endpoint that removes a frequency entry.
   *
   * @return redirect to {@code /missions/{id}}
   */
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
      log.debug(
          "Add/update frequency (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.debug("UNEXPECTED ERROR in addOrUpdateFrequencyAjax for mission {}", id, e);
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
      log.debug(
          "Delete frequency (AJAX) failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.debug(
          "UNEXPECTED ERROR in deleteFrequencyAjax for mission {} freq {}", id, frequencyId, e);
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
      log.debug("Add unit (AJAX) failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.debug("UNEXPECTED ERROR in addUnitAjax for mission {}", id, e);
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
      log.debug("Update unit (AJAX) failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.debug("UNEXPECTED ERROR in updateUnitAjax for mission {} unit {}", id, unitId, e);
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
      log.debug("Delete unit (AJAX) failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.debug("UNEXPECTED ERROR in deleteUnitAjax for mission {} unit {}", id, unitId, e);
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
      log.debug(
          "Add participant (AJAX) failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.debug("UNEXPECTED ERROR in addParticipantAjax for mission {}", id, e);
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
      log.debug(
          "Update participant (AJAX) failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.debug(
          "UNEXPECTED ERROR in updateParticipantAjax for mission {} participant {}",
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
      log.debug(
          "Delete participant (AJAX) failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.debug(
          "UNEXPECTED ERROR in deleteParticipantAjax for mission {} participant {}",
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
      log.debug(
          "Check-in participant (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.debug(
          "UNEXPECTED ERROR in checkInParticipantAjax for mission {} participant {}",
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
      log.debug(
          "Check-out participant (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.debug(
          "UNEXPECTED ERROR in checkOutParticipantAjax for mission {} participant {}",
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
      log.debug("Add crew (AJAX) failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.debug("UNEXPECTED ERROR in addCrewAjax for mission {} unit {}", id, unitId, e);
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
      log.debug("Update crew (AJAX) failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.debug(
          "UNEXPECTED ERROR in updateCrewAjax for mission {} unit {} crew {}",
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
      log.debug("Delete crew (AJAX) failed: status={}, msg={}", e.getStatusCode(), e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.debug(
          "UNEXPECTED ERROR in deleteCrewAjax for mission {} unit {} crew {}",
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
      log.debug(
          "Get unassigned participants (AJAX) failed: status={}, msg={}",
          e.getStatusCode(),
          e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.debug("UNEXPECTED ERROR in getUnassignedParticipantsAjax for mission {}", id, e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Re-emits a backend {@link de.greluc.krt.iri.basetool.frontend.service.BackendServiceException}
   * as an {@code application/problem+json} response that preserves the stable {@code code} and
   * human-readable {@code detail} from the upstream RFC 7807 body.
   *
   * <p>The mission-detail AJAX layer (see {@code krt-fetch.js}) reads {@code code} to decide
   * between a "stale data, reload?" prompt (only for {@code OPTIMISTIC_LOCK} / {@code
   * PESSIMISTIC_LOCK}) and a plain error toast for domain conflicts ({@code DUPLICATE_ENTITY},
   * {@code BUSINESS_CONFLICT}, …). Returning {@code .build()} with only the status code stripped
   * that signal and made every 409 look like an optimistic-lock conflict.
   *
   * @param e parsed backend exception with status + RFC 7807 fields
   * @return problem+json response mirroring the upstream status and body
   */
  // Package-private so the sibling MissionFinancePageController's /ajax handlers reuse the same
  // RFC 7807 passthrough instead of duplicating it.
  static org.springframework.http.ResponseEntity<Object> propagateBackendError(
      @NotNull de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("status", e.getStatusCode());
    body.put("code", e.getProblemCode());
    if (e.getProblemDetail() != null && !e.getProblemDetail().isBlank()) {
      body.put("detail", e.getProblemDetail());
    }
    if (e.getCorrelationId() != null && !e.getCorrelationId().isBlank()) {
      body.put("correlationId", e.getCorrelationId());
    }
    return org.springframework.http.ResponseEntity.status(e.getStatusCode())
        .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
        .body(body);
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

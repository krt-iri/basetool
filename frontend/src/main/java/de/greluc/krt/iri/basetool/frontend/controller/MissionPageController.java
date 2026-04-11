package de.greluc.krt.iri.basetool.frontend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import de.greluc.krt.iri.basetool.frontend.model.dto.UpdatePayoutPreferenceRequest;
import de.greluc.krt.iri.basetool.frontend.model.form.MissionForm;
import de.greluc.krt.iri.basetool.frontend.model.form.ParticipantForm;
import de.greluc.krt.iri.basetool.frontend.model.form.UnitForm;
import de.greluc.krt.iri.basetool.frontend.model.form.CrewForm;
import de.greluc.krt.iri.basetool.frontend.model.dto.*;
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;

import java.time.Instant;
import java.util.*;

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
            PageResponse<OperationDto> operationsPage = backendApiClient.get(
                    "/api/v1/operations?page=0&size=1000",
                    new ParameterizedTypeReference<PageResponse<OperationDto>>() {},
                    false
            );
            model.addAttribute("operationsList", operationsPage.content());
        } catch (Exception e) {
            log.warn("Could not load operations", e);
            model.addAttribute("operationsList", List.of());
        }
    }

    public void addFormsToModel(Model model) {
        if (!model.containsAttribute("participantForm")) {
            model.addAttribute("participantForm", new ParticipantForm(null, "", null, null, "", null, null, null, null, null));
        }
        if (!model.containsAttribute("unitForm")) {
            model.addAttribute("unitForm", new UnitForm("", null, null, false, null));
        }
        if (!model.containsAttribute("crewForm")) {
            model.addAttribute("crewForm", new CrewForm(null, null));
        }
        if (!model.containsAttribute("financeForm")) {
            model.addAttribute("financeForm", new de.greluc.krt.iri.basetool.frontend.model.form.MissionFinanceEntryForm());
        }
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String listMissions(@RequestParam(required = false) String search,
                               @RequestParam(required = false) String start,
                               @RequestParam(required = false) String end,
                               @RequestParam(required = false) List<String> status,
                               @RequestParam(required = false, defaultValue = "false") boolean showPast,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size,
                               Model model,
                               @AuthenticationPrincipal OidcUser principal) {
        StringBuilder uri = new StringBuilder("/api/v1/missions/search?");
        if (search != null && !search.isBlank()) uri.append("query=").append(search).append("&");
        if (start != null && !start.isBlank()) uri.append("start=").append(start).append("&");
        if (end != null && !end.isBlank()) uri.append("end=").append(end).append("&");
        if (page != null) uri.append("page=").append(page).append("&");
        if (size != null) uri.append("size=").append(size).append("&");
        
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
            
            PageResponse<MissionListDto> missionsPage = backendApiClient.get(
                    uri.toString(),
                    new ParameterizedTypeReference<PageResponse<MissionListDto>>() {},
                    isPublic
            );
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
        return "missions";
    }

    @GetMapping("/{id}")
    public String missionDetail(@PathVariable @NotNull UUID id, Model model, @AuthenticationPrincipal OidcUser principal) {
        try {
            MissionDto mission = backendApiClient.get(
                    "/api/v1/missions/" + id,
                    new ParameterizedTypeReference<MissionDto>() {},
                    principal == null
            );

            // Sort participants and build groupings
            List<MissionParticipantDto> participants = new java.util.ArrayList<>(mission.participants());
            participants.sort((p1, p2) -> {
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
                    participantsByLeadType.computeIfAbsent(jobId.toString(), k -> new java.util.ArrayList<>()).add(p);
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
                                assignedUnitByParticipantId.merge(c.participantId(), unitName, (oldVal, newVal) -> oldVal + " " + newVal);
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
                        java.time.Instant effectiveStart = pStart.isBefore(missionStart) ? missionStart : pStart;
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
                    List<UserReferenceDto> allUsers = backendApiClient.get(
                            "/api/v1/users/lookup",
                            new ParameterizedTypeReference<List<UserReferenceDto>>() {},
                            false
                    );
                    model.addAttribute("allUsers", allUsers);
                } catch (Exception e) {
                    log.warn("Could not load users for manager selection", e);
                }
            }

            if (!model.containsAttribute("missionForm")) {
                model.addAttribute("missionForm", new MissionForm(
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
                    mission.version()
                ));
            }
            model.addAttribute("isNew", false);
            addFormsToModel(model);
            addOperationsToModel(model, principal == null);

            model.addAttribute("roundingMode", fetchRoundingMode(principal == null));

            // Fetch Mission JobTypes
            try {
                PageResponse<Map<String, Object>> jobTypesPage = backendApiClient.getCached(
                        "/api/v1/job-types?archetype=MISSION&size=1000",
                        new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {},
                        true
                );
                model.addAttribute("jobTypes", jobTypesPage.content());
            } catch (Exception e) {
                // Ignore if job types fail
            }

            // Fetch Crew JobTypes
            try {
                PageResponse<Map<String, Object>> crewJobTypesPage = backendApiClient.getCached(
                        "/api/v1/job-types?archetype=CREW&size=1000",
                        new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {},
                        true
                );
                model.addAttribute("crewJobTypes", crewJobTypesPage.content());
            } catch (Exception e) {
                // Ignore
            }
            
            // Fetch Squadrons
            try {
                PageResponse<Map<String, Object>> squadronsPage = backendApiClient.getCached(
                        "/api/v1/squadrons?size=1000",
                        new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {},
                        true
                );
                model.addAttribute("squadrons", squadronsPage.content());
            } catch (Exception e) {
                // Ignore
            }


            // Fetch FrequencyTypes
            try {
                PageResponse<Map<String, Object>> freqTypesPage = backendApiClient.getCached(
                        "/api/v1/frequency-types?size=1000&active=true&sort=sortIndex,asc",
                        new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {},
                        true
                );
                model.addAttribute("frequencyTypes", freqTypesPage.content());
            } catch (Exception e) {
                // Ignore
            }

            // Fetch Ships (Only if authenticated)
            if (principal != null) {
                try {
                    PageResponse<ShipDto> allShipsPage = backendApiClient.getCached(
                            "/api/v1/hangar/ships?size=1000",
                            new ParameterizedTypeReference<PageResponse<ShipDto>>() {}
                    );
                    model.addAttribute("allShips", allShipsPage.content());
                } catch (Exception e) {
                    // Ignore, e.g. if user has no HANGAR_READ or other issue
                }

                try {
                    PageResponse<ShipTypeDto> allShipTypesPage = backendApiClient.getCached(
                            "/api/v1/ship-types?size=1000",
                            new ParameterizedTypeReference<PageResponse<ShipTypeDto>>() {}
                    );
                    model.addAttribute("allShipTypes", allShipTypesPage.content());
                } catch (Exception e) {
                    // Ignore, e.g. if user has no HANGAR_READ or other issue
                }
            }

            // Fetch Finance Entries and Refinery Orders
            if (principal != null) {
                try {
                    PageResponse<MissionFinanceEntryDto> financesPage = backendApiClient.get(
                            "/api/v1/missions/" + id + "/finance-entries?size=1000",
                            new ParameterizedTypeReference<PageResponse<MissionFinanceEntryDto>>() {},
                            false
                    );
                    model.addAttribute("financeEntries", financesPage.content());

                    java.math.BigDecimal financeSum = backendApiClient.get(
                            "/api/v1/missions/" + id + "/finance-entries/sum",
                            java.math.BigDecimal.class,
                            false
                    );
                    model.addAttribute("financeSum", financeSum);

                    List<RefineryOrderListDto> refineryOrders = backendApiClient.get(
                            "/api/v1/refinery-orders/mission/" + id,
                            new ParameterizedTypeReference<List<RefineryOrderListDto>>() {},
                            false
                    );
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
        return "mission-detail";
    }
    
    @PostMapping("/{id}/participant")
    public String addParticipant(@PathVariable @NotNull UUID id,
                                 @Valid @ModelAttribute("participantForm") ParticipantForm form,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes,
                                 @AuthenticationPrincipal OidcUser principal) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.participantForm", bindingResult);
            redirectAttributes.addFlashAttribute("participantForm", form);
            redirectAttributes.addFlashAttribute("openModal", "participant-modal");
            return "redirect:/missions/" + id;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            if (form.userId() != null) body.put("userId", form.userId());
            if (form.guestName() != null && !form.guestName().isBlank()) body.put("guestName", form.guestName());
            if (form.desiredJobTypeId() != null) body.put("desiredJobTypeId", form.desiredJobTypeId());
            if (form.squadronId() != null) body.put("squadronId", form.squadronId());
            body.put("comment", form.comment());

            boolean isPublic = (principal == null);
            backendApiClient.post("/api/v1/missions/" + id + "/participants/add", body, Void.class, isPublic);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
        } catch (Exception e) {
            log.error("Add participant failed", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.mission.participant.add");
        }
        return "redirect:/missions/" + id;
    }

    @PostMapping("/{id}/participants/{participantId}/check-in")
    public String checkInParticipant(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID participantId, @AuthenticationPrincipal OidcUser principal, RedirectAttributes redirectAttributes) {
        try {
            boolean isPublic = (principal == null);
            backendApiClient.post("/api/v1/missions/" + id + "/participants/" + participantId + "/check-in", null, Void.class, isPublic);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
        } catch (Exception e) {
            log.error("Check-in participant failed", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.mission.participant.update");
        }
        return "redirect:/missions/" + id;
    }

    @PostMapping("/{id}/participants/{participantId}/check-out")
    public String checkOutParticipant(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID participantId, @AuthenticationPrincipal OidcUser principal, RedirectAttributes redirectAttributes) {
        try {
            boolean isPublic = (principal == null);
            backendApiClient.post("/api/v1/missions/" + id + "/participants/" + participantId + "/check-out", null, Void.class, isPublic);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
        } catch (Exception e) {
            log.error("Check-out participant failed", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.mission.participant.update");
        }
        return "redirect:/missions/" + id;
    }

    @PostMapping("/{id}/participants/{participantId}/payout-preference")
    @ResponseBody
    public org.springframework.http.ResponseEntity<Void> updatePayoutPreference(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID participantId, @RequestBody UpdatePayoutPreferenceRequest request, @AuthenticationPrincipal OidcUser principal) {
        try {
            boolean isPublic = (principal == null);
            backendApiClient.put("/api/v1/missions/" + id + "/participants/" + participantId + "/payout-preference", request, Void.class, isPublic);
            return org.springframework.http.ResponseEntity.ok().build();
        } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
            log.error("Update payout preference failed with status {}: {}", e.getStatusCode(), e.getMessage());
            if (e.getStatusCode() == 403 || e.getStatusCode() == 401) {
                return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
            }
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Update payout preference failed", e);
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/participants/{participantId}/delete")
    public String deleteParticipant(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID participantId, @AuthenticationPrincipal OidcUser principal, RedirectAttributes redirectAttributes) {
        try {
            boolean isPublic = (principal == null);
            backendApiClient.delete("/api/v1/missions/" + id + "/participants/" + participantId, Void.class, isPublic);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.delete");
        } catch (Exception e) {
            log.error("Delete participant failed", e);
            return "redirect:/missions/" + id + "?error=error.mission.participant.delete";
        }
        return "redirect:/missions/" + id;
    }

    @PostMapping("/{id}/participants/{participantId}/update")
    public String updateParticipant(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID participantId,
                                    @Valid @ModelAttribute("participantForm") ParticipantForm form,
                                    BindingResult bindingResult,
                                    RedirectAttributes redirectAttributes,
                                    @AuthenticationPrincipal OidcUser principal) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.participantForm", bindingResult);
            redirectAttributes.addFlashAttribute("participantForm", form);
            redirectAttributes.addFlashAttribute("openModal", "edit-participant-modal");
            redirectAttributes.addFlashAttribute("modalAction", "/missions/" + id + "/participants/" + participantId + "/update");
            return "redirect:/missions/" + id;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            if (form.desiredJobTypeId() != null) body.put("desiredMissionJobTypeId", form.desiredJobTypeId());
            if (form.plannedMissionJobTypeId() != null) body.put("plannedMissionJobTypeId", form.plannedMissionJobTypeId());
            if (form.squadronId() != null) body.put("squadronId", form.squadronId());
            body.put("comment", form.comment());
            if (form.startTime() != null && !form.startTime().isBlank()) {
                java.time.Instant parsed = parseToInstant(form.startTime());
                if (parsed != null) body.put("startTime", parsed.toString());
            }
            if (form.endTime() != null && !form.endTime().isBlank()) {
                java.time.Instant parsed = parseToInstant(form.endTime());
                if (parsed != null) body.put("endTime", parsed.toString());
            }
            if (form.payoutPreference() != null) body.put("payoutPreference", form.payoutPreference().name());
            if (form.version() != null) body.put("version", form.version());

            boolean isPublic = (principal == null);
            backendApiClient.put("/api/v1/missions/" + id + "/participants/" + participantId, body, Void.class, isPublic);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
        } catch (Exception e) {
            log.error("Update participant failed", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.mission.participant.update");
        }
        return "redirect:/missions/" + id;
    }

    @PostMapping("/{id}/units")
    @PreAuthorize("isAuthenticated()")
    public String addUnit(@PathVariable @NotNull UUID id,
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
    public String updateUnit(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID unitId,
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
    public String deleteUnit(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID unitId, RedirectAttributes redirectAttributes) {
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
    public String addCrew(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID unitId,
                          @Valid @ModelAttribute("crewForm") CrewForm form,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.crewForm", bindingResult);
            redirectAttributes.addFlashAttribute("crewForm", form);
            redirectAttributes.addFlashAttribute("openModal", "assign-crew-modal");
            redirectAttributes.addFlashAttribute("modalAction", "/missions/" + id + "/units/" + unitId + "/crew");
            return "redirect:/missions/" + id;
        }
         try {
             Map<String, Object> body = new HashMap<>();
             body.put("participantId", form.participantId());
             if (form.jobTypeIds() != null && !form.jobTypeIds().isEmpty()) {
                 body.put("jobTypeIds", form.jobTypeIds());
             }
             
             backendApiClient.post("/api/v1/missions/" + id + "/units/" + unitId + "/crew", body, Void.class);
             redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
         } catch(Exception e) {
             log.error("Add crew failed", e);
             redirectAttributes.addFlashAttribute("errorToast", "error.mission.crew.add");
         }
         return "redirect:/missions/" + id;
    }

    @PostMapping("/{id}/units/{unitId}/crew/{crewId}/update")
    @PreAuthorize("isAuthenticated()")
    public String updateCrew(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID unitId, @PathVariable @NotNull UUID crewId,
                             @Valid @ModelAttribute("crewForm") CrewForm form,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.crewForm", bindingResult);
            redirectAttributes.addFlashAttribute("crewForm", form);
            redirectAttributes.addFlashAttribute("openModal", "edit-crew-modal");
            redirectAttributes.addFlashAttribute("modalAction", "/missions/" + id + "/units/" + unitId + "/crew/" + crewId + "/update");
            return "redirect:/missions/" + id;
        }
         try {
             Map<String, Object> body = new HashMap<>();
             if (form.jobTypeIds() != null && !form.jobTypeIds().isEmpty()) {
                 body.put("jobTypeIds", form.jobTypeIds());
             } else {
                 body.put("jobTypeIds", List.of());
             }
             
             backendApiClient.put("/api/v1/missions/" + id + "/units/" + unitId + "/crew/" + crewId, body, Void.class);
             redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
         } catch(Exception e) {
             log.error("Update crew failed", e);
             redirectAttributes.addFlashAttribute("errorToast", "error.mission.crew.update");
         }
         return "redirect:/missions/" + id;
    }

    @PostMapping("/{id}/units/{unitId}/crew/{crewId}/delete")
    @PreAuthorize("isAuthenticated()")
    public String deleteCrew(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID unitId, @PathVariable @NotNull UUID crewId, RedirectAttributes redirectAttributes) {
        try {
            backendApiClient.delete("/api/v1/missions/" + id + "/units/" + unitId + "/crew/" + crewId, Void.class);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.delete");
        } catch (Exception e) {
            log.error("Delete crew failed", e);
            return "redirect:/missions/" + id + "?error=error.mission.crew.delete";
        }
        return "redirect:/missions/" + id;
    }

    @GetMapping("/new")
    @PreAuthorize("isAuthenticated()")
    public String createMissionForm(Model model) {
        if (!model.containsAttribute("missionForm")) {
            model.addAttribute("missionForm", new MissionForm("", "", "", "PLANNED", "", "", "", "", "", false, null, null));
        }
        model.addAttribute("isNew", true);
        model.addAttribute("mission", new MissionDto(null, "", null, null, "PLANNED", null, null, null, null, null, false, null, null, null, null, null, null, null, null, null, true, true, null));
        addFormsToModel(model);
        addOperationsToModel(model, false);
        return "mission-detail";
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public String createMission(@Valid @ModelAttribute("missionForm") MissionForm form,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.missionForm", bindingResult);
            redirectAttributes.addFlashAttribute("missionForm", form);
            return "redirect:/missions/new";
        }
         try {
             Instant meetingTime = (form.meetingTime() != null && !form.meetingTime().isBlank()) ? parseToInstant(form.meetingTime()) : null;
             Instant plannedStartTime = (form.plannedStartTime() != null && !form.plannedStartTime().isBlank()) ? parseToInstant(form.plannedStartTime()) : null;
             Instant plannedEndTime = (form.plannedEndTime() != null && !form.plannedEndTime().isBlank()) ? parseToInstant(form.plannedEndTime()) : null;
             
             OperationDto operation = (form.operationId() != null && !form.operationId().isBlank()) ? 
                     new OperationDto(UUID.fromString(form.operationId()), null, null, null, null, null, null) : null;

             MissionDto missionDto = new MissionDto(
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
                     null  // version
             );

             backendApiClient.post("/api/v1/missions", missionDto, Void.class);
             redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
         } catch(Exception e) {
             log.error("Create mission failed", e);
             redirectAttributes.addFlashAttribute("errorToast", "error.mission.create");
             redirectAttributes.addFlashAttribute("missionForm", form);
             return "redirect:/missions/new";
         }
         return "redirect:/missions";
    }

    @PostMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public String updateMission(@PathVariable @NotNull UUID id,
                                @Valid @ModelAttribute("missionForm") MissionForm form,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.missionForm", bindingResult);
            redirectAttributes.addFlashAttribute("missionForm", form);
            return "redirect:/missions/" + id;
        }
         try {
             Instant meetingTime = (form.meetingTime() != null && !form.meetingTime().isBlank()) ? parseToInstant(form.meetingTime()) : null;
             Instant plannedStartTime = (form.plannedStartTime() != null && !form.plannedStartTime().isBlank()) ? parseToInstant(form.plannedStartTime()) : null;
             Instant plannedEndTime = (form.plannedEndTime() != null && !form.plannedEndTime().isBlank()) ? parseToInstant(form.plannedEndTime()) : null;
             Instant actualStartTime = (form.actualStartTime() != null && !form.actualStartTime().isBlank()) ? parseToInstant(form.actualStartTime()) : null;
             Instant actualEndTime = (form.actualEndTime() != null && !form.actualEndTime().isBlank()) ? parseToInstant(form.actualEndTime()) : null;

             OperationDto operation = (form.operationId() != null && !form.operationId().isBlank()) ? 
                     new OperationDto(UUID.fromString(form.operationId()), null, null, null, null, null, null) : null;

             MissionDto missionDto = new MissionDto(
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
                     form.version() // version
             );

             backendApiClient.put("/api/v1/missions/" + id, missionDto, Void.class);
             redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
         } catch(Exception e) {
             log.error("Update mission failed", e);
             redirectAttributes.addFlashAttribute("errorToast", "error.mission.update");
             redirectAttributes.addFlashAttribute("missionForm", form);
             return "redirect:/missions/" + id;
         }
         return "redirect:/missions/" + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteMission(@PathVariable @NotNull UUID id, RedirectAttributes redirectAttributes) {
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
    public org.springframework.http.ResponseEntity<Void> addManager(@PathVariable String id, @PathVariable String userId) {
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
                log.error("[DEBUG_LOG] INVALID MISSION ID FORMAT - id: '{}', Error: {}", id, e.getMessage());
                return org.springframework.http.ResponseEntity.badRequest().build();
            }
            try {
                userUuid = java.util.UUID.fromString(userId.trim());
            } catch (IllegalArgumentException e) {
                log.error("[DEBUG_LOG] INVALID USER ID FORMAT - userId: '{}', Error: {}", userId, e.getMessage());
                return org.springframework.http.ResponseEntity.badRequest().build();
            }

            log.info("[DEBUG_LOG] CALLING BACKEND - Mission: {}, User: {}", missionUuid, userUuid);
            try {
                backendApiClient.post("/api/v1/missions/" + missionUuid + "/managers/" + userUuid, null, String.class, false);
                log.info("[DEBUG_LOG] SUCCESS - Manager {} added to mission {}", userUuid, missionUuid);
                return org.springframework.http.ResponseEntity.ok().build();
            } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
                log.error("[DEBUG_LOG] BACKEND ERROR adding manager for mission {} and user {}: Status={}, Message={}, Readable={}", 
                    missionUuid, userUuid, e.getStatusCode(), e.getMessage(), e.getReadableErrorMessage());
                return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
            }
        } catch (Exception e) {
            log.error("[DEBUG_LOG] UNEXPECTED ERROR in addManager: id='{}', userId='{}', error={}", id, userId, e.getMessage(), e);
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{id}/managers/{userId}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<Void> removeManager(@PathVariable String id, @PathVariable String userId) {
        log.info("[DEBUG_LOG] START removeManager - id: '{}', userId: '{}'", id, userId);
        try {
            if (id == null || id.isBlank() || userId == null || userId.isBlank()) {
                log.error("[DEBUG_LOG] MISSING PARAMETERS in removeManager - id: '{}', userId: '{}'", id, userId);
                return org.springframework.http.ResponseEntity.badRequest().build();
            }
            java.util.UUID missionUuid;
            java.util.UUID userUuid;
            try {
                missionUuid = java.util.UUID.fromString(id.trim());
            } catch (IllegalArgumentException e) {
                log.error("[DEBUG_LOG] INVALID MISSION ID FORMAT in removeManager - id: '{}', Error: {}", id, e.getMessage());
                return org.springframework.http.ResponseEntity.badRequest().build();
            }
            try {
                userUuid = java.util.UUID.fromString(userId.trim());
            } catch (IllegalArgumentException e) {
                log.error("[DEBUG_LOG] INVALID USER ID FORMAT in removeManager - userId: '{}', Error: {}", userId, e.getMessage());
                return org.springframework.http.ResponseEntity.badRequest().build();
            }

            log.info("[DEBUG_LOG] CALLING BACKEND DELETE - Mission: {}, User: {}", missionUuid, userUuid);
            backendApiClient.delete("/api/v1/missions/" + missionUuid + "/managers/" + userUuid, Object.class, false);
            log.info("[DEBUG_LOG] SUCCESS DELETE - Manager {} removed from mission {}", userUuid, missionUuid);
            return org.springframework.http.ResponseEntity.ok().build();
        } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
            log.error("[DEBUG_LOG] BACKEND ERROR removing manager: Status={}, Message={}, Readable={}", 
                e.getStatusCode(), e.getMessage(), e.getReadableErrorMessage());
            return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("[DEBUG_LOG] UNEXPECTED ERROR in removeManager: id='{}', userId='{}', error={}", id, userId, e.getMessage(), e);
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{id}/owner/{userId}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<Void> setMissionOwner(@PathVariable String id, @PathVariable String userId) {
        log.info("[DEBUG_LOG] START setMissionOwner - id: '{}', userId: '{}'", id, userId);
        try {
            if (id == null || id.isBlank() || userId == null || userId.isBlank()) {
                log.error("[DEBUG_LOG] MISSING PARAMETERS in setMissionOwner - id: '{}', userId: '{}'", id, userId);
                return org.springframework.http.ResponseEntity.badRequest().build();
            }
            java.util.UUID missionUuid;
            java.util.UUID userUuid;
            try {
                missionUuid = java.util.UUID.fromString(id.trim());
            } catch (IllegalArgumentException e) {
                log.error("[DEBUG_LOG] INVALID MISSION ID FORMAT in setMissionOwner - id: '{}', Error: {}", id, e.getMessage());
                return org.springframework.http.ResponseEntity.badRequest().build();
            }
            try {
                userUuid = java.util.UUID.fromString(userId.trim());
            } catch (IllegalArgumentException e) {
                log.error("[DEBUG_LOG] INVALID USER ID FORMAT in setMissionOwner - userId: '{}', Error: {}", userId, e.getMessage());
                return org.springframework.http.ResponseEntity.badRequest().build();
            }

            log.info("[DEBUG_LOG] CALLING BACKEND PUT - Mission: {}, User: {}", missionUuid, userUuid);
            try {
                backendApiClient.put("/api/v1/missions/" + missionUuid + "/owner/" + userUuid, null, Void.class, false);
                log.info("[DEBUG_LOG] SUCCESS - Owner of mission {} changed to user {}", missionUuid, userUuid);
                return org.springframework.http.ResponseEntity.ok().build();
            } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
                log.error("[DEBUG_LOG] BACKEND ERROR changing owner: Status={}, Message={}, Readable={}", 
                    e.getStatusCode(), e.getMessage(), e.getReadableErrorMessage());
                return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
            }
        } catch (Exception e) {
            log.error("[DEBUG_LOG] UNEXPECTED ERROR in setMissionOwner: id='{}', userId='{}', error={}", id, userId, e.getMessage(), e);
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/frequencies")
    @PreAuthorize("hasRole('MISSION_MANAGER')")
    public String addOrUpdateFrequency(@PathVariable @NotNull UUID id,
                                       @RequestParam @NotNull UUID frequencyTypeId,
                                       @RequestParam @NotNull java.math.BigDecimal value,
                                       RedirectAttributes redirectAttributes) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("frequencyTypeId", frequencyTypeId);
            body.put("value", value);

            backendApiClient.post("/api/v1/missions/" + id + "/frequencies", body, Void.class);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
        } catch (Exception e) {
            log.error("Add or update frequency failed", e);
            return "redirect:/missions/" + id + "?error=error.mission.frequency.update";
        }
        return "redirect:/missions/" + id;
    }

    @PostMapping("/{id}/frequencies/{frequencyId}/delete")
    @PreAuthorize("hasRole('MISSION_MANAGER')")
    public String deleteFrequency(@PathVariable @NotNull UUID id, @PathVariable @NotNull UUID frequencyId, RedirectAttributes redirectAttributes) {
        try {
            backendApiClient.delete("/api/v1/missions/" + id + "/frequencies/" + frequencyId, Void.class);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.delete");
        } catch (Exception e) {
            log.error("Delete frequency failed", e);
            return "redirect:/missions/" + id + "?error=error.mission.frequency.delete";
        }
        return "redirect:/missions/" + id;
    }

    private String extractParticipantName(MissionParticipantDto participant) {
        if (participant == null) return "";
        if (participant.user() != null) {
            if (participant.user().effectiveName() != null && !participant.user().effectiveName().isBlank()) {
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
            Map<String, Object> setting = backendApiClient.get("/api/v1/settings/refinery.rounding.mode", new ParameterizedTypeReference<Map<String, Object>>() {}, isPublic);
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
                if (s.isBlank()) return "";
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

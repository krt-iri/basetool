package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.JobTypeDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.SquadronDto;
import de.greluc.krt.iri.basetool.frontend.model.form.FrequencyTypeForm;
import de.greluc.krt.iri.basetool.frontend.model.form.JobTypeForm;
import de.greluc.krt.iri.basetool.frontend.model.form.SquadronForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/mission-data")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
public class AdminMissionDataPageController {

  private final BackendApiClient backendApiClient;

  @GetMapping
  public String listData(
      @RequestParam(required = false, defaultValue = "false") boolean includeInactiveJobTypes,
      @RequestParam(required = false, defaultValue = "false") boolean includeInactiveSquadrons,
      @RequestParam(required = false, defaultValue = "false") boolean includeInactiveFrequencyTypes,
      Model model) {
    if (!model.containsAttribute("jobTypeForm")) {
      model.addAttribute("jobTypeForm", new JobTypeForm("", "", "", false, 0L));
    }
    if (!model.containsAttribute("squadronForm")) {
      model.addAttribute("squadronForm", new SquadronForm("", "", "", 0L));
    }
    if (!model.containsAttribute("frequencyTypeForm")) {
      model.addAttribute("frequencyTypeForm", new FrequencyTypeForm("", "", 0L));
    }
    model.addAttribute("includeInactiveJobTypes", includeInactiveJobTypes);
    model.addAttribute("includeInactiveSquadrons", includeInactiveSquadrons);
    model.addAttribute("includeInactiveFrequencyTypes", includeInactiveFrequencyTypes);
    try {
      PageResponse<Map<String, Object>> jobTypesPage =
          backendApiClient.get(
              "/api/v1/job-types?size=1000&sort=name,asc&includeInactive="
                  + includeInactiveJobTypes,
              new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {});

      List<JobTypeDto> jobTypes = null;
      if (jobTypesPage != null && jobTypesPage.content() != null) {
        jobTypes =
            jobTypesPage.content().stream()
                .map(
                    m ->
                        new JobTypeDto(
                            parseUuid(m.get("id")),
                            parseString(m.get("name")),
                            parseString(m.get("description")),
                            parseString(m.get("archetype")),
                            parseUuid(m.get("parentId")),
                            parseBoolean(m.get("active")),
                            parseBoolean(m.get("isLeadershipRole")),
                            parseLong(m.get("version"))))
                .collect(Collectors.toCollection(ArrayList::new));
        jobTypes.sort(
            Comparator.comparing(
                j -> j.name() == null ? "" : j.name(), String.CASE_INSENSITIVE_ORDER));
      }
      model.addAttribute("jobTypes", jobTypes);

      PageResponse<Map<String, Object>> squadronsPage =
          backendApiClient.get(
              "/api/v1/squadrons?size=1000&sort=name,asc&includeInactive="
                  + includeInactiveSquadrons,
              new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {});

      List<SquadronDto> squadrons = null;
      if (squadronsPage != null && squadronsPage.content() != null) {
        squadrons =
            squadronsPage.content().stream()
                .map(
                    m ->
                        new SquadronDto(
                            parseUuid(m.get("id")),
                            parseString(m.get("name")),
                            parseString(m.get("shorthand")),
                            parseString(m.get("description")),
                            parseBoolean(m.get("active")),
                            parseLong(m.get("version"))))
                .collect(Collectors.toCollection(ArrayList::new));
        squadrons.sort(
            Comparator.comparing(
                s -> s.name() == null ? "" : s.name(), String.CASE_INSENSITIVE_ORDER));
      }
      model.addAttribute("squadrons", squadrons);

      PageResponse<Map<String, Object>> freqsPage =
          backendApiClient.get(
              "/api/v1/frequency-types?size=1000&sort=sortIndex,asc"
                  + (includeInactiveFrequencyTypes ? "" : "&active=true"),
              new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {});
      if (freqsPage != null) {
        model.addAttribute("frequencyTypes", freqsPage.content());
      }

    } catch (Exception e) {
      log.error("Error loading mission data", e);
      model.addAttribute("error", "error.admin.mission.data.load");
    }
    return "admin/mission-data";
  }

  // JobTypes
  @PostMapping("/job-types")
  public String createJobType(
      @Valid @ModelAttribute("jobTypeForm") JobTypeForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      // Render directly; the BindingResult stays request-scoped so it never goes
      // through a Redis-serialised FlashMap (see RedisSessionConfig).
      model.addAttribute("openModal", "jobtype-modal");
      model.addAttribute("modalAction", "/admin/mission-data/job-types");
      return listData(false, false, false, model);
    }
    try {
      JobTypeDto body =
          new JobTypeDto(
              null,
              form.name(),
              form.description(),
              form.archetype(),
              null,
              true,
              form.isLeadershipRole(),
              0L);
      backendApiClient.post("/api/v1/job-types", body, Void.class);
      backendApiClient.clearStaticDataCache();
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (BackendServiceException e) {
      log.error("Create JobType failed", e);
      if (e.getStatusCode() == 409) {
        redirectAttributes.addFlashAttribute("errorToast", "error.duplicate.jobtype");
        return "redirect:/admin/mission-data";
      }
      return "redirect:/admin/mission-data?error=CreateJobTypeFailed";
    } catch (Exception e) {
      log.error("Create JobType failed", e);
      return "redirect:/admin/mission-data?error=CreateJobTypeFailed";
    }
    return "redirect:/admin/mission-data";
  }

  @PostMapping("/job-types/{id}/update")
  public String updateJobType(
      @PathVariable @NotNull UUID id,
      @Valid @ModelAttribute("jobTypeForm") JobTypeForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("openModal", "jobtype-modal");
      model.addAttribute("modalAction", "/admin/mission-data/job-types/" + id + "/update");
      return listData(false, false, false, model);
    }
    try {
      JobTypeDto body =
          new JobTypeDto(
              id,
              form.name(),
              form.description(),
              form.archetype(),
              null,
              true,
              form.isLeadershipRole(),
              form.version());
      backendApiClient.put("/api/v1/job-types/" + id, body, Void.class);
      backendApiClient.clearStaticDataCache();
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (BackendServiceException e) {
      log.error("Update JobType failed", e);
      if (e.getStatusCode() == 409) {
        if ("concurrency-conflict".equals(e.getProblemType())) {
          redirectAttributes.addFlashAttribute("errorToast", "error.concurrency.conflict");
        } else {
          redirectAttributes.addFlashAttribute("errorToast", "error.duplicate.jobtype");
        }
        return "redirect:/admin/mission-data";
      }
      return "redirect:/admin/mission-data?error=UpdateJobTypeFailed";
    } catch (Exception e) {
      log.error("Update JobType failed", e);
      return "redirect:/admin/mission-data?error=UpdateJobTypeFailed";
    }
    return "redirect:/admin/mission-data";
  }

  @PostMapping("/job-types/{id}/delete")
  public String deleteJobType(
      @PathVariable @NotNull UUID id, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/job-types/" + id, Void.class);
      backendApiClient.clearStaticDataCache();
      redirectAttributes.addFlashAttribute("successToast", "notification.success.delete");
    } catch (BackendServiceException e) {
      log.error("Delete JobType failed", e);
      if (e.getStatusCode() == 409) {
        redirectAttributes.addFlashAttribute("errorToast", "error.delete.jobtype.in_use");
        return "redirect:/admin/mission-data";
      }
      return "redirect:/admin/mission-data?error=DeleteJobTypeFailed";
    } catch (Exception e) {
      log.error("Delete JobType failed", e);
      return "redirect:/admin/mission-data?error=DeleteJobTypeFailed";
    }
    return "redirect:/admin/mission-data";
  }

  @PostMapping("/job-types/{id}/activate")
  @PreAuthorize("hasRole('ADMIN')")
  public String activateJobType(
      @PathVariable @NotNull UUID id, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.post("/api/v1/job-types/" + id + "/activate", null, Void.class);
      backendApiClient.clearStaticDataCache();
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (BackendServiceException e) {
      log.error("Activate JobType failed", e);
      return "redirect:/admin/mission-data?error=ActivateJobTypeFailed";
    } catch (Exception e) {
      log.error("Activate JobType failed", e);
      return "redirect:/admin/mission-data?error=ActivateJobTypeFailed";
    }
    return "redirect:/admin/mission-data";
  }

  // Squadrons
  @PostMapping("/squadrons")
  public String createSquadron(
      @Valid @ModelAttribute("squadronForm") SquadronForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("openModal", "squadron-modal");
      model.addAttribute("modalAction", "/admin/mission-data/squadrons");
      return listData(false, false, false, model);
    }
    try {
      SquadronDto body =
          new SquadronDto(null, form.name(), form.shorthand(), form.description(), true, 0L);
      backendApiClient.post("/api/v1/squadrons", body, Void.class);
      backendApiClient.clearStaticDataCache();
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (BackendServiceException e) {
      log.error("Create Squadron failed", e);
      if (e.getStatusCode() == 409) {
        redirectAttributes.addFlashAttribute("errorToast", "error.duplicate.squadron");
        return "redirect:/admin/mission-data";
      }
      return "redirect:/admin/mission-data?error=CreateSquadronFailed";
    } catch (Exception e) {
      log.error("Create Squadron failed", e);
      return "redirect:/admin/mission-data?error=CreateSquadronFailed";
    }
    return "redirect:/admin/mission-data";
  }

  @PostMapping("/squadrons/{id}/update")
  public String updateSquadron(
      @PathVariable @NotNull UUID id,
      @Valid @ModelAttribute("squadronForm") SquadronForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("openModal", "squadron-modal");
      model.addAttribute("modalAction", "/admin/mission-data/squadrons/" + id + "/update");
      return listData(false, false, false, model);
    }
    try {
      SquadronDto body =
          new SquadronDto(
              id, form.name(), form.shorthand(), form.description(), true, form.version());
      backendApiClient.put("/api/v1/squadrons/" + id, body, Void.class);
      backendApiClient.clearStaticDataCache();
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (BackendServiceException e) {
      log.error("Update Squadron failed", e);
      if (e.getStatusCode() == 409) {
        if ("concurrency-conflict".equals(e.getProblemType())) {
          redirectAttributes.addFlashAttribute("errorToast", "error.concurrency.conflict");
        } else {
          redirectAttributes.addFlashAttribute("errorToast", "error.duplicate.squadron");
        }
        return "redirect:/admin/mission-data";
      }
      return "redirect:/admin/mission-data?error=UpdateSquadronFailed";
    } catch (Exception e) {
      log.error("Update Squadron failed", e);
      return "redirect:/admin/mission-data?error=UpdateSquadronFailed";
    }
    return "redirect:/admin/mission-data";
  }

  @PostMapping("/squadrons/{id}/delete")
  public String deleteSquadron(
      @PathVariable @NotNull UUID id, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/squadrons/" + id, Void.class);
      backendApiClient.clearStaticDataCache();
      redirectAttributes.addFlashAttribute("successToast", "notification.success.delete");
    } catch (BackendServiceException e) {
      log.error("Delete Squadron failed", e);
      if (e.getStatusCode() == 409) {
        redirectAttributes.addFlashAttribute("errorToast", "error.delete.squadron.in_use");
        return "redirect:/admin/mission-data";
      }
      return "redirect:/admin/mission-data?error=DeleteSquadronFailed";
    } catch (Exception e) {
      log.error("Delete Squadron failed", e);
      return "redirect:/admin/mission-data?error=DeleteSquadronFailed";
    }
    return "redirect:/admin/mission-data";
  }

  @PostMapping("/squadrons/{id}/activate")
  @PreAuthorize("hasRole('ADMIN')")
  public String activateSquadron(
      @PathVariable @NotNull UUID id, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.post("/api/v1/squadrons/" + id + "/activate", null, Void.class);
      backendApiClient.clearStaticDataCache();
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Activate Squadron failed", e);
      return "redirect:/admin/mission-data?error=ActivateSquadronFailed";
    }
    return "redirect:/admin/mission-data";
  }

  // FrequencyTypes
  @PostMapping("/frequency-types")
  public String createFrequencyType(
      @Valid @ModelAttribute("frequencyTypeForm") FrequencyTypeForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("openModal", "frequency-type-modal");
      model.addAttribute("modalAction", "/admin/mission-data/frequency-types");
      return listData(false, false, false, model);
    }
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("name", form.name());
      body.put("description", form.description());
      body.put("active", true);
      backendApiClient.post("/api/v1/frequency-types", body, Void.class);
      backendApiClient.clearStaticDataCache();
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Create FrequencyType failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.general");
    }
    return "redirect:/admin/mission-data";
  }

  @PostMapping("/frequency-types/{id}/update")
  public String updateFrequencyType(
      @PathVariable @NotNull UUID id,
      @Valid @ModelAttribute("frequencyTypeForm") FrequencyTypeForm form,
      @RequestParam(required = false) Boolean active,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("openModal", "frequency-type-modal");
      model.addAttribute("modalAction", "/admin/mission-data/frequency-types/" + id + "/update");
      return listData(false, false, false, model);
    }
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("name", form.name());
      body.put("description", form.description());
      body.put("active", active != null ? active : true);
      body.put("version", form.version());
      backendApiClient.put("/api/v1/frequency-types/" + id, body, Void.class);
      backendApiClient.clearStaticDataCache();
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (BackendServiceException e) {
      log.error("Update FrequencyType failed", e);
      if (e.getStatusCode() == 409 && "concurrency-conflict".equals(e.getProblemType())) {
        redirectAttributes.addFlashAttribute("errorToast", "error.concurrency.conflict");
      } else {
        redirectAttributes.addFlashAttribute("errorToast", "error.general");
      }
    } catch (Exception e) {
      log.error("Update FrequencyType failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.general");
    }
    return "redirect:/admin/mission-data";
  }

  @PostMapping("/frequency-types/{id}/delete")
  public String deleteFrequencyType(
      @PathVariable @NotNull UUID id, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/frequency-types/" + id, Void.class);
      backendApiClient.clearStaticDataCache();
      redirectAttributes.addFlashAttribute("successToast", "notification.success.delete");
    } catch (BackendServiceException e) {
      log.error("Delete FrequencyType failed", e);
      if (e.getStatusCode() == 409) {
        redirectAttributes.addFlashAttribute("errorToast", "error.delete.frequency_type.in_use");
        return "redirect:/admin/mission-data";
      }
      redirectAttributes.addFlashAttribute("errorToast", "error.general");
    } catch (Exception e) {
      log.error("Delete FrequencyType failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.general");
    }
    return "redirect:/admin/mission-data";
  }

  @PostMapping("/frequency-types/{id}/activate")
  public String activateFrequencyType(
      @PathVariable @NotNull UUID id, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.post("/api/v1/frequency-types/" + id + "/activate", null, Void.class);
      backendApiClient.clearStaticDataCache();
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Activate FrequencyType failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.general");
    }
    return "redirect:/admin/mission-data";
  }

  @PostMapping("/frequency-types/reorder")
  @ResponseBody
  public ResponseEntity<Void> reorderFrequencyTypes(@RequestBody List<UUID> ids) {
    try {
      backendApiClient.post("/api/v1/frequency-types/reorder", ids, Void.class);
      backendApiClient.clearStaticDataCache();
      return ResponseEntity.ok().build();
    } catch (Exception e) {
      log.error("Reorder FrequencyTypes failed", e);
      return ResponseEntity.status(500).build();
    }
  }

  private static String parseString(Object o) {
    return o == null ? null : String.valueOf(o);
  }

  private static UUID parseUuid(Object o) {
    if (o == null) {
      return null;
    }
    try {
      return UUID.fromString(String.valueOf(o));
    } catch (Exception ignored) {
      return null;
    }
  }

  private static boolean parseBoolean(Object o) {
    if (o == null) {
      return false;
    }
    if (o instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(o));
  }

  private static Long parseLong(Object o) {
    if (o == null) {
      return 0L;
    }
    if (o instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(o));
    } catch (Exception ignored) {
      return 0L;
    }
  }
}

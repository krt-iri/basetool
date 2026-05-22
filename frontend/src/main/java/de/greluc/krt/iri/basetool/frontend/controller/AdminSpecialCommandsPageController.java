package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.SpecialCommandDto;
import de.greluc.krt.iri.basetool.frontend.model.form.SpecialCommandForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC controller for the admin Spezialkommando-management page ({@code
 * /admin/special-commands}). Mirrors the Squadron section of {@link
 * AdminMissionDataPageController} field-for-field: list + create + update + soft-delete +
 * re-activate, with the same BindingResult-inline-rerender / 409-distinct-toast / generic-error-
 * redirect pattern.
 *
 * <p>SK-specific differences from Squadron:
 *
 * <ul>
 *   <li>No promotion-feature toggle — Spezialkommandos never carry the promotion subsystem. The
 *       backend's V94 CHECK constraint plus the {@code SpecialCommand} setter override forbid the
 *       flag from ever being {@code true} on an SK row.
 *   <li>SK lives on a dedicated page rather than being one of three columns on {@code
 *       /admin/mission-data}. SK administration is a denser surface (separate detail page with a
 *       member roster lands in R5.c.b) so the dedicated page avoids cluttering the existing
 *       reference-data view.
 *   <li>No detail-page link yet — R5.c.b adds the per-SK detail page with the member roster, the
 *       add/remove modal, and the role-flag controls. This PR only adds the list-level CRUD.
 * </ul>
 */
@Controller
@RequestMapping("/admin/special-commands")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminSpecialCommandsPageController {

  private final BackendApiClient backendApiClient;

  /**
   * Renders the SK overview list. Re-seeds an empty form when the model does not already carry
   * one (which would be the case after a validation re-render). {@code includeInactive=true}
   * surfaces soft-deleted SKs so the admin can reactivate them.
   *
   * @param includeInactive show soft-deleted SKs.
   * @param model Thymeleaf model populated with the SK list, the form and the toggle.
   * @return the {@code admin/special-commands} view name.
   */
  @GetMapping
  public String listSpecialCommands(
      @RequestParam(required = false, defaultValue = "false") boolean includeInactive,
      Model model) {
    if (!model.containsAttribute("specialCommandForm")) {
      model.addAttribute("specialCommandForm", new SpecialCommandForm("", "", "", 0L));
    }
    model.addAttribute("includeInactive", includeInactive);

    try {
      model.addAttribute("specialCommands", fetchSpecialCommands(includeInactive));
    } catch (Exception e) {
      log.error("Error loading SpecialCommands", e);
      model.addAttribute("specialCommands", List.of());
      model.addAttribute("error", "error.admin.specialcommands.load");
    }
    return "admin/special-commands";
  }

  /**
   * Fetches the SK catalog from the backend and transforms the raw payload into a sorted list of
   * {@link SpecialCommandDto} records. Mirrors {@link
   * AdminMissionDataPageController}'s {@code fetchSquadrons} parsing path.
   *
   * @param includeInactive forward to the backend's {@code includeInactive} query param.
   * @return list of SKs sorted case-insensitively by name; never {@code null}.
   */
  private List<SpecialCommandDto> fetchSpecialCommands(boolean includeInactive) {
    PageResponse<Map<String, Object>> page =
        backendApiClient.get(
            "/api/v1/special-commands?size=1000&sort=name,asc&includeInactive=" + includeInactive,
            new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {});
    if (page == null || page.content() == null) {
      return List.of();
    }
    List<SpecialCommandDto> commands =
        page.content().stream()
            .map(
                m ->
                    new SpecialCommandDto(
                        parseUuid(m.get("id")),
                        parseString(m.get("name")),
                        parseString(m.get("shorthand")),
                        parseString(m.get("description")),
                        parseBoolean(m.get("active")),
                        parseLong(m.get("version"))))
            .collect(Collectors.toCollection(ArrayList::new));
    commands.sort(
        Comparator.comparing(s -> s.name() == null ? "" : s.name(), String.CASE_INSENSITIVE_ORDER));
    return commands;
  }

  /**
   * Creates a new Spezialkommando. Validation failure re-renders the list inline with the create
   * modal re-opened; a 409 from the backend's duplicate-name check surfaces as the dedicated
   * toast; all other failures redirect with an error query param.
   *
   * @param form SK form payload.
   * @param bindingResult validation errors carrier.
   * @param model Thymeleaf model used for inline re-rendering on validation failure.
   * @param redirectAttributes flash-attribute carrier for the success / error toast.
   * @return inline list page on validation failure, otherwise redirect to {@code
   *     /admin/special-commands}.
   */
  @PostMapping
  public String createSpecialCommand(
      @Valid @ModelAttribute("specialCommandForm") SpecialCommandForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("openModal", "specialcommand-modal");
      model.addAttribute("modalAction", "/admin/special-commands");
      return listSpecialCommands(false, model);
    }
    try {
      SpecialCommandDto body =
          new SpecialCommandDto(null, form.name(), form.shorthand(), form.description(), true, 0L);
      backendApiClient.post("/api/v1/special-commands", body, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (BackendServiceException e) {
      log.error("Create SpecialCommand failed", e);
      if (e.getStatusCode() == 409) {
        redirectAttributes.addFlashAttribute("errorToast", "error.duplicate.specialcommand");
        return "redirect:/admin/special-commands";
      }
      return "redirect:/admin/special-commands?error=CreateSpecialCommandFailed";
    } catch (Exception e) {
      log.error("Create SpecialCommand failed", e);
      return "redirect:/admin/special-commands?error=CreateSpecialCommandFailed";
    }
    return "redirect:/admin/special-commands";
  }

  /**
   * Updates an existing Spezialkommando. Distinguishes optimistic-locking conflict ({@code
   * concurrency-conflict} problem type) from a duplicate-name 409 so the user gets the right
   * toast.
   *
   * @param id SK id.
   * @param form SK form (carries the version).
   * @param bindingResult validation errors carrier.
   * @param model Thymeleaf model used for inline re-rendering.
   * @param redirectAttributes flash-attribute carrier.
   * @return inline list page on failure, otherwise redirect.
   */
  @PostMapping("/{id}/update")
  public String updateSpecialCommand(
      @PathVariable @NotNull UUID id,
      @Valid @ModelAttribute("specialCommandForm") SpecialCommandForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("openModal", "specialcommand-modal");
      model.addAttribute("modalAction", "/admin/special-commands/" + id + "/update");
      return listSpecialCommands(false, model);
    }
    try {
      SpecialCommandDto body =
          new SpecialCommandDto(
              id, form.name(), form.shorthand(), form.description(), true, form.version());
      backendApiClient.put("/api/v1/special-commands/" + id, body, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (BackendServiceException e) {
      log.error("Update SpecialCommand failed", e);
      if (e.getStatusCode() == 409) {
        if ("concurrency-conflict".equals(e.getProblemType())) {
          redirectAttributes.addFlashAttribute("errorToast", "error.concurrency.conflict");
        } else {
          redirectAttributes.addFlashAttribute("errorToast", "error.duplicate.specialcommand");
        }
        return "redirect:/admin/special-commands";
      }
      return "redirect:/admin/special-commands?error=UpdateSpecialCommandFailed";
    } catch (Exception e) {
      log.error("Update SpecialCommand failed", e);
      return "redirect:/admin/special-commands?error=UpdateSpecialCommandFailed";
    }
    return "redirect:/admin/special-commands";
  }

  /**
   * Soft-deletes a Spezialkommando (flips {@code active = false}). A 409 from the backend (would
   * indicate a future referential-integrity guard once aggregates can be owned by SKs) surfaces
   * as the dedicated "in use" toast.
   *
   * @param id SK id.
   * @param redirectAttributes flash-attribute carrier.
   * @return redirect to {@code /admin/special-commands}.
   */
  @PostMapping("/{id}/delete")
  public String deleteSpecialCommand(
      @PathVariable @NotNull UUID id, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/special-commands/" + id, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.delete");
    } catch (BackendServiceException e) {
      log.error("Delete SpecialCommand failed", e);
      if (e.getStatusCode() == 409) {
        redirectAttributes.addFlashAttribute("errorToast", "error.delete.specialcommand.in_use");
        return "redirect:/admin/special-commands";
      }
      return "redirect:/admin/special-commands?error=DeleteSpecialCommandFailed";
    } catch (Exception e) {
      log.error("Delete SpecialCommand failed", e);
      return "redirect:/admin/special-commands?error=DeleteSpecialCommandFailed";
    }
    return "redirect:/admin/special-commands";
  }

  /**
   * Re-activates a soft-deleted Spezialkommando.
   *
   * @param id SK id.
   * @param redirectAttributes flash-attribute carrier.
   * @return redirect to {@code /admin/special-commands}.
   */
  @PostMapping("/{id}/activate")
  public String activateSpecialCommand(
      @PathVariable @NotNull UUID id, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.post("/api/v1/special-commands/" + id + "/activate", null, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Activate SpecialCommand failed", e);
      return "redirect:/admin/special-commands?error=ActivateSpecialCommandFailed";
    }
    return "redirect:/admin/special-commands";
  }

  // ---------- payload-parsing helpers (mirror AdminMissionDataPageController) -----------

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

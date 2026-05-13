package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.TerminalDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC controller for the admin terminal-management page ({@code /admin/terminals}).
 *
 * <p>The terminal list is much larger than locations (every UEX terminal in every system); the page
 * pulls up to 10 000 records in one shot, decodes raw JSON maps into the lightweight {@link
 * TerminalDto} (only the fields actually shown), and sorts case-insensitively by name. The PUT
 * endpoint only propagates the hidden flag — UEX-imported fields stay untouched so admins cannot
 * accidentally rename terminals via this page.
 */
@Controller
@RequestMapping("/admin/terminals")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
public class AdminTerminalsPageController {

  private final BackendApiClient backendApiClient;

  /**
   * Fetches up to 10 000 terminals (including hidden), decodes the slim projection and sorts
   * case-insensitively by name. Backend failures land as a flash error rather than blanking the
   * page.
   *
   * @param model Thymeleaf model populated with the sorted terminal list
   * @return the {@code admin/terminals} view name
   */
  @GetMapping
  public String listData(Model model) {
    try {
      PageResponse<Map<String, Object>> terminalsPage =
          backendApiClient.get(
              "/api/v1/terminals?size=10000&sort=name,asc",
              new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {});

      List<TerminalDto> terminals = new ArrayList<>();
      if (terminalsPage != null && terminalsPage.content() != null) {
        terminals =
            terminalsPage.content().stream()
                .map(
                    m ->
                        new TerminalDto(
                            parseUuid(m.get("id")),
                            parseString(m.get("name")),
                            parseString(m.get("nickname")),
                            parseString(m.get("starSystemName")),
                            parseString(m.get("planetName")),
                            parseString(m.get("cityName")),
                            parseString(m.get("spaceStationName")),
                            Boolean.TRUE.equals(m.get("hidden"))))
                .collect(Collectors.toCollection(ArrayList::new));
        terminals.sort(
            Comparator.comparing(
                t -> t.name() == null ? "" : t.name(), String.CASE_INSENSITIVE_ORDER));
      }
      model.addAttribute("terminals", terminals);

    } catch (Exception e) {
      log.error("Error loading terminals data", e);
      model.addAttribute("error", "error.admin.terminals.load");
    }
    return "admin/terminals";
  }

  /**
   * Toggles a single terminal's hidden flag.
   *
   * <p>Pulls the current record so the PUT body contains the UEX-imported display fields verbatim —
   * the backend's PUT endpoint only updates the hidden flag, but the body still has to be a full
   * {@link TerminalDto}. Any failure redirects with an error query param.
   *
   * @param id terminal id
   * @param hidden desired new hidden flag
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /admin/terminals} (optionally with {@code ?error=...})
   */
  @PostMapping("/{id}/toggle-visibility")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public String toggleTerminalVisibility(
      @PathVariable @NotNull UUID id,
      @RequestParam boolean hidden,
      RedirectAttributes redirectAttributes) {
    try {
      TerminalDto currentTerminal =
          backendApiClient.get("/api/v1/terminals/" + id, TerminalDto.class);
      TerminalDto body =
          new TerminalDto(
              id,
              currentTerminal.name(),
              currentTerminal.nickname(),
              currentTerminal.starSystemName(),
              currentTerminal.planetName(),
              currentTerminal.cityName(),
              currentTerminal.spaceStationName(),
              hidden);
      backendApiClient.put("/api/v1/terminals/" + id, body, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Toggle terminal visibility failed", e);
      return "redirect:/admin/terminals?error=ToggleVisibilityFailed";
    }
    return "redirect:/admin/terminals";
  }

  private String parseString(Object o) {
    return o == null ? null : o.toString();
  }

  private UUID parseUuid(Object o) {
    if (o == null) return null;
    try {
      return UUID.fromString(o.toString());
    } catch (Exception e) {
      return null;
    }
  }
}

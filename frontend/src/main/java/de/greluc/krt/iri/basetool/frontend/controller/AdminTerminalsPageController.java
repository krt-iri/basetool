package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.TerminalDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
                            parseNullableBoolean(m.get("hasLoadingDock")),
                            parseNullableBoolean(m.get("isAutoLoad")),
                            Boolean.TRUE.equals(m.get("hasLoadingDockOverridden")),
                            Boolean.TRUE.equals(m.get("isAutoLoadOverridden")),
                            parseNullableBoolean(m.get("uexHasLoadingDock")),
                            parseNullableBoolean(m.get("uexIsAutoLoad")),
                            parseInstant(m.get("uexSyncedAt")),
                            Boolean.TRUE.equals(m.get("hidden"))))
                .collect(Collectors.toCollection(ArrayList::new));
        terminals.sort(
            Comparator.comparing(
                t -> t.name() == null ? "" : t.name(), String.CASE_INSENSITIVE_ORDER));
      }
      // The terminal sweep stamps every row with the same instant, so the most recent
      // value across the list is also the most recent global sweep — surfacing it once
      // at the top keeps the table itself clean.
      Instant latestSync =
          terminals.stream()
              .map(TerminalDto::uexSyncedAt)
              .filter(java.util.Objects::nonNull)
              .max(Comparator.naturalOrder())
              .orElse(null);
      model.addAttribute("terminals", terminals);
      model.addAttribute("latestUexSync", latestSync);

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
              currentTerminal.hasLoadingDock(),
              currentTerminal.isAutoLoad(),
              currentTerminal.hasLoadingDockOverridden(),
              currentTerminal.isAutoLoadOverridden(),
              currentTerminal.uexHasLoadingDock(),
              currentTerminal.uexIsAutoLoad(),
              currentTerminal.uexSyncedAt(),
              hidden);
      backendApiClient.put("/api/v1/terminals/" + id, body, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Toggle terminal visibility failed", e);
      return "redirect:/admin/terminals?error=ToggleVisibilityFailed";
    }
    return "redirect:/admin/terminals";
  }

  /**
   * Sets or clears the loading-dock override on a terminal.
   *
   * <p>The {@code action} query param maps to the three button states in the admin UI:
   *
   * <ul>
   *   <li>{@code uex} → clear the admin pin so the next UEX sweep restores the value
   *   <li>{@code yes} → pin {@code hasLoadingDock} to {@code true}
   *   <li>{@code no} → pin {@code hasLoadingDock} to {@code false}
   * </ul>
   *
   * @param id terminal id
   * @param action requested state (see above)
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /admin/terminals}
   */
  @PostMapping("/{id}/loading-dock")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public String updateLoadingDockOverride(
      @PathVariable @NotNull UUID id,
      @RequestParam String action,
      RedirectAttributes redirectAttributes) {
    return dispatchOverride(
        id, action, "loading-dock", "loading-dock-override", redirectAttributes);
  }

  /**
   * Sets or clears the auto-load override on a terminal. See {@link
   * #updateLoadingDockOverride(UUID, String, RedirectAttributes)} for the {@code action} semantics.
   *
   * @param id terminal id
   * @param action requested state ({@code uex}, {@code yes}, or {@code no})
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /admin/terminals}
   */
  @PostMapping("/{id}/auto-load")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public String updateAutoLoadOverride(
      @PathVariable @NotNull UUID id,
      @RequestParam String action,
      RedirectAttributes redirectAttributes) {
    return dispatchOverride(id, action, "auto-load", "auto-load-override", redirectAttributes);
  }

  private String dispatchOverride(
      UUID id,
      String action,
      String setPath,
      String clearPath,
      RedirectAttributes redirectAttributes) {
    try {
      switch (action) {
        case "uex" ->
            backendApiClient.delete("/api/v1/terminals/" + id + "/" + clearPath, Void.class);
        case "yes" ->
            backendApiClient.patch(
                "/api/v1/terminals/" + id + "/" + setPath + "?value=true", null, Void.class);
        case "no" ->
            backendApiClient.patch(
                "/api/v1/terminals/" + id + "/" + setPath + "?value=false", null, Void.class);
        default -> {
          redirectAttributes.addFlashAttribute("errorToast", "error.admin.uex.flag.update");
          return "redirect:/admin/terminals";
        }
      }
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (BackendServiceException e) {
      log.error("Override update failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.admin.uex.flag.update");
    } catch (Exception e) {
      log.error("Override update failed", e);
      return "redirect:/admin/terminals?error=OverrideUpdateFailed";
    }
    return "redirect:/admin/terminals";
  }

  private String parseString(Object o) {
    return o == null ? null : o.toString();
  }

  private UUID parseUuid(Object o) {
    if (o == null) {
      return null;
    }
    try {
      return UUID.fromString(o.toString());
    } catch (Exception e) {
      return null;
    }
  }

  private Boolean parseNullableBoolean(Object o) {
    if (o == null) {
      return null;
    }
    if (o instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(o.toString());
  }

  private Instant parseInstant(Object o) {
    if (o == null) {
      return null;
    }
    if (o instanceof Instant i) {
      return i;
    }
    try {
      return Instant.parse(o.toString());
    } catch (Exception e) {
      return null;
    }
  }
}

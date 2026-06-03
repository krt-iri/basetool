package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.OrgChartDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserReferenceDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Spring MVC controller for the Profit-Bereich org chart page ({@code /org-chart}). The page itself
 * is open to every authenticated user (read-only view); the inline editor's write operations are
 * proxied through the AJAX endpoints below, each hard-gated to ADMIN — the class is intentionally
 * NOT class-level {@code @PreAuthorize("hasRole('ADMIN')")} so members can still view the chart.
 *
 * <p>The AJAX proxies forward the JSON body verbatim to the backend and, on a backend RFC-7807
 * failure, relay the HTTP status plus a slim {@code {code, detail}} body so the page JS can show
 * the backend's localized message in a toast (and recognise the {@code OPTIMISTIC_LOCK} code to
 * prompt a reload).
 */
@Controller
@RequestMapping("/org-chart")
@RequiredArgsConstructor
@Slf4j
public class OrgChartPageController {

  private final BackendApiClient backendApiClient;

  /**
   * Renders the org-chart page. Loads the assembled chart for everyone; additionally preloads the
   * user-lookup list for admins so the inline editor's assign picker has its options without a
   * second round-trip. Non-admins get an empty {@code allUsers} list (they never see the editor).
   *
   * @param model Thymeleaf model populated with {@code orgChart} and {@code allUsers}.
   * @param authentication the current authentication, used to decide whether to preload the picker.
   * @return the {@code org-chart} view name.
   */
  @GetMapping
  public String orgChart(Model model, Authentication authentication) {
    try {
      model.addAttribute("orgChart", backendApiClient.get("/api/v1/org-chart", OrgChartDto.class));
    } catch (Exception e) {
      log.error("Failed to load org chart", e);
      model.addAttribute("error", "error.orgChart.load");
    }
    model.addAttribute("allUsers", isAdmin(authentication) ? fetchUserLookup() : List.of());
    return "org-chart";
  }

  /**
   * Creates a new org-chart position. ADMIN-only. Relays backend validation failures (scope,
   * parent, cardinality, uniqueness) as their original status + {@code {code, detail}} body.
   *
   * @param body the create payload ({@code positionType}, {@code orgUnitId}, {@code userId}, {@code
   *     parentId}, {@code sortIndex}).
   * @return 200 with the created position on success, or the backend error status + body.
   */
  @PostMapping("/positions/ajax")
  @ResponseBody
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Object> createPosition(@RequestBody Map<String, Object> body) {
    try {
      return ResponseEntity.ok(
          backendApiClient.post("/api/v1/org-chart/positions", body, Object.class));
    } catch (BackendServiceException e) {
      return relayError("Create org-chart position failed", e);
    } catch (Exception e) {
      return unexpectedError("Create org-chart position failed", e);
    }
  }

  /**
   * Reassigns or reorders an existing position. ADMIN-only.
   *
   * @param id the position id.
   * @param body the edit payload ({@code userId}, {@code sortIndex}, {@code version}).
   * @return 200 with the updated position on success, or the backend error status + body.
   */
  @PutMapping("/positions/{id}/ajax")
  @ResponseBody
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Object> updatePosition(
      @PathVariable @NotNull UUID id, @RequestBody Map<String, Object> body) {
    try {
      return ResponseEntity.ok(
          backendApiClient.put("/api/v1/org-chart/positions/" + id, body, Object.class));
    } catch (BackendServiceException e) {
      return relayError("Update org-chart position failed", e);
    } catch (Exception e) {
      return unexpectedError("Update org-chart position failed", e);
    }
  }

  /**
   * Removes a position (cascading a Kommandoleiter's Stv. + Ensigns). ADMIN-only.
   *
   * @param id the position id.
   * @return 200 on success, or the backend error status + body.
   */
  @DeleteMapping("/positions/{id}/ajax")
  @ResponseBody
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Object> deletePosition(@PathVariable @NotNull UUID id) {
    try {
      backendApiClient.delete("/api/v1/org-chart/positions/" + id, Void.class);
      return ResponseEntity.ok().build();
    } catch (BackendServiceException e) {
      return relayError("Delete org-chart position failed", e);
    } catch (Exception e) {
      return unexpectedError("Delete org-chart position failed", e);
    }
  }

  private static boolean isAdmin(Authentication authentication) {
    return authentication != null
        && authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
  }

  private ResponseEntity<Object> relayError(String logMessage, BackendServiceException e) {
    log.warn("{}: status={}, code={}", logMessage, e.getStatusCode(), e.getProblemCode());
    Map<String, Object> payload = new HashMap<>();
    payload.put("code", e.getProblemCode());
    payload.put("detail", e.getProblemDetail());
    int status = e.getStatusCode() > 0 ? e.getStatusCode() : 500;
    return ResponseEntity.status(status).body(payload);
  }

  private ResponseEntity<Object> unexpectedError(String logMessage, Exception e) {
    log.error(logMessage, e);
    Map<String, Object> payload = new HashMap<>();
    payload.put("code", "INTERNAL_ERROR");
    return ResponseEntity.status(500).body(payload);
  }

  /**
   * Fetches the slim user-lookup list from the backend for the assign picker, sorted
   * case-insensitively by effective name. Mirrors the parsing path in {@link
   * AdminSpecialCommandsPageController}.
   *
   * @return the users; never {@code null}, possibly empty.
   */
  private List<UserReferenceDto> fetchUserLookup() {
    List<Map<String, Object>> raw =
        backendApiClient.get(
            "/api/v1/users/lookup", new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    if (raw == null) {
      return List.of();
    }
    List<UserReferenceDto> users =
        raw.stream()
            .map(
                m ->
                    new UserReferenceDto(
                        parseUuid(m.get("id")),
                        parseString(m.get("username")),
                        parseString(m.get("displayName")),
                        parseString(m.get("effectiveName")),
                        parseInt(m.get("rank"))))
            .collect(Collectors.toCollection(ArrayList::new));
    users.sort(
        Comparator.comparing(
            u -> u.effectiveName() == null ? "" : u.effectiveName(),
            String.CASE_INSENSITIVE_ORDER));
    return users;
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
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static Integer parseInt(Object o) {
    if (o == null) {
      return null;
    }
    if (o instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(o));
    } catch (NumberFormatException ignored) {
      return null;
    }
  }
}

package de.greluc.krt.iri.basetool.frontend.config;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.SquadronDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Cross-cutting advice that injects the current squadron context into every rendered model so the
 * sidebar switcher / context badge / squadron-aware columns can be rendered from the layout
 * fragments without each page controller having to load the data separately.
 *
 * <p>Populates four model attributes:
 *
 * <ul>
 *   <li>{@code activeSquadronId} — UUID of the squadron the backend currently scopes queries to, or
 *       {@code null} when an admin is in "all squadrons" mode or the user has no assigned squadron
 *       (also {@code null} for anonymous callers).
 *   <li>{@code activeSquadron} — {@link SquadronDto} resolved from the id, or {@code null} when no
 *       context applies. Used by the templates to render the shorthand badge and the dropdown
 *       selection state.
 *   <li>{@code availableSquadrons} — the full {@link SquadronDto} list the admin can switch to.
 *       Empty for non-admin / anonymous callers (they never see the switcher control).
 *   <li>{@code isAllSquadronsMode} — {@code true} when an admin is currently viewing the
 *       cross-staffel union (no active selection). Members and guests never enter this mode and
 *       always see {@code false}.
 * </ul>
 *
 * <p>Failures from the backend round-trip degrade gracefully: a non-resolvable active-squadron call
 * leaves the badge empty; a non-resolvable squadron list leaves the dropdown empty. We never let an
 * unrelated UI fail because the context advice could not reach the backend — the page would render
 * empty cells for the squadron columns but the rest of the layout stays intact.
 */
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class SquadronContextAdvice {

  private final BackendApiClient backendApiClient;

  /**
   * The {@code GET /api/v1/me/active-squadron} response body. Mirrors the backend's {@code
   * MeController.ActiveSquadronResponse} record - kept local to avoid a frontend dependency on the
   * backend module just for one wire-shape.
   *
   * @param squadronId resolved squadron UUID, or {@code null} when none applies.
   */
  public record ActiveSquadronResponse(UUID squadronId) {}

  /**
   * Resolves {@code activeSquadronId} for the current request by calling the backend's {@code
   * /me/active-squadron} GET endpoint. {@code null} when the call is anonymous, when the admin is
   * in "all squadrons" mode, or when the backend round-trip fails.
   *
   * @return the active squadron UUID, or {@code null}.
   */
  @ModelAttribute("activeSquadronId")
  public UUID activeSquadronId() {
    if (!isAuthenticated()) {
      return null;
    }
    try {
      ActiveSquadronResponse resp =
          backendApiClient.get("/api/v1/me/active-squadron", ActiveSquadronResponse.class);
      return resp != null ? resp.squadronId() : null;
    } catch (Exception ex) {
      log.debug("Failed to resolve active squadron for current request", ex);
      return null;
    }
  }

  /**
   * Resolves the full {@link SquadronDto} that matches {@link #activeSquadronId()} so the template
   * can render the shorthand badge without doing a second lookup. {@code null} when no active
   * squadron applies.
   *
   * @param activeSquadronId previously-resolved id (Spring re-injects model attributes between
   *     {@code @ModelAttribute} methods).
   * @param availableSquadrons the squadron catalogue the advice already loaded; reused to avoid a
   *     dedicated per-id GET.
   * @return matching squadron, or {@code null}.
   */
  @ModelAttribute("activeSquadron")
  public SquadronDto activeSquadron(
      @ModelAttribute("activeSquadronId") UUID activeSquadronId,
      @ModelAttribute("availableSquadrons") List<SquadronDto> availableSquadrons) {
    if (activeSquadronId == null || availableSquadrons == null) {
      return null;
    }
    return availableSquadrons.stream()
        .filter(s -> activeSquadronId.equals(s.id()))
        .findFirst()
        .orElse(null);
  }

  /**
   * Loads the squadron catalogue once per request. Returned to admins for the switcher dropdown and
   * reused by {@link #activeSquadron(UUID, List)} to dereference the active id without a second
   * round-trip. Empty list for anonymous callers or when the backend call fails - the dropdown
   * gracefully renders without options rather than 500ing the page.
   *
   * @return list of active squadrons, ordered by name; never {@code null}.
   */
  @ModelAttribute("availableSquadrons")
  public List<SquadronDto> availableSquadrons() {
    if (!isAuthenticated()) {
      return List.of();
    }
    try {
      PageResponse<SquadronDto> page =
          backendApiClient.get(
              "/api/v1/squadrons?size=1000&sort=name,asc", new ParameterizedTypeReference<>() {});
      return page != null && page.content() != null ? page.content() : List.of();
    } catch (Exception ex) {
      log.debug("Failed to load squadron list for sidebar dropdown", ex);
      return List.of();
    }
  }

  /**
   * {@code true} when the current admin is viewing the cross-staffel union (no active squadron
   * selection). False for everyone else - non-admins always operate in their persistent home
   * squadron and cannot enter this mode.
   *
   * @param activeSquadronId previously-resolved id, {@code null} signals all-squadrons mode for
   *     admins.
   * @return whether the current viewer is an admin without a selection.
   */
  @ModelAttribute("isAllSquadronsMode")
  public boolean isAllSquadronsMode(@ModelAttribute("activeSquadronId") UUID activeSquadronId) {
    return isAdmin() && activeSquadronId == null;
  }

  /**
   * The request URI the sidebar switcher form posts back as {@code _referer} so the redirect after
   * the squadron change lands the user on the same page they were on. We resolve it via a model
   * attribute rather than the Thymeleaf {@code #httpServletRequest} utility because the latter is
   * not exposed in every render context (MockMvc tests in particular).
   *
   * @param request the current HTTP servlet request injected by Spring; never {@code null}.
   * @return the path + query of the current request, or {@code "/"} as a defensive fallback.
   */
  @ModelAttribute("currentRequestUri")
  public String currentRequestUri(HttpServletRequest request) {
    if (request == null) {
      return "/";
    }
    String uri = request.getRequestURI();
    String query = request.getQueryString();
    if (uri == null || uri.isBlank()) {
      return "/";
    }
    return query != null && !query.isBlank() ? uri + "?" + query : uri;
  }

  private boolean isAuthenticated() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
  }

  private boolean isAdmin() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return false;
    }
    return auth.getAuthorities().stream().map(Object::toString).anyMatch("ROLE_ADMIN"::equals);
  }
}

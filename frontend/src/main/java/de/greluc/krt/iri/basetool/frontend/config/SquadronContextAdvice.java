package de.greluc.krt.iri.basetool.frontend.config;

import de.greluc.krt.iri.basetool.frontend.controller.MeFrontendController;
import de.greluc.krt.iri.basetool.frontend.model.dto.OrgUnitMembershipOptionDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.SquadronDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.FrontendAuthHelperService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.ParameterizedTypeReference;
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
  private final MessageSource messageSource;
  private final FrontendAuthHelperService authHelper;

  /**
   * Resolves {@code activeSquadronId} for the current request. Two different paths because the
   * state lives in different places for admins and members:
   *
   * <ul>
   *   <li>Admin: read the switcher selection from the frontend's Redis-backed Spring Session (set
   *       by {@link MeFrontendController}). {@code null} means "all squadrons" mode.
   *   <li>Non-admin: the user's persistent home squadron from {@code app_user.squadron_id} on the
   *       backend. The {@code GET /api/v1/me/active-squadron} endpoint already resolves this for
   *       the current principal; we reuse it instead of duplicating the lookup on the frontend.
   * </ul>
   *
   * <p>Anonymous callers return {@code null}; the failure of the backend round-trip degrades
   * silently to {@code null} so an unrelated UI never breaks because of this advice.
   *
   * @param request the current HTTP servlet request; never {@code null}.
   * @return the active squadron UUID, or {@code null}.
   */
  @ModelAttribute("activeSquadronId")
  public UUID activeSquadronId(HttpServletRequest request) {
    if (!authHelper.isAuthenticated()) {
      return null;
    }
    HttpSession session = request.getSession(false);
    if (session != null) {
      // R5.e: prefer the new session key; fall back to the legacy one so admin sessions
      // stored under the old name during deploy keep working. Same dual-read pattern as
      // ActiveSquadronContextFilter.
      UUID fromSession =
          de.greluc.krt.iri.basetool.frontend.logging.ActiveSquadronContext.coerce(
              session.getAttribute(MeFrontendController.ACTIVE_ORG_UNIT_SESSION_KEY));
      if (fromSession == null) {
        fromSession =
            de.greluc.krt.iri.basetool.frontend.logging.ActiveSquadronContext.coerce(
                session.getAttribute(MeFrontendController.ACTIVE_SQUADRON_SESSION_KEY));
      }
      if (fromSession != null) {
        return fromSession;
      }
    }
    if (authHelper.isAdmin()) {
      // Admin without an active session pin → all-scopes mode, no badge.
      return null;
    }
    try {
      ActiveSquadronResponse resp =
          backendApiClient.get("/api/v1/me/active-squadron", ActiveSquadronResponse.class);
      return resp != null ? resp.squadronId() : null;
    } catch (Exception ex) {
      log.debug("Failed to resolve home squadron for non-admin caller", ex);
      return null;
    }
  }

  /**
   * Wire-shape mirror of the backend's {@code MeController.ActiveSquadronResponse} record. Kept
   * local to avoid a frontend dependency on the backend module just for one JSON envelope.
   *
   * @param squadronId resolved squadron UUID, or {@code null} when none applies.
   */
  public record ActiveSquadronResponse(UUID squadronId) {}

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
    if (!authHelper.isAuthenticated()) {
      return List.of();
    }
    // R5.e: kept identical to the pre-R5.e semantics — load the full Squadron catalogue for
    // every authenticated caller. The {@link #activeSquadron} dereference and the per-squadron
    // {@code promotionEnabled} gate downstream both read from this list, so a non-admin narrowing
    // would break the {@code SquadronContextAdvice.activeSquadron} resolution for non-admin
    // pages. The new sidebar switcher reads {@link #availableOrgUnits} instead — the two
    // attributes coexist with disjoint purposes.
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
   * R5.e — list of {@link OrgUnitMembershipOptionDto} that the caller can switch their active scope
   * to. Replaces {@link #availableSquadrons()} for the post-R5.e sidebar switcher: admins see the
   * full Squadron + SpecialCommand catalogue; non-admins see only the OrgUnits they are a member of
   * (Staffel + every SK membership). The switcher template hides itself when this list has fewer
   * than two entries — no choice to offer means no UI noise (plan §7.2).
   *
   * <p>Backend round-trips:
   *
   * <ul>
   *   <li>Admin: still uses {@code /api/v1/squadrons} (Staffel-only today) merged with {@code
   *       /api/v1/special-commands}. Both calls are paginated to {@code size=1000} which is
   *       generous for the foreseeable OrgUnit population.
   *   <li>Non-admin: uses the lean {@code /api/v1/users/{id}/memberships} that R5.d.a introduced
   *       for the picker fragment. Reuses the existing wire shape so no new endpoint is needed.
   * </ul>
   *
   * <p>Failures degrade silently to an empty list — the switcher then hides itself rather than
   * 500'ing the sidebar render.
   *
   * @return the OrgUnit options visible in the switcher; never {@code null}.
   */
  @ModelAttribute("availableOrgUnits")
  public List<OrgUnitMembershipOptionDto> availableOrgUnits() {
    if (!authHelper.isAuthenticated()) {
      return List.of();
    }
    if (authHelper.isAdmin()) {
      return loadAdminOrgUnitCatalogue();
    }
    return loadCallerMemberships();
  }

  /**
   * Admin path of {@link #availableOrgUnits()} — concatenates the Squadron catalogue with the
   * SpecialCommand catalogue. Both lists are paginated server-side; we ask for {@code size=1000}
   * which exceeds the foreseeable OrgUnit count by an order of magnitude.
   *
   * @return Squadron + SK catalogue, never {@code null}.
   */
  private List<OrgUnitMembershipOptionDto> loadAdminOrgUnitCatalogue() {
    java.util.List<OrgUnitMembershipOptionDto> combined = new java.util.ArrayList<>();
    try {
      PageResponse<SquadronDto> squadrons =
          backendApiClient.get(
              "/api/v1/squadrons?size=1000&sort=name,asc", new ParameterizedTypeReference<>() {});
      if (squadrons != null && squadrons.content() != null) {
        for (SquadronDto s : squadrons.content()) {
          combined.add(new OrgUnitMembershipOptionDto(s.id(), s.name(), s.shorthand(), "SQUADRON"));
        }
      }
    } catch (Exception ex) {
      log.debug("Failed to load Squadron catalogue for admin switcher", ex);
    }
    try {
      PageResponse<SquadronDto> specialCommands =
          backendApiClient.get(
              "/api/v1/special-commands?size=1000&sort=name,asc",
              new ParameterizedTypeReference<>() {});
      if (specialCommands != null && specialCommands.content() != null) {
        for (SquadronDto sk : specialCommands.content()) {
          combined.add(
              new OrgUnitMembershipOptionDto(
                  sk.id(), sk.name(), sk.shorthand(), "SPECIAL_COMMAND"));
        }
      }
    } catch (Exception ex) {
      log.debug("Failed to load SpecialCommand catalogue for admin switcher", ex);
    }
    return combined;
  }

  /**
   * Non-admin path of {@link #availableOrgUnits()} — reads the caller's memberships via the lean
   * R5.d.a endpoint. One round-trip to {@code /api/v1/users/me} to resolve the principal's id, then
   * one to {@code /api/v1/users/{id}/memberships}; the second call already returns the Staffel +
   * every SK membership in one shot.
   *
   * @return the caller's memberships, never {@code null}.
   */
  private List<OrgUnitMembershipOptionDto> loadCallerMemberships() {
    try {
      UserDto me = backendApiClient.get("/api/v1/users/me", UserDto.class);
      if (me == null || me.id() == null) {
        return List.of();
      }
      List<OrgUnitMembershipOptionDto> memberships =
          backendApiClient.get(
              "/api/v1/users/" + me.id() + "/memberships", new ParameterizedTypeReference<>() {});
      return memberships != null ? memberships : List.of();
    } catch (Exception ex) {
      log.debug("Failed to load memberships for non-admin switcher", ex);
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
    return authHelper.isAdmin() && activeSquadronId == null;
  }

  /**
   * Composes the dynamic application title for the {@code <title>} tag and the sidebar logo: a
   * plain "Basetool" when no squadron context applies, "Basetool – &lt;shorthand&gt;" when a
   * concrete squadron is active, and "Basetool – Alle Staffeln" when an admin is in the
   * cross-staffel view. Replaces the previous hardcoded "IRIDIUM Basetool" title
   * (MULTI_SQUADRON_PLAN.md section 5.4: app.title generic or dynamic).
   *
   * <p>Resolution uses the request locale via {@link LocaleContextHolder} so the squadron suffix is
   * localised consistently with the rest of the page (the message-format pattern {@code {0}} is
   * filled with the squadron shorthand or the localised "all squadrons" label).
   *
   * @param activeSquadron resolved squadron, or {@code null}.
   * @param isAllSquadronsMode whether the current viewer is an admin without a selection.
   * @return the rendered title string, never {@code null}.
   */
  @ModelAttribute("appTitle")
  public String appTitle(
      @ModelAttribute("activeSquadron") SquadronDto activeSquadron,
      @ModelAttribute("isAllSquadronsMode") boolean isAllSquadronsMode) {
    Locale locale = LocaleContextHolder.getLocale();
    if (activeSquadron != null) {
      return messageSource.getMessage(
          "app.title.with.squadron", new Object[] {activeSquadron.shorthand()}, locale);
    }
    if (isAllSquadronsMode) {
      String allLabel = messageSource.getMessage("squadron.switcher.all", null, locale);
      return messageSource.getMessage("app.title.all.squadrons", new Object[] {allLabel}, locale);
    }
    return messageSource.getMessage("app.title", null, locale);
  }

  /**
   * Computes whether the promotion subsystem is exposed to the current caller. Admins always see
   * the menu (regardless of the per-squadron flag) so they can re-enable a squadron that locked
   * itself out; for everyone else the flag stored on {@link SquadronDto#isPromotionEnabled()} of
   * their active squadron decides. {@code null} active squadron (anonymous caller, member without
   * squadron) defaults to {@code true} — the squadron-scope filter in the backend already returns
   * empty lists for them so a hidden menu would be redundant and inconsistent with the rest of the
   * sidebar.
   *
   * <p>The sidebar's {@code Beförderung} section reads this attribute via {@code
   * th:if="${promotionFeatureEnabled}"}, and every {@code PromotionPageController} {@code
   * GetMapping} blocks the request with HTTP 403 when it resolves to {@code false}.
   *
   * @param activeSquadron previously-resolved squadron mini-record, or {@code null}.
   * @return {@code true} when the promotion menu should be exposed; {@code false} when it must be
   *     hidden / blocked.
   */
  @ModelAttribute("promotionFeatureEnabled")
  public boolean promotionFeatureEnabled(
      @ModelAttribute("activeSquadron") SquadronDto activeSquadron) {
    if (authHelper.isAdmin()) {
      return true;
    }
    if (activeSquadron == null || activeSquadron.isPromotionEnabled() == null) {
      return true;
    }
    return activeSquadron.isPromotionEnabled();
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
}

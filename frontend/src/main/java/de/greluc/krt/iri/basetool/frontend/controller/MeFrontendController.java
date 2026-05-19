package de.greluc.krt.iri.basetool.frontend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Owner of the admin's per-user "active squadron" preference. The state lives in the frontend's
 * Redis-backed Spring Session — NOT in the backend — because backend REST calls relay only the
 * OAuth2 bearer token and no session cookies, so a backend-side {@code HttpSession.setAttribute}
 * would be lost as soon as the response returned. The active squadron is propagated to the backend
 * on every API call via the {@code X-Active-Squadron-Id} request header (see {@code
 * ActiveSquadronRelayFilter}); the backend trusts the header for ADMIN principals and ignores it
 * for everyone else.
 *
 * <p>Admin-only — regular members always operate in their persistent home squadron and have no
 * switcher to call.
 */
@Controller
@RequestMapping("/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class MeFrontendController {

  /**
   * HTTP session attribute name under which the admin's currently selected squadron lives. Mirrored
   * on the backend's {@code SquadronScopeService} so the two sides agree on the wire vocabulary;
   * the actual storage is the frontend's Spring Session (Redis-backed in dev/prod), NOT the
   * backend's request-scoped session.
   */
  public static final String ACTIVE_SQUADRON_SESSION_KEY = "iridium.activeSquadronId";

  /**
   * Sets or clears the admin's active squadron selection in the frontend session. {@code
   * squadronId} blank/empty clears the selection (admin returns to "all squadrons" mode); a
   * non-blank UUID activates that squadron. The redirect makes the next page render see the new
   * context via {@code SquadronContextAdvice}; the backend learns about the change on the next API
   * call through the {@code X-Active-Squadron-Id} header.
   *
   * @param squadronId the squadron to activate, blank/null to clear; never silently rejected.
   * @param referer optional referer field used to redirect back; defaults to {@code /} when
   *     missing.
   * @param request HTTP request injected by Spring; never {@code null}.
   * @param redirectAttributes flash attribute carrier for the success toast.
   * @return redirect view to the referring page so the next render sees the new context.
   */
  @PostMapping("/active-squadron")
  public RedirectView setActiveSquadron(
      @RequestParam(value = "squadronId", required = false) @Nullable String squadronId,
      @RequestParam(value = "_referer", required = false) @Nullable String referer,
      HttpServletRequest request,
      RedirectAttributes redirectAttributes) {
    HttpSession session = request.getSession(true);
    if (squadronId == null || squadronId.isBlank()) {
      session.removeAttribute(ACTIVE_SQUADRON_SESSION_KEY);
      redirectAttributes.addFlashAttribute("toastSuccess", "squadron.switcher.cleared");
    } else {
      // Store as the UUID's canonical string form so the Redis-backed Spring Session can
      // round-trip the value without serializer ambiguity. Spring Session's default
      // JdkSerializationRedisSerializer plus the JSON wrapper in some configurations can
      // change a UUID instance into a String on deserialization — storing the String
      // representation up front avoids that brittleness and matches how the readers parse
      // it back.
      UUID parsed = UUID.fromString(squadronId.trim());
      session.setAttribute(ACTIVE_SQUADRON_SESSION_KEY, parsed.toString());
      redirectAttributes.addFlashAttribute("toastSuccess", "squadron.switcher.activated");
    }
    return new RedirectView(referer != null && !referer.isBlank() ? referer : "/");
  }
}

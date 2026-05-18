package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Frontend proxy for the per-user "active squadron" preference. Browsers cannot talk to the backend
 * directly (cross-origin and the backend's bearer-token relay sits in the frontend) so the sidebar
 * squadron switcher posts here; this controller forwards the choice through {@link
 * BackendApiClient} to {@code PUT/DELETE /api/v1/me/active-squadron} and redirects back to the
 * referring page so the next render reflects the new context. Admin-only - regular members always
 * operate in their persistent home squadron and have no switcher to call.
 */
@Controller
@RequestMapping("/me")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class MeFrontendController {

  private final BackendApiClient backendApiClient;

  /**
   * Sets or clears the admin's active squadron selection. {@code squadronId} blank/empty clears the
   * selection (admin returns to "all squadrons" mode); a non-blank UUID activates that squadron.
   *
   * @param squadronId the squadron to activate, blank/null to clear; never silently rejected.
   * @param referer optional referer header used to redirect back; defaults to {@code /} when
   *     missing.
   * @param redirectAttributes flash attribute carrier for the success toast.
   * @return redirect view to the referring page so the next render sees the new context.
   */
  @PostMapping("/active-squadron")
  public RedirectView setActiveSquadron(
      @RequestParam(value = "squadronId", required = false) @Nullable String squadronId,
      @RequestParam(value = "_referer", required = false) @Nullable String referer,
      RedirectAttributes redirectAttributes) {
    if (squadronId == null || squadronId.isBlank()) {
      backendApiClient.delete("/api/v1/me/active-squadron", Void.class);
      redirectAttributes.addFlashAttribute("toastSuccess", "squadron.switcher.cleared");
    } else {
      UUID parsed = UUID.fromString(squadronId.trim());
      backendApiClient.put(
          "/api/v1/me/active-squadron", new SetActiveSquadronBody(parsed), Void.class);
      redirectAttributes.addFlashAttribute("toastSuccess", "squadron.switcher.activated");
    }
    return new RedirectView(referer != null && !referer.isBlank() ? referer : "/");
  }

  /**
   * Body forwarded to the backend's {@code PUT /api/v1/me/active-squadron}. Mirrors the backend's
   * inner record so the JSON serialisation stays stable across module boundaries (see {@code
   * MeController.SetActiveSquadronRequest}).
   *
   * @param squadronId UUID of the squadron to activate; required.
   */
  public record SetActiveSquadronBody(UUID squadronId) {}
}

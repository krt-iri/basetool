package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC controller for the admin announcement-management page ({@code /admin/announcement}).
 *
 * <p>The announcement is a single shared record across the squadron — the page reads it via the
 * {@code /admin} endpoint (returns the record even when no public announcement is currently
 * published) and exposes Create/Update/Delete actions. PUT carries an optimistic-lock version so a
 * second admin editing the same announcement concurrently sees a 409 toast rather than silently
 * overwriting the other person's text.
 */
@Controller
@RequestMapping("/admin/announcement")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
public class AdminAnnouncementPageController {

  private final BackendApiClient backendApiClient;

  /**
   * Loads the current admin-view announcement record. A backend failure is logged but the page
   * still renders so the admin can post a new announcement from the empty form.
   *
   * @param model Thymeleaf model populated with {@code adminAnnouncement} (raw JSON map)
   * @return the {@code admin/announcement} view name
   */
  @GetMapping
  public String showAnnouncementPage(Model model) {
    try {
      Map<String, Object> adminAnnouncement =
          backendApiClient.get(
              "/api/v1/announcement/admin",
              new ParameterizedTypeReference<Map<String, Object>>() {});
      model.addAttribute("adminAnnouncement", adminAnnouncement);
    } catch (Exception e) {
      log.error("Could not fetch admin announcement", e);
    }
    return "admin/announcement";
  }

  /**
   * Updates the shared announcement.
   *
   * <p>{@code version} is optional ({@code null} = first-time create); a 409 with problem type
   * {@code concurrency-conflict} surfaces as a dedicated optimistic-lock toast.
   *
   * @param content new announcement text (raw)
   * @param version optimistic-lock version, may be {@code null} on first creation
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /admin/announcement}
   */
  @PostMapping("/update")
  public String updateAnnouncement(
      @RequestParam String content,
      @RequestParam(required = false) Long version,
      RedirectAttributes redirectAttributes) {
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("content", content);
      body.put("version", version);

      backendApiClient.put("/api/v1/announcement", body, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (BackendServiceException e) {
      log.error("Update announcement failed", e);
      if (e.getStatusCode() == 409 && "concurrency-conflict".equals(e.getProblemType())) {
        redirectAttributes.addFlashAttribute("errorToast", "error.concurrency.conflict");
      } else {
        redirectAttributes.addFlashAttribute("errorToast", "error.profile.update.failed");
      }
      return "redirect:/admin/announcement";
    } catch (Exception e) {
      log.error("Update announcement failed", e);
      return "redirect:/admin/announcement?error=UpdateFailed";
    }
    return "redirect:/admin/announcement";
  }

  /**
   * Removes the current announcement entirely. Failure redirects with an error query param.
   *
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /admin/announcement} (optionally with {@code ?error=...})
   */
  @PostMapping("/delete")
  public String deleteAnnouncement(RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/announcement", Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.delete");
    } catch (Exception e) {
      log.error("Delete announcement failed", e);
      return "redirect:/admin/announcement?error=DeleteFailed";
    }
    return "redirect:/admin/announcement";
  }
}

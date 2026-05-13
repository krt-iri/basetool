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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/announcement")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
public class AdminAnnouncementPageController {

  private final BackendApiClient backendApiClient;

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

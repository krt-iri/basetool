package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
@Slf4j
public class HomeController {

  @Value("${app.ui.notification-duration:5000}")
  private long notificationDuration;

  private final BackendApiClient backendApiClient;

  @GetMapping("/")
  public String home(
      Model model, @AuthenticationPrincipal OidcUser principal, HttpSession session) {
    // Fetch Next Mission
    try {
      Map<String, Object> nextMission =
          backendApiClient.get(
              "/api/v1/missions/next",
              new ParameterizedTypeReference<Map<String, Object>>() {},
              principal == null);
      model.addAttribute("nextMission", nextMission);
    } catch (BackendServiceException e) {
      if (e.getStatusCode() != 204) {
        // Log or handle error if strictly needed, otherwise just no mission
        log.error("Error fetching missions", e);
        model.addAttribute("error", "error.mission.fetch");
      }
    } catch (Exception e) {
      log.error("Could not fetch mission details", e);
      model.addAttribute("error", "error.mission.fetch.details");
    }

    if (principal != null) {
      model.addAttribute("username", principal.getPreferredUsername());

      if (session.getAttribute("welcomeMessageShown") == null) {
        model.addAttribute("showLoginNotification", true);
        model.addAttribute("notificationDuration", notificationDuration);
        session.setAttribute("welcomeMessageShown", true);
      }

      try {
        de.greluc.krt.iri.basetool.frontend.model.dto.UserDto currentUser =
            backendApiClient.get(
                "/api/v1/users/me", de.greluc.krt.iri.basetool.frontend.model.dto.UserDto.class);
        model.addAttribute("currentUser", currentUser);

        // Fetch Public Announcement
        Map<String, Object> announcement =
            backendApiClient.get(
                "/api/v1/announcement", new ParameterizedTypeReference<Map<String, Object>>() {});
        model.addAttribute("announcement", announcement);

        boolean unread = false;
        if (announcement != null && announcement.containsKey("id")) {
          String announcementId = (String) announcement.get("id");
          if (currentUser.lastReadAnnouncementId() == null
              || !currentUser.lastReadAnnouncementId().toString().equals(announcementId)) {
            unread = true;
          }
        }
        model.addAttribute("unreadAnnouncement", unread);
      } catch (Exception e) {
        // Ignore if no announcement or error
      }
    }
    return "index";
  }

  @org.springframework.web.bind.annotation.PostMapping("/announcement/read")
  public String markAnnouncementAsRead(
      @org.springframework.web.bind.annotation.RequestParam String id) {
    try {
      backendApiClient.put("/api/v1/users/me/read-announcement/" + id, null, Void.class);
    } catch (Exception e) {
      log.error("Failed to mark announcement as read", e);
    }
    return "redirect:/";
  }
}

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

/**
 * Spring MVC controller for the home page.
 *
 * <p>Renders {@code /} for both guests and authenticated users. The page always shows the next
 * mission (a guest-visible endpoint) plus, for authenticated users, the current user record and the
 * active announcement with an unread flag. The first authenticated render in a session also
 * surfaces a transient login-notification toast (session attribute {@code welcomeMessageShown}
 * prevents the toast from re-appearing on every refresh).
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class HomeController {

  @Value("${app.ui.notification-duration:5000}")
  private long notificationDuration;

  private final BackendApiClient backendApiClient;

  /**
   * Renders the home page. Pulls the next mission for everyone; for authenticated users also
   * fetches the {@code /me} user record and the public announcement, computes the unread flag by
   * comparing the announcement id to {@code lastReadAnnouncementId}, and arms the once-per-session
   * welcome toast.
   *
   * @param model Thymeleaf model populated with mission, announcement, username and toast flags
   * @param principal authenticated OIDC user, or {@code null} for guests
   * @param session HTTP session used to gate the welcome toast to the first render after login
   * @return the {@code index} view name
   */
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

  /**
   * Marks the given announcement as read for the current user by delegating to {@code PUT
   * /api/v1/users/me/read-announcement/{id}}. Backend failures are logged but swallowed — a failed
   * mark-as-read must not block navigation back to the home page.
   *
   * @param id announcement id to mark as read
   * @return redirect back to {@code /}
   */
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

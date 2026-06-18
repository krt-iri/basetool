/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.frontend.controller;

import de.greluc.krt.profit.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.profit.basetool.frontend.service.BackendServiceException;
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
        de.greluc.krt.profit.basetool.frontend.model.dto.UserDto currentUser =
            backendApiClient.get(
                "/api/v1/users/me", de.greluc.krt.profit.basetool.frontend.model.dto.UserDto.class);
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

  /**
   * AJAX variant of {@link #markAnnouncementAsRead}: marks the announcement read and answers with a
   * bare 200 so the home page removes the "mark read" control in place (epic #571, REQ-FE-005)
   * instead of reloading. Selected over the redirect handler only when the request carries {@code
   * X-Requested-With: XMLHttpRequest}; a script-disabled browser still posts the HTML form and
   * lands on the redirect handler, which stays the no-JS fallback. A backend failure answers 502
   * (the client leaves the control in place) — a failed mark-as-read must never break the page.
   *
   * @param id announcement id to mark as read
   * @return {@code 200} on success, {@code 502} on a backend failure
   */
  @org.springframework.web.bind.annotation.PostMapping(
      value = "/announcement/read",
      headers = "X-Requested-With=XMLHttpRequest")
  @org.springframework.web.bind.annotation.ResponseBody
  public org.springframework.http.ResponseEntity<Void> markAnnouncementAsReadAjax(
      @org.springframework.web.bind.annotation.RequestParam String id) {
    try {
      backendApiClient.put("/api/v1/users/me/read-announcement/" + id, null, Void.class);
      return org.springframework.http.ResponseEntity.ok().build();
    } catch (Exception e) {
      log.error("Failed to mark announcement as read", e);
      return org.springframework.http.ResponseEntity.status(502).build();
    }
  }
}

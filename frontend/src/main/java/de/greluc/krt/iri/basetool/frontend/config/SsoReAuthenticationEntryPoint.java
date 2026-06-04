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

package de.greluc.krt.iri.basetool.frontend.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Custom {@link AuthenticationEntryPoint} that attempts a silent Keycloak SSO re-authentication
 * before falling back to the standard login page.
 *
 * <p>When a user's Spring session expires (e.g. after a session timeout), this entry point first
 * redirects the browser to the OAuth2 authorization endpoint with {@code prompt=none}. Keycloak
 * will then transparently re-authenticate the user using its own SSO session cookie (which lives in
 * the browser independently of the Spring session). If the Keycloak SSO session is still active,
 * the user is re-authenticated without any visible login prompt. If the Keycloak SSO session has
 * also expired, Keycloak returns an {@code interaction_required} error and the user is redirected
 * to the normal login page.
 *
 * <p>Bot and scanner requests are handled upstream by {@link BotProtectionFilter} before reaching
 * this entry point, so this class only deals with genuine unauthenticated user requests.
 *
 * <p>A short-lived cookie ({@code SSO_ATTEMPTED}) is used to prevent infinite redirect loops: if a
 * silent re-auth attempt has already been made for this request cycle, the entry point falls back
 * directly to the standard OAuth2 login flow (which will show the Keycloak login page if the SSO
 * session has also expired).
 *
 * <p>Note: With a persistent Redis-backed Spring Session store, this entry point is only triggered
 * for genuinely expired sessions, not after service restarts.
 */
@Component
@Slf4j
public class SsoReAuthenticationEntryPoint implements AuthenticationEntryPoint {

  static final String SSO_ATTEMPTED_COOKIE = "SSO_ATTEMPTED";
  private static final String OAUTH2_AUTHORIZATION_BASE = "/oauth2/authorization/keycloak";

  @Override
  public void commence(
      @NotNull HttpServletRequest request,
      @NotNull HttpServletResponse response,
      @NotNull AuthenticationException authException)
      throws IOException {

    String uri = request.getRequestURI();

    if (isSsoAlreadyAttempted(request)) {
      // Silent re-auth already tried and failed – fall back to interactive login
      log.info(
          "[SSO] Silent re-auth already attempted and failed, falling back to interactive login."
              + " URI={} | remoteAddr={}",
          uri,
          request.getRemoteAddr());
      clearSsoAttemptedCookie(response);
      response.sendRedirect(request.getContextPath() + OAUTH2_AUTHORIZATION_BASE);
      return;
    }

    log.info(
        "[SSO] No active session found, attempting silent Keycloak SSO re-authentication. URI={} |"
            + " remoteAddr={} | sessionId={}",
        uri,
        request.getRemoteAddr(),
        request.getSession(false) != null ? request.getSession(false).getId() : "none");

    // Mark that a silent SSO attempt is in progress (prevents redirect loops)
    setSsoAttemptedCookie(response);

    // Redirect to Keycloak with prompt=none for silent re-authentication.
    // Spring Security's SavedRequestAwareAuthenticationSuccessHandler will restore
    // the original request URI after successful authentication.
    String redirectUrl = request.getContextPath() + OAUTH2_AUTHORIZATION_BASE + "?prompt=none";

    log.debug("[SSO] Redirecting to silent SSO endpoint: {}", redirectUrl);
    response.sendRedirect(redirectUrl);
  }

  private boolean isSsoAlreadyAttempted(@NotNull HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return false;
    }
    for (Cookie cookie : cookies) {
      if (SSO_ATTEMPTED_COOKIE.equals(cookie.getName()) && "1".equals(cookie.getValue())) {
        return true;
      }
    }
    return false;
  }

  private void setSsoAttemptedCookie(@NotNull HttpServletResponse response) {
    log.debug("[SSO] Setting SSO_ATTEMPTED cookie to prevent redirect loop");
    Cookie cookie = new Cookie(SSO_ATTEMPTED_COOKIE, "1");
    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setPath("/");
    cookie.setMaxAge(60); // expires after 60 seconds – only needed for the redirect cycle
    response.addCookie(cookie);
  }

  private void clearSsoAttemptedCookie(@NotNull HttpServletResponse response) {
    log.debug("[SSO] Clearing SSO_ATTEMPTED cookie");
    Cookie cookie = new Cookie(SSO_ATTEMPTED_COOKIE, "");
    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setPath("/");
    cookie.setMaxAge(0);
    response.addCookie(cookie);
  }
}

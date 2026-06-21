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

package de.greluc.krt.profit.basetool.frontend.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
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
    // SameSite=Strict to match the session / XSRF cookies (security audit gap-fill); jakarta's
    // Cookie has no SameSite setter, so emit a ResponseCookie Set-Cookie header. 60s max-age — only
    // needed for the redirect cycle.
    writeSsoAttemptedCookie(response, "1", 60);
  }

  private void clearSsoAttemptedCookie(@NotNull HttpServletResponse response) {
    log.debug("[SSO] Clearing SSO_ATTEMPTED cookie");
    writeSsoAttemptedCookie(response, "", 0);
  }

  /**
   * Emits the {@code SSO_ATTEMPTED} cookie as a {@link ResponseCookie} {@code Set-Cookie} header
   * with {@code Secure}, {@code HttpOnly} and {@code SameSite=Strict} — matching the rest of the
   * app's cookie posture (the servlet {@link Cookie} API cannot set {@code SameSite}). A {@code
   * maxAge} of {@code 0} clears the cookie.
   *
   * @param response the servlet response to write the {@code Set-Cookie} header on
   * @param value the cookie value ({@code "1"} to set, {@code ""} to clear)
   * @param maxAgeSeconds the cookie max-age in seconds ({@code 0} to expire immediately)
   */
  private void writeSsoAttemptedCookie(
      @NotNull HttpServletResponse response, @NotNull String value, long maxAgeSeconds) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(SSO_ATTEMPTED_COOKIE, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/")
            .maxAge(maxAgeSeconds)
            .build()
            .toString());
  }
}

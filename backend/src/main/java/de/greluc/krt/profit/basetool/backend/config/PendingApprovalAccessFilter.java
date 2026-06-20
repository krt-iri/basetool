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

package de.greluc.krt.profit.basetool.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Backend enforcement of "a PENDING/REJECTED registration has no access" (REQ-SEC-017).
 *
 * <p>{@link CustomJwtGrantedAuthoritiesConverter} already short-circuits a non-approved user to the
 * single authority {@code ROLE_PENDING_APPROVAL}, but such a user is still <em>authenticated</em> —
 * so the many writes gated only on {@code @PreAuthorize("isAuthenticated()")} (e.g. personal-
 * inventory create/delete) would otherwise be reachable by a pending user calling the API directly,
 * bypassing the frontend's waiting-page redirect (which is UX, not the access boundary). This
 * filter closes that gap: any authenticated caller whose sole authority is the pending marker is
 * refused with {@code 403} on every {@code /api/**} endpoint, with one deliberate exception — the
 * registration-status endpoint the frontend reads to route them to the waiting page.
 *
 * <p>Runs after the bearer-token authentication filter (so the authorities are already assembled)
 * and is a no-op for every approved/role-bearing user, so it adds no risk to the normal path.
 */
public class PendingApprovalAccessFilter extends OncePerRequestFilter {

  /** The synthetic authority a PENDING/REJECTED user carries (and nothing else). */
  static final String PENDING_AUTHORITY = "ROLE_PENDING_APPROVAL";

  /** The only {@code /api} endpoint a pending user may reach (drives the waiting-page routing). */
  static final String SELF_STATUS_PATH = "/api/v1/users/me/registration-status";

  @Override
  protected void doFilterInternal(
      @NotNull HttpServletRequest request,
      @NotNull HttpServletResponse response,
      @NotNull FilterChain filterChain)
      throws ServletException, IOException {
    if (isBlockedPendingApiCall(request)) {
      writeForbidden(request, response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private boolean isBlockedPendingApiCall(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (!path.startsWith("/api/") || path.equals(SELF_STATUS_PATH)) {
      return false;
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null
        && auth.isAuthenticated()
        && auth.getAuthorities().stream()
            .anyMatch(authority -> PENDING_AUTHORITY.equals(authority.getAuthority()));
  }

  private void writeForbidden(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/problem+json");
    response.setCharacterEncoding("UTF-8");
    String instance = request.getRequestURI().replace("\\", "\\\\").replace("\"", "\\\"");
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
                + "\"detail\":\"Account is pending admin approval.\","
                + "\"code\":\"PENDING_APPROVAL\",\"instance\":\""
                + instance
                + "\"}");
  }
}

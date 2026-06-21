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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Unit tests for {@link PendingApprovalAccessFilter} (PR review #1: REQ-SEC-017 backend gate). */
class PendingApprovalAccessFilterTest {

  private final PendingApprovalAccessFilter filter = new PendingApprovalAccessFilter();

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private void authenticateWith(String... authorities) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "user",
                "n/a",
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
  }

  private MockHttpServletResponse run(String method, String uri, FilterChain chain)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setRequestURI(uri);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);
    return response;
  }

  @Test
  void pendingUser_isForbiddenOnApiWrite() throws Exception {
    authenticateWith(PendingApprovalAccessFilter.PENDING_AUTHORITY);
    FilterChain chain = mock(FilterChain.class);

    MockHttpServletResponse response = run("POST", "/api/v1/inventory", chain);

    assertEquals(403, response.getStatus());
    assertTrue(response.getContentType().contains("application/problem+json"));
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void pendingUser_mayReadOwnRegistrationStatus() throws Exception {
    authenticateWith(PendingApprovalAccessFilter.PENDING_AUTHORITY);
    FilterChain chain = mock(FilterChain.class);

    MockHttpServletResponse response =
        run("GET", PendingApprovalAccessFilter.SELF_STATUS_PATH, chain);

    assertEquals(200, response.getStatus());
    verify(chain).doFilter(any(), any());
  }

  @Test
  void approvedUser_passesThrough() throws Exception {
    authenticateWith("ROLE_SQUADRON_MEMBER", "HANGAR_WRITE");
    FilterChain chain = mock(FilterChain.class);

    MockHttpServletResponse response = run("POST", "/api/v1/inventory", chain);

    assertEquals(200, response.getStatus());
    verify(chain).doFilter(any(), any());
  }

  @Test
  void pendingUser_nonApiPath_passesThrough() throws Exception {
    authenticateWith(PendingApprovalAccessFilter.PENDING_AUTHORITY);
    FilterChain chain = mock(FilterChain.class);

    MockHttpServletResponse response = run("GET", "/actuator/health", chain);

    assertEquals(200, response.getStatus());
    verify(chain).doFilter(any(), any());
  }
}

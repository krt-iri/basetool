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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.profit.basetool.frontend.model.dto.UserDto;
import de.greluc.krt.profit.basetool.frontend.service.BackendApiClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Unit tests for {@link BackendRoleSyncFilter}, focused on the "do not poison the session on a
 * failed sync" contract (REQ-SEC-013): the {@code BACKEND_ROLES_SYNCED} session flag must only be
 * set when the backend role read genuinely succeeded, so a transient backend outage on the first
 * request of a session is retried instead of leaving the principal under-privileged until re-login.
 */
class BackendRoleSyncFilterTest {

  private static final String SYNC_COMPLETE_FLAG = "BACKEND_ROLES_SYNCED";
  private static final String USERS_ME = "/api/v1/users/me";

  private BackendApiClient backendApiClient;
  private BackendRoleSyncFilter filter;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain chain;
  private HttpSession session;

  /** Wires fresh mocks and an authenticated OAuth2 token (officer) into the security context. */
  @BeforeEach
  void setUp() {
    backendApiClient = mock(BackendApiClient.class);
    filter = new BackendRoleSyncFilter(backendApiClient);

    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    chain = mock(FilterChain.class);
    session = mock(HttpSession.class);
    when(request.getSession(false)).thenReturn(session);
    when(session.getAttribute(SYNC_COMPLETE_FLAG)).thenReturn(null);

    OidcIdToken idToken = OidcIdToken.withTokenValue("token").subject("user-1").build();
    List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_OFFICER"));
    OidcUser oidcUser = new DefaultOidcUser(authorities, idToken);
    SecurityContextHolder.getContext()
        .setAuthentication(new OAuth2AuthenticationToken(oidcUser, authorities, "keycloak"));
  }

  /** Clears the per-test security context. */
  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilterInternal_whenUsersMeReturnsNull_doesNotMarkSessionSynced() throws Exception {
    // Given — Resilience4j fallback hands back null (backend unavailable)
    when(backendApiClient.get(USERS_ME, UserDto.class)).thenReturn(null);

    // When
    filter.doFilterInternal(request, response, chain);

    // Then — flag stays unset so the next request retries; chain still proceeds
    verify(session, never()).setAttribute(eq(SYNC_COMPLETE_FLAG), any());
    verify(chain).doFilter(request, response);
  }

  @Test
  void doFilterInternal_whenUsersMeThrows_doesNotMarkSessionSynced() throws Exception {
    // Given — the backend call blows up
    when(backendApiClient.get(USERS_ME, UserDto.class))
        .thenThrow(new RuntimeException("backend down"));

    // When
    filter.doFilterInternal(request, response, chain);

    // Then
    verify(session, never()).setAttribute(eq(SYNC_COMPLETE_FLAG), any());
    verify(chain).doFilter(request, response);
  }

  @Test
  void doFilterInternal_whenUsersMeSucceeds_marksSessionSynced() throws Exception {
    // Given — a valid user whose roles are already present on the token (modified=false path, so no
    // SecurityContext rewrite is exercised) — the read still counts as a successful sync.
    UserDto user =
        new UserDto(
            UUID.randomUUID(),
            "officer",
            "Officer",
            "Officer",
            null,
            null,
            null,
            Set.of("Officer"),
            Set.of(),
            null,
            false,
            false,
            true,
            null,
            1L,
            null,
            false);
    when(backendApiClient.get(USERS_ME, UserDto.class)).thenReturn(user);

    // When
    filter.doFilterInternal(request, response, chain);

    // Then — successful read marks the session synced
    verify(session).setAttribute(SYNC_COMPLETE_FLAG, true);
    verify(chain).doFilter(request, response);
  }
}

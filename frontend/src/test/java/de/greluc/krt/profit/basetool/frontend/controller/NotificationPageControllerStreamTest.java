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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.greluc.krt.profit.basetool.frontend.service.BackendApiClient;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Unit tests for {@link NotificationPageController#stream(HttpServletRequest, Authentication)}: the
 * notification SSE relay must resolve the OAuth2 bearer <b>read-only</b> and never drive a token
 * refresh on this long-lived request (REQ-SEC-012, ADR-0019). A refresh here would rotate the
 * session's online refresh token and a late session write-back could resurrect a stale token,
 * tripping Keycloak's reuse detection and revoking the whole session.
 */
class NotificationPageControllerStreamTest {

  private static final String REGISTRATION_ID = "keycloak";

  @Test
  void stream_withNoBoundToken_failsSoft_withoutCallingTheBackendStream() {
    // Given a session whose authorized client cannot be loaded (e.g. a freshly-lost session)
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MessageSource messageSource = mock(MessageSource.class);
    WebClient sseWebClient = mock(WebClient.class);
    OAuth2AuthorizedClientRepository authorizedClientRepository =
        mock(OAuth2AuthorizedClientRepository.class);
    NotificationPageController controller =
        new NotificationPageController(
            backendApiClient, messageSource, sseWebClient, authorizedClientRepository);

    HttpServletRequest request = mock(HttpServletRequest.class);
    Authentication authentication = mock(Authentication.class);
    when(authorizedClientRepository.loadAuthorizedClient(REGISTRATION_ID, authentication, request))
        .thenReturn(null);

    // When the browser opens the stream
    SseEmitter emitter = controller.stream(request, authentication);

    // Then the token was resolved read-only and the relay failed soft: no upstream subscription,
    // so the long-lived request never asks the manager to refresh/rotate the token.
    assertNotNull(emitter);
    verify(authorizedClientRepository)
        .loadAuthorizedClient(REGISTRATION_ID, authentication, request);
    verifyNoInteractions(sseWebClient);
  }

  @Test
  void stream_withTokenlessClient_failsSoft_withoutCallingTheBackendStream() {
    // Given an authorized client present in the session but carrying no access token
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MessageSource messageSource = mock(MessageSource.class);
    WebClient sseWebClient = mock(WebClient.class);
    OAuth2AuthorizedClientRepository authorizedClientRepository =
        mock(OAuth2AuthorizedClientRepository.class);
    NotificationPageController controller =
        new NotificationPageController(
            backendApiClient, messageSource, sseWebClient, authorizedClientRepository);

    HttpServletRequest request = mock(HttpServletRequest.class);
    Authentication authentication = mock(Authentication.class);
    OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
    when(client.getAccessToken()).thenReturn((OAuth2AccessToken) null);
    when(authorizedClientRepository.loadAuthorizedClient(REGISTRATION_ID, authentication, request))
        .thenReturn(client);

    // When the browser opens the stream
    SseEmitter emitter = controller.stream(request, authentication);

    // Then it still fails soft rather than relaying a tokenless call.
    assertNotNull(emitter);
    verifyNoInteractions(sseWebClient);
  }
}

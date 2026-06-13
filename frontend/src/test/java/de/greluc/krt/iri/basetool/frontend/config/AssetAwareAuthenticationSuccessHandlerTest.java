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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

class AssetAwareAuthenticationSuccessHandlerTest {

  private RequestCache requestCache;
  private AuthenticationSuccessHandler delegate;
  private AssetAwareAuthenticationSuccessHandler handler;
  private Authentication authentication;

  @BeforeEach
  void setUp() {
    requestCache = mock(RequestCache.class);
    delegate = mock(AuthenticationSuccessHandler.class);
    authentication = mock(Authentication.class);
    handler = new AssetAwareAuthenticationSuccessHandler(requestCache, delegate);
  }

  // ---------------------------------------------------------------------------
  // onAuthenticationSuccess — branching between asset-drop and delegate paths.
  // ---------------------------------------------------------------------------

  @Test
  void onAuthenticationSuccess_assetSavedRequest_dropsSavedRequestAndRedirectsToContextRoot()
      throws Exception {
    // Given a saved request pointing to a /sm/* sourcemap probe.
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    SavedRequest saved = mock(SavedRequest.class);
    when(saved.getRedirectUrl()).thenReturn("https://profit-base.online/sm/86972ebd.map");
    when(requestCache.getRequest(request, response)).thenReturn(saved);

    // When
    handler.onAuthenticationSuccess(request, response, authentication);

    // Then the saved request is removed, the response redirects to "/", and the delegate is not
    // invoked.
    verify(requestCache).removeRequest(request, response);
    assertEquals("/", response.getRedirectedUrl());
    verify(delegate, never()).onAuthenticationSuccess(any(), any(), any());
  }

  @Test
  void onAuthenticationSuccess_assetSavedRequestWithContextPath_redirectsToContextRootHome()
      throws Exception {
    // Given a context path is set on the request (deployed under /app), the redirect must respect
    // it so the user lands on the application root rather than the servlet container root.
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setContextPath("/app");
    MockHttpServletResponse response = new MockHttpServletResponse();
    SavedRequest saved = mock(SavedRequest.class);
    when(saved.getRedirectUrl()).thenReturn("/js/vendor/foo.js.map");
    when(requestCache.getRequest(request, response)).thenReturn(saved);

    // When
    handler.onAuthenticationSuccess(request, response, authentication);

    // Then
    assertEquals("/app/", response.getRedirectedUrl());
  }

  @Test
  void onAuthenticationSuccess_navigationalSavedRequest_delegatesToWrappedHandler()
      throws Exception {
    // Given a saved request that points to a legitimate business URL.
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    SavedRequest saved = mock(SavedRequest.class);
    when(saved.getRedirectUrl()).thenReturn("/missions/abc-123");
    when(requestCache.getRequest(request, response)).thenReturn(saved);

    // When
    handler.onAuthenticationSuccess(request, response, authentication);

    // Then the delegate runs and we do not redirect ourselves or remove the saved request.
    verify(delegate).onAuthenticationSuccess(request, response, authentication);
    verify(requestCache, never()).removeRequest(any(), any());
    assertEquals(null, response.getRedirectedUrl(), "Wrapper must not write its own redirect");
  }

  @Test
  void onAuthenticationSuccess_noSavedRequest_delegatesToWrappedHandler() throws Exception {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(requestCache.getRequest(request, response)).thenReturn(null);

    // When
    handler.onAuthenticationSuccess(request, response, authentication);

    // Then
    verify(delegate).onAuthenticationSuccess(request, response, authentication);
    verify(requestCache, never()).removeRequest(any(), any());
  }

  // ---------------------------------------------------------------------------
  // isAssetLikePath — the suffix / prefix rules and edge cases.
  // ---------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/sm/abcdef.map",
        "/sm/anything",
        "/SM/upper-case-prefix.map",
        "/js/vendor/example.min.js.map",
        "/css/styles.css.map",
        "/favicon.ico",
        "/logos/krt.webp",
        "/images/flags/de.svg",
        "/fonts/Lato-Bold.woff2",
        "/css/styles.css",
        "/js/app.js",
        "/robots.txt",
        "/api/openapi.json",
        "/some/deep/path/with/dots/file.png",
        "/file.jpeg",
        "/file.JPG",
        "https://profit-base.online/sm/86972ebd.map",
        "https://example.com/asset.css?v=42",
        "/asset.css?v=42&cb=1"
      })
  void isAssetLikePath_returnsTrueForAssetLikeUrls(String url) {
    assertTrue(
        AssetAwareAuthenticationSuccessHandler.isAssetLikePath(url),
        "Expected URL to be classified as asset-like: " + url);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/missions",
        "/missions/abc-123",
        "/missions/abc-123/edit",
        "/operations/42",
        "/admin/users",
        "/sm",
        "/smtp/messages",
        "https://profit-base.online/missions/abc-123",
        "/some/path",
        "/inventory/lager?query=string",
        "/profile"
      })
  void isAssetLikePath_returnsFalseForNavigationalUrls(String url) {
    assertFalse(
        AssetAwareAuthenticationSuccessHandler.isAssetLikePath(url),
        "Expected URL to NOT be classified as asset-like: " + url);
  }

  @Test
  void isAssetLikePath_returnsFalseForNull() {
    assertFalse(AssetAwareAuthenticationSuccessHandler.isAssetLikePath(null));
  }

  @Test
  void isAssetLikePath_returnsFalseForEmptyString() {
    assertFalse(AssetAwareAuthenticationSuccessHandler.isAssetLikePath(""));
  }

  @Test
  void isAssetLikePath_returnsFalseForUnparsableUri() {
    // A path with an illegal character that URI.create rejects must surface as "not asset-like"
    // rather than throwing — a malformed saved request must never short-circuit the login flow.
    assertFalse(AssetAwareAuthenticationSuccessHandler.isAssetLikePath("https://[bad-uri"));
  }
}

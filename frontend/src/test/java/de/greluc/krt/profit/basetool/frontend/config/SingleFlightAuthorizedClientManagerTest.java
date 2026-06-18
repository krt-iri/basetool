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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Unit tests for {@link SingleFlightAuthorizedClientManager}: the per-session refresh
 * de-duplication that stops the frontend's parallel page fan-out from tripping Keycloak's
 * refresh-token reuse detection (REQ-SEC-012).
 */
class SingleFlightAuthorizedClientManagerTest {

  private static final String REGISTRATION_ID = "keycloak";

  /** Clears any servlet request bound by a test so the thread-local does not leak across tests. */
  @AfterEach
  void clearRequestContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  /** Builds an authorized client whose access token expires {@code secondsFromNow} from now. */
  private static OAuth2AuthorizedClient clientExpiringIn(long secondsFromNow) {
    OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
    OAuth2AccessToken token = mock(OAuth2AccessToken.class);
    when(client.getAccessToken()).thenReturn(token);
    when(token.getExpiresAt()).thenReturn(Instant.now().plusSeconds(secondsFromNow));
    return client;
  }

  /** Builds an authorize request bound to {@code sessionId} (null = no HTTP session attribute). */
  private static OAuth2AuthorizeRequest requestForSession(String sessionId) {
    Authentication principal = new TestingAuthenticationToken("u-1", "n/a");
    OAuth2AuthorizeRequest.Builder builder =
        OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID).principal(principal);
    if (sessionId != null) {
      HttpServletRequest servletRequest = mock(HttpServletRequest.class);
      HttpSession session = mock(HttpSession.class);
      when(servletRequest.getSession(false)).thenReturn(session);
      when(session.getId()).thenReturn(sessionId);
      builder.attributes(attrs -> attrs.put(HttpServletRequest.class.getName(), servletRequest));
    }
    return builder.build();
  }

  @Test
  void secondCallWithinSession_servesCachedClient_withoutAskingDelegateAgain() {
    // Given a delegate that would refresh on every call
    OAuth2AuthorizedClientManager delegate = mock(OAuth2AuthorizedClientManager.class);
    OAuth2AuthorizedClient fresh = clientExpiringIn(300);
    when(delegate.authorize(any())).thenReturn(fresh);
    SingleFlightAuthorizedClientManager manager = new SingleFlightAuthorizedClientManager(delegate);

    // When the same session authorizes twice
    OAuth2AuthorizedClient first = manager.authorize(requestForSession("sess-1"));
    OAuth2AuthorizedClient second = manager.authorize(requestForSession("sess-1"));

    // Then only the first call hit the delegate; the second was a cache hit
    assertSame(fresh, first);
    assertSame(fresh, second);
    verify(delegate, times(1)).authorize(any());
  }

  @Test
  void distinctSessions_refreshIndependently() {
    OAuth2AuthorizedClientManager delegate = mock(OAuth2AuthorizedClientManager.class);
    // Build the mock client BEFORE the when(...) stubbing to avoid Mockito nested-stubbing.
    OAuth2AuthorizedClient fresh = clientExpiringIn(300);
    when(delegate.authorize(any())).thenReturn(fresh);
    SingleFlightAuthorizedClientManager manager = new SingleFlightAuthorizedClientManager(delegate);

    manager.authorize(requestForSession("sess-1"));
    manager.authorize(requestForSession("sess-2"));

    // Two different sessions must not share a cached token.
    verify(delegate, times(2)).authorize(any());
  }

  @Test
  void staleDelegateResult_isNotCached_soNextCallRefreshes() {
    OAuth2AuthorizedClientManager delegate = mock(OAuth2AuthorizedClientManager.class);
    // A token already inside the 30s expiry-skew window counts as stale and must not be cached.
    OAuth2AuthorizedClient stale = clientExpiringIn(5);
    when(delegate.authorize(any())).thenReturn(stale);
    SingleFlightAuthorizedClientManager manager = new SingleFlightAuthorizedClientManager(delegate);

    manager.authorize(requestForSession("sess-1"));
    manager.authorize(requestForSession("sess-1"));

    verify(delegate, times(2)).authorize(any());
  }

  @Test
  void withoutHttpSession_keysByPrincipal_andStillSingleFlights() {
    OAuth2AuthorizedClientManager delegate = mock(OAuth2AuthorizedClientManager.class);
    OAuth2AuthorizedClient fresh = clientExpiringIn(300);
    when(delegate.authorize(any())).thenReturn(fresh);
    SingleFlightAuthorizedClientManager manager = new SingleFlightAuthorizedClientManager(delegate);

    manager.authorize(requestForSession(null));
    manager.authorize(requestForSession(null));

    // Same principal, no session → still de-duplicated by the principal-name fallback key.
    verify(delegate, times(1)).authorize(any());
  }

  @Test
  void missingServletAttribute_recoversSessionIdFromRequestContext_soSameSessionSingleFlights() {
    OAuth2AuthorizedClientManager delegate = mock(OAuth2AuthorizedClientManager.class);
    OAuth2AuthorizedClient fresh = clientExpiringIn(300);
    when(delegate.authorize(any())).thenReturn(fresh);
    SingleFlightAuthorizedClientManager manager = new SingleFlightAuthorizedClientManager(delegate);

    // Bind a servlet request (session "sess-ctx") to the current thread, as a servlet/worker thread
    // would have during a real request even when the authorize request did not carry the attribute.
    HttpServletRequest boundRequest = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(boundRequest.getSession(false)).thenReturn(session);
    when(session.getId()).thenReturn("sess-ctx");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(boundRequest));

    // First call carries NO servlet attribute → it must recover "sess-ctx" from the RequestContext
    // instead of downgrading to the principal key; the second call carries the same session id
    // explicitly. Both must resolve to the identical session key and collapse to one grant.
    manager.authorize(requestForSession(null));
    manager.authorize(requestForSession("sess-ctx"));

    verify(delegate, times(1)).authorize(any());
  }

  @Test
  void concurrentBurstOnOneSession_refreshesOnce() throws InterruptedException {
    OAuth2AuthorizedClientManager delegate = mock(OAuth2AuthorizedClientManager.class);
    AtomicInteger delegateCalls = new AtomicInteger();
    when(delegate.authorize(any()))
        .thenAnswer(
            invocation -> {
              delegateCalls.incrementAndGet();
              return clientExpiringIn(300);
            });
    SingleFlightAuthorizedClientManager manager = new SingleFlightAuthorizedClientManager(delegate);

    int threads = 16;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    for (int i = 0; i < threads; i++) {
      pool.submit(
          () -> {
            try {
              start.await();
              manager.authorize(requestForSession("sess-burst"));
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              done.countDown();
            }
          });
    }
    start.countDown();
    done.await(5, TimeUnit.SECONDS);
    pool.shutdownNow();

    // The whole concurrent burst collapses to a single refresh-token grant.
    assertEquals(1, delegateCalls.get());
  }
}

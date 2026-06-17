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

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * A {@link OAuth2AuthorizedClientManager} decorator that collapses the concurrent token refreshes a
 * single user session triggers into exactly one refresh (REQ-SEC-012, ADR-0019).
 *
 * <h2>Why this exists</h2>
 *
 * <p>A logged-in page fans out several backend-bound requests at once — the page render, the
 * notification SSE relay ({@code /notifications/stream}) and the periodic unread-count poll each
 * run on their own servlet thread but share one Spring session. With a 5-minute access-token
 * lifespan, the moment the token expires two or more of those requests independently ask the
 * underlying {@link
 * org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager} to refresh,
 * and each presents the <i>same</i> stored refresh token. Under Keycloak's refresh-token rotation
 * with reuse detection ({@code Revoke Refresh Token = on}, {@code Refresh Token Max Reuse = 0}) the
 * first refresh rotates the token and the second — replaying the now-consumed token — trips reuse
 * detection, which revokes the whole token family. Every subsequent call then fails with {@code
 * client_authorization_required} until the user logs in again.
 *
 * <h2>How it fixes it</h2>
 *
 * <p>Refreshes are serialised per session (a striped {@link ReentrantLock}, keyed by session id)
 * and the result is held in a short-lived freshness cache. The first request through the gate
 * refreshes and caches the new client; the rest, while the cached access token is still valid,
 * return it <i>without</i> asking the delegate to refresh again — so only one refresh-token grant
 * ever leaves the frontend per expiry window, and reuse detection never fires for the in-session
 * race.
 *
 * <h2>Scope &amp; limitations</h2>
 *
 * <ul>
 *   <li>The lock and cache are JVM-local, so single-flight is complete for a single frontend
 *       instance. With the frontend scaled horizontally, two instances handling the same session
 *       concurrently could still race; that residual is absorbed by setting {@code Refresh Token
 *       Max Reuse} to a small value &gt; 0 on Keycloak (see {@code docs/INGEST_KEYCLOAK_SETUP.md}).
 *   <li>Cache-hit requests are not re-persisted into their own session: Spring Session writes only
 *       changed attributes (delta save), so the cache-hit request never rewrites the authorized
 *       client and cannot clobber the value the refreshing request already flushed to Redis.
 *   <li>Cached entries hold an access/refresh token in memory. They already live in Redis alongside
 *       the session; entries are replaced on the next refresh and the map is bounded (see {@link
 *       #MAX_CACHE_ENTRIES}). When no session can be derived (e.g. a background task with no bound
 *       request) the call delegates directly with no caching.
 * </ul>
 */
public class SingleFlightAuthorizedClientManager implements OAuth2AuthorizedClientManager {

  /** Number of lock stripes; bounds memory and avoids a per-session lock-map leak. */
  private static final int STRIPE_COUNT = 64;

  /** Hard cap on cached entries; the map is cleared wholesale if it is ever exceeded. */
  private static final int MAX_CACHE_ENTRIES = 50_000;

  /**
   * Safety margin subtracted from the access-token expiry: a token within this window of expiring
   * is treated as stale so the next request refreshes proactively rather than relaying a token that
   * dies mid-flight.
   */
  private static final Duration EXPIRY_SKEW = Duration.ofSeconds(30);

  private final OAuth2AuthorizedClientManager delegate;
  private final ReentrantLock[] stripes;
  private final Map<String, OAuth2AuthorizedClient> freshClients = new ConcurrentHashMap<>();

  /**
   * Wraps the real authorized-client manager.
   *
   * @param delegate the underlying manager that performs the actual authorization-code / refresh
   *     grants (typically a {@code DefaultOAuth2AuthorizedClientManager})
   */
  public SingleFlightAuthorizedClientManager(@NotNull OAuth2AuthorizedClientManager delegate) {
    this.delegate = delegate;
    this.stripes = new ReentrantLock[STRIPE_COUNT];
    for (int i = 0; i < STRIPE_COUNT; i++) {
      this.stripes[i] = new ReentrantLock();
    }
  }

  /**
   * Authorizes (or refreshes) the client for {@code authorizeRequest}, serialising concurrent
   * requests of the same session so only one refresh-token grant is issued per expiry window.
   *
   * <p>When a session-scoped cache key cannot be derived the call is delegated directly (no
   * locking, no caching). Otherwise the per-session stripe lock is held while the freshness cache
   * is consulted and — on a miss or a stale entry — the delegate performs the grant and the result
   * is cached.
   *
   * @param authorizeRequest the authorize request supplied by the WebClient OAuth2 filter
   * @return the authorized client (with a usable access token), or {@code null} if the delegate
   *     could not produce one
   */
  @Override
  @Nullable
  public OAuth2AuthorizedClient authorize(@NotNull OAuth2AuthorizeRequest authorizeRequest) {
    String key = cacheKey(authorizeRequest);
    if (key == null) {
      return delegate.authorize(authorizeRequest);
    }
    ReentrantLock lock = stripes[Math.floorMod(key.hashCode(), STRIPE_COUNT)];
    lock.lock();
    try {
      OAuth2AuthorizedClient cached = freshClients.get(key);
      if (isFresh(cached)) {
        return cached;
      }
      OAuth2AuthorizedClient authorized = delegate.authorize(authorizeRequest);
      if (isFresh(authorized)) {
        if (freshClients.size() >= MAX_CACHE_ENTRIES) {
          freshClients.clear();
        }
        freshClients.put(key, authorized);
      } else {
        freshClients.remove(key);
      }
      return authorized;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Builds the per-session cache / lock key from the authorize request: {@code
   * <registrationId>|s:<sessionId>} when an HTTP session is bound (the common case — the OAuth2
   * exchange filter carries the servlet request through as a request attribute), falling back to
   * the principal name when only that is available. Returns {@code null} when neither can be
   * derived, in which case the caller delegates without single-flight.
   *
   * @param request the authorize request
   * @return the cache key, or {@code null} if no session / principal context is available
   */
  @Nullable
  private static String cacheKey(@NotNull OAuth2AuthorizeRequest request) {
    String registrationId = request.getClientRegistrationId();
    HttpServletRequest servletRequest = request.getAttribute(HttpServletRequest.class.getName());
    if (servletRequest != null && servletRequest.getSession(false) != null) {
      return registrationId + "|s:" + servletRequest.getSession(false).getId();
    }
    if (request.getPrincipal() != null && request.getPrincipal().getName() != null) {
      return registrationId + "|p:" + request.getPrincipal().getName();
    }
    return null;
  }

  /**
   * Reports whether {@code client} carries an access token that is still valid beyond the {@link
   * #EXPIRY_SKEW} margin. A {@code null} client, a missing access token, or an absent / imminent
   * expiry all count as stale so the caller refreshes.
   *
   * @param client the candidate authorized client; may be {@code null}
   * @return {@code true} if the access token is safely usable for the near future
   */
  private static boolean isFresh(@Nullable OAuth2AuthorizedClient client) {
    if (client == null || client.getAccessToken() == null) {
      return false;
    }
    Instant expiresAt = client.getAccessToken().getExpiresAt();
    return expiresAt != null && Instant.now().plus(EXPIRY_SKEW).isBefore(expiresAt);
  }
}

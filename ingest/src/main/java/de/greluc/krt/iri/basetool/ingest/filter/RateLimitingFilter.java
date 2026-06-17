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

package de.greluc.krt.iri.basetool.ingest.filter;

import de.greluc.krt.iri.basetool.ingest.config.RateLimitProperties;
import de.greluc.krt.iri.basetool.ingest.web.ProblemResponseWriter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Per-client-IP token-bucket rate limiter for the ingest endpoints (REQ-INGEST-005). The new
 * ingress must not be usable to hammer the backend's import endpoints, so each source IP gets a
 * small bucket; an exhausted bucket yields 429 with a {@code Retry-After}. Mirrors the backend's
 * bucket4j approach. With {@code server.forward-headers-strategy=framework} the remote address is
 * the real client IP behind the proxy, not the proxy's.
 */
@Component
@Order(RateLimitingFilter.ORDER)
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

  /** After correlation id and size cap, still before Spring Security. */
  public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 30;

  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final RateLimitProperties properties;
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(
      @NotNull HttpServletRequest request,
      @NotNull HttpServletResponse response,
      @NotNull FilterChain filterChain)
      throws ServletException, IOException {
    Bucket bucket = buckets.computeIfAbsent(clientIp(request), ip -> newBucket());
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) {
      long retryAfterSeconds =
          Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
      response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
      ProblemResponseWriter.write(
          response,
          objectMapper,
          HttpStatus.TOO_MANY_REQUESTS,
          "Rate limit exceeded",
          "RATE_LIMITED",
          "Too many ingest requests. Please retry later.");
      return;
    }
    filterChain.doFilter(request, response);
  }

  /**
   * Skips paths outside {@code /v1/} and disables the filter entirely when rate limiting is off.
   *
   * @param request the current request
   * @return {@code true} to bypass the filter
   */
  @Override
  protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
    return !properties.isEnabled() || !request.getRequestURI().startsWith("/v1/");
  }

  /**
   * Builds a fresh per-IP bucket from the configured capacity / refill.
   *
   * @return a new token bucket
   */
  private @NotNull Bucket newBucket() {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(properties.getCapacity())
            .refillGreedy(properties.getRefillTokens(), properties.getRefillPeriod())
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  /**
   * Resolves the client key for bucketing — the servlet remote address, which Spring resolves from
   * the forwarded headers under {@code forward-headers-strategy=framework}.
   *
   * @param request the current request
   * @return a non-null IP string usable as a map key
   */
  private static @NotNull String clientIp(@NotNull HttpServletRequest request) {
    String remote = request.getRemoteAddr();
    return remote == null || remote.isBlank() ? "unknown" : remote;
  }
}

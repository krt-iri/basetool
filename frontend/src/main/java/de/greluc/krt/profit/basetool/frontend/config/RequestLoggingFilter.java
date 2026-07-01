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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Emits a single structured access-log line per request on INFO (or WARN for slow requests) in the
 * frontend module. Correlation id and user id are rendered via the MDC pattern in {@code
 * logback-spring.xml} and therefore not duplicated in the message body.
 *
 * <p>Static resources, the actuator and swagger assets are skipped to keep the access log focused
 * on real user traffic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter implements Ordered {

  private final LoggingProperties loggingProperties;

  @Override
  protected void doFilterInternal(
      @NotNull HttpServletRequest request,
      @NotNull HttpServletResponse response,
      @NotNull FilterChain filterChain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - start) / 1_000_000L;
      int status = response.getStatus();
      String method = request.getMethod();
      String path = request.getRequestURI();
      if (durationMs >= loggingProperties.slowRequestThresholdMs()) {
        log.warn("Slow request {} {} -> {} in {} ms", method, path, status, durationMs);
      } else if (log.isInfoEnabled()) {
        log.info("{} {} -> {} in {} ms", method, path, status, durationMs);
      }
    }
  }

  @Override
  protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri.endsWith(".css")
        || uri.endsWith(".js")
        || uri.endsWith(".ico")
        || uri.endsWith(".woff")
        || uri.endsWith(".woff2")
        || uri.contains("/images/")
        || uri.contains("/logos/")
        || uri.contains("/fonts/")
        || uri.startsWith("/actuator/")
        || uri.startsWith("/webjars/");
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE - 50;
  }
}

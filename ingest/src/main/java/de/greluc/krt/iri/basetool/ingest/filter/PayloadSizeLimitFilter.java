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

import de.greluc.krt.iri.basetool.ingest.config.IngestProperties;
import de.greluc.krt.iri.basetool.ingest.web.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Rejects ingest requests whose declared {@code Content-Length} exceeds the configured cap before
 * the body is read or relayed (REQ-INGEST-005) — the gateway mirror of the frontend proxy's
 * 2&nbsp;MB guard. A real extract is a few KB; a larger body is almost certainly hostile or buggy,
 * so it is refused with 413 instead of being streamed to the backend.
 */
@Component
@Order(PayloadSizeLimitFilter.ORDER)
@RequiredArgsConstructor
public class PayloadSizeLimitFilter extends OncePerRequestFilter {

  /** After correlation id, before rate limit and Spring Security. */
  public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

  private final IngestProperties ingestProperties;
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(
      @NotNull HttpServletRequest request,
      @NotNull HttpServletResponse response,
      @NotNull FilterChain filterChain)
      throws ServletException, IOException {
    long declared = request.getContentLengthLong();
    if (declared > ingestProperties.getMaxPayloadBytes()) {
      ProblemResponseWriter.write(
          response,
          objectMapper,
          HttpStatus.CONTENT_TOO_LARGE,
          "Payload too large",
          "PAYLOAD_TOO_LARGE",
          "The ingest payload exceeds the allowed size.");
      return;
    }
    filterChain.doFilter(request, response);
  }

  /**
   * Limits this filter to the ingest endpoints; other paths (actuator, api-docs) are unaffected.
   *
   * @param request the current request
   * @return {@code true} for any path that is not under {@code /v1/}
   */
  @Override
  protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/v1/");
  }
}

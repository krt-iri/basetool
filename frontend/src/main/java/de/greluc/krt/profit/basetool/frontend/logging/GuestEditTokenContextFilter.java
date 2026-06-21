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

package de.greluc.krt.profit.basetool.frontend.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that snapshots the inbound {@code X-Guest-Edit-Token} header into {@link
 * GuestEditTokenContext} on every request and clears it on the way out (security audit M1 /
 * REQ-SEC-018).
 *
 * <p>An anonymous guest's browser replays the per-row capability token it received at sign-up via
 * this header (see {@code krt-fetch.js}); the {@link GuestEditTokenRelayFilter} on the WebClient
 * pipeline forwards it to the backend so the guest can edit/withdraw their own sign-up. That relay
 * runs on Netty reactor threads where {@code RequestContextHolder} is not bound, so a thread-local
 * snapshot taken on the Tomcat request thread — carried across the hop by Reactor's automatic
 * context propagation — is required. The {@code finally} cleanup prevents bleed-through onto pooled
 * or virtual threads. Same precedence band as {@link ClientIpContextFilter}.
 */
@Component
public class GuestEditTokenContextFilter extends OncePerRequestFilter implements Ordered {

  /** Inbound (and relayed-outbound) header carrying the per-row guest capability token. */
  public static final String GUEST_EDIT_TOKEN_HEADER = "X-Guest-Edit-Token";

  /**
   * Filter order: same late precedence band as {@link ClientIpContextFilter}, so the token is bound
   * before any controller-emitted backend call leaves the JVM.
   *
   * @return the filter order
   */
  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE - 97;
  }

  @Override
  protected void doFilterInternal(
      @NotNull HttpServletRequest request,
      @NotNull HttpServletResponse response,
      @NotNull FilterChain chain)
      throws ServletException, IOException {
    GuestEditTokenContext.set(request.getHeader(GUEST_EDIT_TOKEN_HEADER));
    try {
      chain.doFilter(request, response);
    } finally {
      GuestEditTokenContext.clear();
    }
  }
}

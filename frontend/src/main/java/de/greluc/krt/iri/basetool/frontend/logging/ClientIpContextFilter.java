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

package de.greluc.krt.iri.basetool.frontend.logging;

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
 * Servlet filter that snapshots the resolved client IP ({@link HttpServletRequest#getRemoteAddr()})
 * into {@link ClientIpContext} on every request and clears it on the way out.
 *
 * <p>With {@code server.forward-headers-strategy=framework} the frontend's {@code
 * ForwardedHeaderFilter} has already rewritten {@code getRemoteAddr()} to the real client IP behind
 * nginx-proxy-manager by the time this filter runs. The {@link ClientIpRelayFilter} on the
 * WebClient pipeline cannot read it directly because it runs on Netty reactor threads where {@code
 * RequestContextHolder} is not bound; a thread-local snapshot taken on the Tomcat request thread,
 * combined with Reactor's automatic context propagation, survives the hop. The {@code finally}
 * cleanup prevents bleed-through onto pooled or virtual threads.
 *
 * <p>The filter runs late in the chain (after {@code ForwardedHeaderFilter} and Spring Session) so
 * the resolved IP is bound before any controller-emitted backend call leaves the JVM — the same
 * precedence band as {@link ActiveSquadronContextFilter}.
 */
@Component
public class ClientIpContextFilter extends OncePerRequestFilter implements Ordered {

  /**
   * Filter order: late enough that {@code ForwardedHeaderFilter} has resolved the real client IP.
   * Same precedence band as {@link ActiveSquadronContextFilter}.
   *
   * @return the filter order
   */
  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE - 98;
  }

  @Override
  protected void doFilterInternal(
      @NotNull HttpServletRequest request,
      @NotNull HttpServletResponse response,
      @NotNull FilterChain chain)
      throws ServletException, IOException {
    ClientIpContext.set(request.getRemoteAddr());
    try {
      chain.doFilter(request, response);
    } finally {
      ClientIpContext.clear();
    }
  }
}

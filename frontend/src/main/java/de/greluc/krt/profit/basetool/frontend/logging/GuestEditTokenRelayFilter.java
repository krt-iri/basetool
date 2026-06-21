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

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

/**
 * Relays the per-row guest edit token from the inbound servlet request to the backend via the
 * {@code X-Guest-Edit-Token} request header on every outbound {@code WebClient} call (security
 * audit M1 / REQ-SEC-018).
 *
 * <p>The token is snapshotted onto a thread-local by {@link GuestEditTokenContextFilter} at the
 * start of every servlet request and read here on the WebClient pipeline; Reactor's automatic
 * context propagation carries the thread-local across the hop to the Netty reactor thread that
 * issues the I/O. Without this relay an anonymous guest could never edit/withdraw their own sign-up
 * after the M1 gate tightening, because the backend's {@code MissionSecurityService} verifies the
 * token from this header. No header is added when no token is bound — the backend then falls
 * through to its mission-manager-only path for the guest row.
 *
 * <p>The token is a low-value, per-row capability (it authorises only edits to a single guest
 * participant), so relaying it to every backend call for the request is harmless; only the
 * participant write endpoints read it. Mirrors {@link ActiveSquadronRelayFilter} and {@link
 * ClientIpRelayFilter}.
 */
@Component
public class GuestEditTokenRelayFilter {

  /**
   * Returns the filter function that adds the {@code X-Guest-Edit-Token} header to outbound
   * requests when the inbound request carried one. No header is added otherwise.
   *
   * @return filter function for the WebClient pipeline; never {@code null}.
   */
  @NotNull
  public ExchangeFilterFunction relayGuestEditToken() {
    return (request, next) -> {
      String token = GuestEditTokenContext.get();
      if (token == null || token.isBlank()) {
        return next.exchange(request);
      }
      return next.exchange(
          ClientRequest.from(request)
              .header(GuestEditTokenContextFilter.GUEST_EDIT_TOKEN_HEADER, token)
              .build());
    };
  }
}

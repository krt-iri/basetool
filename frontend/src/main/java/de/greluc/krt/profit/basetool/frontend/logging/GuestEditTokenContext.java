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

import org.jetbrains.annotations.Nullable;

/**
 * Thread-local holder for the per-row guest edit token of the current servlet request, so the
 * {@code WebClient} exchange filter ({@link GuestEditTokenRelayFilter}) can forward it to the
 * backend as the {@code X-Guest-Edit-Token} header from a Netty reactor thread that no longer has
 * the Tomcat request bound (security audit M1 / REQ-SEC-018).
 *
 * <p><b>Why this exists:</b> the backend is a pure resource server no browser reaches directly —
 * every participant write is proxied server-side by this frontend. An anonymous guest who created a
 * sign-up holds an unguessable capability token (returned once at create time, kept client-side and
 * replayed by {@code krt-fetch.js} as the {@code X-Guest-Edit-Token} request header). Without
 * relaying that header to the backend, the guest could never edit/withdraw their own sign-up after
 * the M1 fix tightened the guest-row gate. Populated by {@link GuestEditTokenContextFilter} at the
 * start of every servlet request and cleared in the matching {@code finally} block to avoid
 * bleed-through on pooled / virtual threads. Mirrors {@link ClientIpContext}; Reactor's automatic
 * context propagation carries the value across the hop to the reactor worker thread.
 */
public final class GuestEditTokenContext {

  private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

  private GuestEditTokenContext() {
    // utility
  }

  /**
   * Stores the given guest edit token for the calling thread; a {@code null}/blank value clears the
   * slot.
   *
   * @param token the guest edit token from the inbound request, or {@code null}/blank to clear.
   */
  public static void set(@Nullable String token) {
    if (token == null || token.isBlank()) {
      HOLDER.remove();
    } else {
      HOLDER.set(token);
    }
  }

  /**
   * Returns the guest edit token stored for the current thread.
   *
   * @return the token, or {@code null} when none is bound.
   */
  @Nullable
  public static String get() {
    return HOLDER.get();
  }

  /** Removes the stored token - call from {@code finally} blocks to avoid leakage. */
  public static void clear() {
    HOLDER.remove();
  }
}

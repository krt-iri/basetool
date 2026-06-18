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

import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

/**
 * Relays the caller's active OrgUnit selection from the frontend's Spring Session to the backend
 * via the {@code X-Active-Org-Unit-Id} request header on every outbound {@code WebClient} call. The
 * legacy {@code X-Active-Squadron-Id} header is sent in parallel for one release so backend deploys
 * lagging the frontend deploy still honour the pin (SPEZIALKOMMANDO_PLAN.md §7.2 / R5.e).
 *
 * <p>The state lives on the frontend because backend REST calls do not relay session cookies (the
 * frontend's {@code BackendApiClient} only attaches the OAuth2 bearer token), so a backend-side
 * {@code HttpSession} would be lost between calls. The active OrgUnit is snapshotted onto a
 * thread-local by {@link ActiveSquadronContextFilter} at the start of every servlet request and
 * read here on the WebClient pipeline. Reactor's automatic context propagation (enabled by Spring
 * Boot 4) carries the thread-local across the hop to the Netty reactor thread that actually issues
 * the I/O.
 *
 * <p>R5.e widening: the filter no longer special-cases admin callers — it relays a pinned selection
 * for any authenticated user with &gt;1 membership. The backend independently re-validates
 * non-admin pins against the caller's actual memberships (see {@link
 * de.greluc.krt.profit.basetool.backend.service.OwnerScopeService#currentScopePredicate()}), so a
 * spoofed thread-local cannot widen visibility past what the user's memberships permit.
 *
 * <p>Failure modes degrade silently: no thread-local bound (background task / scheduled job) and no
 * active OrgUnit set both yield "no header added" — the backend then falls through to its default
 * behaviour (admin sees all OrgUnits, members see the union of their memberships).
 */
@Component
public class ActiveSquadronRelayFilter {

  /**
   * R5.e replacement for {@link #ACTIVE_SQUADRON_HEADER}. HTTP header name carrying the caller's
   * active OrgUnit selection to the backend. {@link
   * de.greluc.krt.profit.basetool.backend.service.OwnerScopeService} reads this name first.
   */
  public static final String ACTIVE_ORG_UNIT_HEADER = "X-Active-Org-Unit-Id";

  /**
   * Legacy HTTP header name preserved for one release as an alias of {@link
   * #ACTIVE_ORG_UNIT_HEADER}. Sent in parallel by the relay so a backend deploy lagging the
   * frontend deploy still honours the pin. Removed in the destructive cleanup release.
   *
   * @deprecated R5.e replacement is {@link #ACTIVE_ORG_UNIT_HEADER}.
   */
  @Deprecated public static final String ACTIVE_SQUADRON_HEADER = "X-Active-Squadron-Id";

  /**
   * Returns the filter function that adds both the {@code X-Active-Org-Unit-Id} header (new
   * canonical name) and the {@code X-Active-Squadron-Id} header (one-release alias) to outbound
   * requests when the caller has an OrgUnit selected in the frontend session. No header is added
   * for callers without an active selection — the backend then falls through to its default
   * behaviour (admin sees all OrgUnits, non-admin sees the union of memberships).
   *
   * @return filter function for the WebClient pipeline; never {@code null}.
   */
  @NotNull
  public ExchangeFilterFunction relayActiveSquadron() {
    return (request, next) -> {
      UUID active = ActiveSquadronContext.get();
      if (active == null) {
        return next.exchange(request);
      }
      return next.exchange(
          ClientRequest.from(request)
              .header(ACTIVE_ORG_UNIT_HEADER, active.toString())
              .header(ACTIVE_SQUADRON_HEADER, active.toString())
              .build());
    };
  }
}

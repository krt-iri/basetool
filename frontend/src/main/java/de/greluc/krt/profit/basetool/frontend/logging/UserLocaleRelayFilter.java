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

import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

/**
 * Relays the caller's resolved UI locale to the backend via the {@code Accept-Language} request
 * header on every outbound {@code WebClient} call. The backend resolves RFC 7807 problem details
 * (e.g. the refinery-import envelope rejects, #435) through {@code LocaleContextHolder} — without
 * this header every backend-localized message would arrive in the backend container's default
 * locale instead of the language the user actually selected, violating the binding i18n rule.
 *
 * <p>Spring MVC binds the user's locale (session-resolved, title-bar DE/EN toggle) onto {@link
 * LocaleContextHolder} for the duration of the servlet request; Reactor's automatic context
 * propagation (see {@link
 * de.greluc.krt.profit.basetool.frontend.config.ReactorContextPropagationConfig}) restores it on
 * the Reactor-Netty worker thread this filter executes on — the same mechanism the correlation-id
 * and active-OrgUnit relays rely on.
 *
 * <p>Failure mode degrades silently: without a bound locale context (background task, scheduled
 * job) no header is added and the backend falls through to its default-locale behaviour.
 */
@Component
public class UserLocaleRelayFilter {

  /**
   * Returns the filter function that adds the {@code Accept-Language} header carrying the user's
   * resolved locale (as a BCP 47 language tag) to outbound requests. No header is added when no
   * locale context is bound to the current thread.
   *
   * @return filter function for the WebClient pipeline; never {@code null}.
   */
  @NotNull
  public ExchangeFilterFunction relayUserLocale() {
    return (request, next) -> {
      Locale locale =
          LocaleContextHolder.getLocaleContext() != null
              ? LocaleContextHolder.getLocaleContext().getLocale()
              : null;
      if (locale == null) {
        return next.exchange(request);
      }
      return next.exchange(
          ClientRequest.from(request)
              .header(HttpHeaders.ACCEPT_LANGUAGE, locale.toLanguageTag())
              .build());
    };
  }
}

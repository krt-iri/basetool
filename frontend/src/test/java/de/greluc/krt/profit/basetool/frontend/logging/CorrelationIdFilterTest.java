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

import static org.assertj.core.api.Assertions.assertThat;

import de.greluc.krt.profit.basetool.frontend.config.LoggingProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class CorrelationIdFilterTest {

  private final LoggingProperties props =
      new LoggingProperties("X-Correlation-Id", "correlationId", "userId", 2000L, 1500L, false);
  private final CorrelationIdFilter filter = new CorrelationIdFilter(props);

  @AfterEach
  void tearDown() {
    MDC.clear();
    CorrelationContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void generatesCorrelationIdWhenHeaderMissing() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = new MockFilterChain();

    filter.doFilter(req, res, chain);

    String cid = res.getHeader("X-Correlation-Id");
    assertThat(cid).isNotBlank();
    // UUID length including hyphens
    assertThat(cid).matches("[0-9a-fA-F-]{36}");
    assertThat(MDC.get("correlationId")).isNull();
    assertThat(MDC.get("userId")).isNull();
    assertThat(CorrelationContext.get()).isNull();
  }

  @Test
  void reusesInboundCorrelationId() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
    req.addHeader("X-Correlation-Id", "abc-123");
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, new MockFilterChain());

    assertThat(res.getHeader("X-Correlation-Id")).isEqualTo("abc-123");
  }

  @Test
  void rejectsUnsafeInboundCorrelationId() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
    req.addHeader("X-Correlation-Id", "evil\r\nInjected: header");
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, new MockFilterChain());

    String cid = res.getHeader("X-Correlation-Id");
    assertThat(cid).doesNotContain("\r").doesNotContain("\n");
    assertThat(cid).matches("[0-9a-fA-F-]{36}");
  }

  @Test
  void exposesCorrelationIdToCorrelationContextDuringChain() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
    req.addHeader("X-Correlation-Id", "thread-local-check");
    MockHttpServletResponse res = new MockHttpServletResponse();
    final String[] seen = new String[1];
    FilterChain chain = (request, response) -> seen[0] = CorrelationContext.get();

    filter.doFilter(req, res, chain);

    assertThat(seen[0]).isEqualTo("thread-local-check");
    assertThat(CorrelationContext.get()).isNull();
  }

  @Test
  void setsAnonymousUserIdWhenUnauthenticated() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
    MockHttpServletResponse res = new MockHttpServletResponse();
    final String[] seen = new String[1];
    FilterChain chain = (request, response) -> seen[0] = MDC.get("userId");

    filter.doFilter(req, res, chain);

    assertThat(seen[0]).isEqualTo("anonymous");
  }
}

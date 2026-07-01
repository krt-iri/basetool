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

import static org.assertj.core.api.Assertions.assertThat;

import de.greluc.krt.profit.basetool.frontend.logging.ClientIpContextFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Locks the ordering invariant behind the SEC-02 fix: the explicitly registered {@link
 * ForwardedHeaderFilter} MUST run strictly after {@link ClientIpContextFilter}, so the latter still
 * sees the raw (unconsumed) {@code X-Forwarded-For} chain. If a future change flips {@code
 * server.forward-headers-strategy} back to {@code framework} (re-introducing the auto-registered
 * filter at {@code Integer.MIN_VALUE}) or reorders these beans, the spoofing-resistant client-IP
 * resolution silently breaks — this test guards against that.
 */
class ForwardedHeaderConfigTest {

  private final ForwardedHeaderConfig config = new ForwardedHeaderConfig();

  @Test
  void forwardedHeaderFilter_runsExactlyOneSlotAfterClientIpContextFilter() {
    FilterRegistrationBean<ForwardedHeaderFilter> registration = config.forwardedHeaderFilter();
    int clientIpFilterOrder = new ClientIpContextFilter(new ClientIpProperties()).getOrder();

    assertThat(clientIpFilterOrder)
        .as("ClientIpContextFilter must be at the very highest precedence")
        .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    assertThat(registration.getOrder())
        .as("ForwardedHeaderFilter must run strictly after ClientIpContextFilter")
        .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1)
        .isGreaterThan(clientIpFilterOrder);
  }

  @Test
  void forwardedHeaderFilter_registersAForwardedHeaderFilter() {
    FilterRegistrationBean<ForwardedHeaderFilter> registration = config.forwardedHeaderFilter();

    assertThat(registration.getFilter())
        .as("mirrors Spring Boot's own registration so OAuth2/HSTS behaviour is unchanged")
        .isInstanceOf(ForwardedHeaderFilter.class);
  }
}

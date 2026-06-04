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

package de.greluc.krt.iri.basetool.backend.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * Wires Spring's {@link ShallowEtagHeaderFilter} so {@code If-None-Match} conditional GETs are
 * short-circuited at the servlet layer — saves bandwidth on unchanged responses without touching
 * controllers.
 */
@Configuration
public class EtagConfig {

  /**
   * Exposes the raw {@link ShallowEtagHeaderFilter} bean so tests (e.g. {@code HttpCachingTest})
   * can autowire it directly into a {@code MockMvc} chain without going through the servlet
   * container's registration.
   *
   * @return a fresh {@link ShallowEtagHeaderFilter} instance
   */
  @Bean
  public ShallowEtagHeaderFilter shallowEtagFilter() {
    return new ShallowEtagHeaderFilter();
  }

  /**
   * Registers the filter for the entire URI space at near-highest precedence so the 304 short-
   * circuit happens before any heavier filter (auth, caching, controller dispatch) builds a body.
   *
   * @param shallowEtagFilter the filter bean to register
   * @return servlet container registration with URL patterns and order set
   */
  @Bean
  public FilterRegistrationBean<ShallowEtagHeaderFilter> shallowEtagHeaderFilter(
      ShallowEtagHeaderFilter shallowEtagFilter) {
    FilterRegistrationBean<ShallowEtagHeaderFilter> filter = new FilterRegistrationBean<>();
    filter.setFilter(shallowEtagFilter);
    // Ensure ETag is applied early so conditional requests can be short-circuited
    filter.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    filter.addUrlPatterns("/*");
    return filter;
  }
}

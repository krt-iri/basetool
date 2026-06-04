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

package de.greluc.krt.iri.basetool.frontend.config;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.CssLinkResourceTransformer;
import org.springframework.web.servlet.resource.VersionResourceResolver;

/** Spring configuration for Web Mvc. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // `setCacheControl` replaces the older `setCachePeriod(31536000)` so the emitted header is
    // `Cache-Control: max-age=31536000, public, immutable` instead of a bare `max-age` — every
    // static asset URL carries a content hash via the VersionResourceResolver below, which means
    // the resource at a given URL never changes and `immutable` is the safe and correct hint to
    // browsers ("do not even revalidate"). This also lets us retire the standalone
    // `StaticCacheHeaderFilter`: Spring's resource chain now sets the exact same header set the
    // filter used to inject.
    registry
        .addResourceHandler("/**")
        .addResourceLocations(
            "classpath:/META-INF/resources/",
            "classpath:/resources/",
            "classpath:/static/",
            "classpath:/public/")
        .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
        .resourceChain(true)
        .addResolver(new VersionResourceResolver().addContentVersionStrategy("/**"))
        .addTransformer(new CssLinkResourceTransformer());
  }
}

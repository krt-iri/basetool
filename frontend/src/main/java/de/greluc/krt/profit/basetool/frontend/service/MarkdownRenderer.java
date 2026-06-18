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

package de.greluc.krt.profit.basetool.frontend.service;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * Renders user-supplied Markdown (e.g. the mission description) to HTML that is safe to emit via
 * {@code th:utext}. Two hardening switches make the output XSS-safe without a separate sanitizer:
 * raw HTML embedded in the Markdown is escaped to text ({@code escapeHtml}), and link/image URLs
 * with dangerous protocols ({@code javascript:}, {@code data:} …) are stripped ({@code
 * sanitizeUrls}). The only markup that can reach the page is therefore what the CommonMark renderer
 * itself generates. Exposed as the {@code @markdown} bean for Thymeleaf templates; parser and
 * renderer instances are thread-safe and reused.
 */
@Component("markdown")
public class MarkdownRenderer {

  private final Parser parser = Parser.builder().build();

  private final HtmlRenderer renderer =
      HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).softbreak("<br />\n").build();

  /**
   * Converts Markdown text into sanitized HTML for {@code th:utext} consumption.
   *
   * @param markdown the raw Markdown source; {@code null} or blank yields an empty string
   * @return the rendered HTML with raw-HTML input escaped and unsafe URLs removed
   */
  @NotNull
  public String render(String markdown) {
    if (markdown == null || markdown.isBlank()) {
      return "";
    }
    Node document = parser.parse(markdown);
    return renderer.render(document);
  }
}

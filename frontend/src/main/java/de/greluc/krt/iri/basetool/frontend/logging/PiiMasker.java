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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based PII / secret masking shared by every frontend appender that emits log output: {@link
 * PiiMaskingPatternLayout} for the console / plain-text file sinks and {@link
 * PiiMaskingLogstashEncoder} for the prod JSON sink. Mirrors the backend {@code PiiMasker} so both
 * modules scrub the same patterns (audit M-5 closed the gap where the frontend JSON sink used the
 * stock, unmasked {@code LogstashEncoder}).
 *
 * <p>Patterns:
 *
 * <ul>
 *   <li>JWTs (three Base64URL segments separated by dots, header prefix {@code eyJ}) -&gt; {@code
 *       JWT_***}.
 *   <li>RFC 5322-ish e-mail addresses -&gt; {@code ***@***.***}.
 *   <li>Values introduced by the keywords {@code bearer}, {@code token}, {@code session-id} or
 *       {@code authorization} keep the keyword and replace the trailing value with {@code ***}.
 * </ul>
 *
 * <p>All replacements are alphanumeric only, never quotes or backslashes, so applying the masker on
 * top of a serialized JSON document leaves the JSON syntactically valid.
 */
public final class PiiMasker {

  private static final String JWT_PATTERN =
      "(eyJ[a-zA-Z0-9_-]{5,}\\.eyJ[a-zA-Z0-9_-]{5,}\\.[a-zA-Z0-9_-]{5,})";
  private static final String EMAIL_PATTERN =
      "([a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})";
  private static final String KEYWORD_TOKEN_PATTERN =
      "(?i)(bearer\\s+|token\\s*[:=]?\\s*|session[-_]?id\\s*[:=]?\\s*"
          + "|authorization\\s*[:=]?\\s*(?:bearer\\s+)?)([a-zA-Z0-9\\-_\\.]+)";

  private static final Pattern PII_PATTERN =
      Pattern.compile(JWT_PATTERN + "|" + EMAIL_PATTERN + "|" + KEYWORD_TOKEN_PATTERN);

  private PiiMasker() {}

  /**
   * Returns {@code input} with all detected PII / secret occurrences replaced by fixed
   * placeholders. {@code null}, empty and PII-free inputs are returned as-is.
   *
   * @param input the raw log line or serialized JSON document to scrub; may be {@code null}.
   * @return the masked text, or {@code input} unchanged when it is {@code null} / empty / PII-free.
   */
  public static String mask(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }

    Matcher matcher = PII_PATTERN.matcher(input);
    if (!matcher.find()) {
      return input;
    }

    matcher.reset();
    StringBuilder sb = new StringBuilder(input.length());
    while (matcher.find()) {
      if (matcher.group(1) != null) {
        matcher.appendReplacement(sb, "JWT_***");
      } else if (matcher.group(2) != null) {
        matcher.appendReplacement(sb, "***@***.***");
      } else if (matcher.group(3) != null && matcher.group(4) != null) {
        matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(3)) + "***");
      }
    }
    matcher.appendTail(sb);
    return sb.toString();
  }
}

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

package de.greluc.krt.profit.basetool.backend.support;

import java.text.Normalizer;

/**
 * Shared free-text normalization primitives for inbound string fields.
 *
 * <p>Both the JSON path ({@code NormalizedStringDeserializer}) and the form-binding path ({@code
 * NormalizedStringEditor}) must land a submitted value in the same canonical form so a JSON post
 * and a form post of the identical text reach the database identically. This class holds the two
 * pieces they share — the single authoritative free-text length cap and the
 * NFC-normalize-and-length-check step — while each caller keeps its own null/blank policy (which
 * deliberately differ: the JSON deserializer maps any Unicode-whitespace-only value to {@code
 * null}, the form editor only an ASCII-empty one, and only when its {@code emptyAsNull} flag is
 * set).
 */
public final class StringNormalization {

  /**
   * The single free-text length cap, matching the longest free-text column in the schema. Shared by
   * the JSON deserializer and the form editor so the limit is declared in exactly one place instead
   * of being repeated as a bare {@code 8000} literal at each site.
   */
  public static final int MAX_FREE_TEXT_LENGTH = 8000;

  /** Non-instantiable holder of static normalization helpers. */
  private StringNormalization() {}

  /**
   * NFC-normalizes {@code value} and enforces {@code maxLength}. NFC collapses Unicode combining
   * sequences into precomposed code points so {@code "café"} (one code point) and {@code "cafe +
   * ́"} (two code points) canonicalize to the same string and compare equal in the database. The
   * caller is responsible for trimming and for its own null/blank handling before calling this;
   * {@code value} must be non-null.
   *
   * @param value the already-trimmed, non-null value to canonicalize
   * @param maxLength the inclusive maximum allowed length after normalization
   * @return the NFC-normalized value
   * @throws IllegalArgumentException when the normalized value exceeds {@code maxLength}; {@code
   *     GlobalExceptionHandler} maps this to an HTTP 400
   */
  public static String normalizeAndCap(String value, int maxLength) {
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException("String exceeds maximum allowed length of " + maxLength);
    }
    return normalized;
  }
}

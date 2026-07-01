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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StringNormalization}, the shared NFC-normalize-and-length-cap step
 * extracted from {@code NormalizedStringDeserializer} and {@code NormalizedStringEditor}. The
 * accent code points are built via {@link Character#toString(int)} rather than literals or escaped
 * unicode, so the decomposed-vs-precomposed distinction the tests hinge on is unambiguous.
 */
class StringNormalizationTest {

  /** Combining acute accent (U+0301) — the trailing mark of a decomposed "e-acute". */
  private static final String COMBINING_ACUTE = Character.toString(0x0301);

  /** Decomposed "e-acute": base letter {@code e} plus {@link #COMBINING_ACUTE} (two code units). */
  private static final String DECOMPOSED_E_ACUTE = "e" + COMBINING_ACUTE;

  /** Precomposed "e-acute" (U+00E9): the single-code-unit NFC form the sequence collapses to. */
  private static final String PRECOMPOSED_E_ACUTE = Character.toString(0x00E9);

  @Test
  void normalizeAndCap_collapsesCombiningSequenceToNfc() {
    // Given a decomposed grapheme that is longer in code units than its precomposed form
    String input = "caf" + DECOMPOSED_E_ACUTE;
    assertEquals(5, input.length(), "precondition: decomposed input is five code units");

    // When
    String result =
        StringNormalization.normalizeAndCap(input, StringNormalization.MAX_FREE_TEXT_LENGTH);

    // Then it canonicalizes to the single-code-point NFC form
    assertEquals("caf" + PRECOMPOSED_E_ACUTE, result);
    assertEquals(4, result.length(), "NFC collapses the two-code-unit accent into one");
  }

  @Test
  void normalizeAndCap_passesThroughWhenWithinCap() {
    // Given a value exactly at the cap
    String input = "a".repeat(10);

    // When / Then it is returned unchanged
    assertEquals(input, StringNormalization.normalizeAndCap(input, 10));
  }

  @Test
  void normalizeAndCap_throwsWhenExceedingCap() {
    // Given a value one character over the cap
    String input = "a".repeat(11);

    // When / Then the length guard fires
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> StringNormalization.normalizeAndCap(input, 10));
    assertEquals("String exceeds maximum allowed length of 10", ex.getMessage());
  }

  @Test
  void normalizeAndCap_measuresLengthAfterNormalization() {
    // Given 10 decomposed accents (20 code units) that collapse to 10 precomposed ones
    String input = DECOMPOSED_E_ACUTE.repeat(10);
    assertEquals(20, input.length(), "precondition: decomposed input is twenty code units");

    // When capped at 10 — the pre-normalization length (20) would exceed it, the NFC length (10)
    // does not — Then the guard must use the post-normalization length and accept it
    String result = StringNormalization.normalizeAndCap(input, 10);
    assertEquals(PRECOMPOSED_E_ACUTE.repeat(10), result);
  }
}

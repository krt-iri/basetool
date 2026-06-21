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

package de.greluc.krt.profit.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GuestParticipantTokenService}: the per-row guest capability token used by
 * the M1 fix. Verifies that generated tokens are unique and URL-safe, that hashing is deterministic
 * and never echoes the plaintext, and that {@code matches} fails closed on every blank/{@code
 * null}/mismatched input so a guest row can only be edited by a holder of the exact token.
 */
class GuestParticipantTokenServiceTest {

  private final GuestParticipantTokenService service = new GuestParticipantTokenService();

  @Test
  void generateToken_isUnpaddedUrlSafeAndUnique() {
    String a = service.generateToken();
    String b = service.generateToken();

    // 256 bits Base64url-unpadded == 43 chars; URL-safe alphabet only; no padding.
    assertEquals(43, a.length(), "256-bit token should render as 43 unpadded Base64url chars");
    assertTrue(a.matches("[A-Za-z0-9_-]+"), "token must be URL-safe (no +, /, = or whitespace)");
    assertNotEquals(a, b, "two generated tokens must differ");
  }

  @Test
  void hashToken_isDeterministicHexAndHidesPlaintext() {
    String token = service.generateToken();
    String hash = service.hashToken(token);

    assertEquals(64, hash.length(), "SHA-256 hex is 64 chars");
    assertTrue(hash.matches("[0-9a-f]+"), "hash must be lowercase hex");
    assertEquals(hash, service.hashToken(token), "hashing is deterministic");
    assertFalse(hash.contains(token), "the hash must not embed the plaintext token");
  }

  @Test
  void matches_trueOnlyForTheExactToken() {
    String token = service.generateToken();
    String hash = service.hashToken(token);

    assertTrue(service.matches(token, hash), "the minting token must match its own hash");
    assertFalse(service.matches(service.generateToken(), hash), "a different token must not match");
  }

  @Test
  void matches_failsClosedOnBlankOrNullInput() {
    String token = service.generateToken();
    String hash = service.hashToken(token);

    assertFalse(service.matches(null, hash), "null token must not match");
    assertFalse(service.matches("", hash), "blank token must not match");
    assertFalse(
        service.matches(token, null), "a row with no stored hash (pre-V176) must not match");
    assertFalse(service.matches(token, ""), "blank stored hash must not match");
    assertFalse(service.matches(null, null), "null/null must not match");
  }
}

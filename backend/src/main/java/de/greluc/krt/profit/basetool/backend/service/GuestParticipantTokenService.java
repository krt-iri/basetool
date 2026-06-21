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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Mints and verifies the per-row capability token that authorises an anonymous guest to mutate or
 * withdraw their own mission sign-up without a login (security audit M1 / REQ-SEC-018).
 *
 * <p>The slim participant write endpoints are {@code permitAll}; before this token existed a guest
 * (unlinked) participant row was editable by anyone who merely knew its id, so any anonymous caller
 * who scraped a public mission's roster could vandalise or delete another person's sign-up. The fix
 * binds each guest row to its creator: at sign-up a 256-bit token is generated, its SHA-256 hash is
 * persisted on the row, and the plaintext is returned to the caller exactly once. A later guest
 * write must present the matching token (or the caller must hold a mission-management role) — see
 * {@link MissionSecurityService#canAccessParticipant(java.util.UUID, java.util.UUID,
 * org.springframework.security.core.Authentication)}.
 *
 * <p>Only the hash is stored, so the column is not a usable credential at rest beyond what a stolen
 * database already exposes; verification is a constant-time digest comparison.
 */
@Service
public class GuestParticipantTokenService {

  /** Cryptographically strong source for the token bytes. Thread-safe. */
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /** Token entropy in bytes (256 bits) — far beyond brute-force reach over the public endpoint. */
  private static final int TOKEN_BYTES = 32;

  /**
   * Generates a fresh, unguessable capability token: 256 random bits rendered as an unpadded
   * URL-safe Base64 string (~43 chars), safe to carry in an HTTP header and to store client-side.
   *
   * @return a new random token in plaintext; hand it to the caller once, never persist it.
   */
  @NotNull
  public String generateToken() {
    byte[] raw = new byte[TOKEN_BYTES];
    SECURE_RANDOM.nextBytes(raw);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }

  /**
   * Computes the lowercase-hex SHA-256 digest of a token — the form persisted in {@code
   * mission_participant.guest_edit_token_hash}, so the plaintext never touches the database.
   *
   * @param token the plaintext token to hash; never {@code null}.
   * @return the 64-char lowercase-hex SHA-256 of the token.
   */
  @NotNull
  public String hashToken(@NotNull String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is mandated on every conformant JVM; reaching here means a broken runtime.
      throw new IllegalStateException("SHA-256 message digest is unavailable", e);
    }
  }

  /**
   * Constant-time check that a presented plaintext token hashes to the stored hash. Returns {@code
   * false} for any blank/{@code null} input (a guest row with no stored hash, or a caller with no
   * token, is never matched), so the gate fails closed.
   *
   * @param presentedToken the plaintext token supplied by the caller (e.g. the {@code
   *     X-Guest-Edit-Token} header); may be {@code null}/blank.
   * @param storedHash the hash persisted on the participant row; may be {@code null}/blank.
   * @return {@code true} iff both are present and {@code hashToken(presentedToken)} equals {@code
   *     storedHash} under a timing-safe comparison.
   */
  public boolean matches(@Nullable String presentedToken, @Nullable String storedHash) {
    if (presentedToken == null
        || presentedToken.isBlank()
        || storedHash == null
        || storedHash.isBlank()) {
      return false;
    }
    String presentedHash = hashToken(presentedToken);
    return MessageDigest.isEqual(
        presentedHash.getBytes(StandardCharsets.UTF_8),
        storedHash.getBytes(StandardCharsets.UTF_8));
  }
}

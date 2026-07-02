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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fluent composer for the space-separated {@code key=value} {@code details} payload passed to
 * {@code AuditService.record(...)} / {@code BankAuditService.record(...)} (S8, #914).
 *
 * <p>Before this class the {@code details} string was hand-assembled as ad-hoc {@code "k=" + v + "
 * k2=" + v2} concatenation at ~100 call sites, which invited two recurring defects: format drift (a
 * missing separator space fusing {@code k1=v1k2=v2}, an inconsistent separator) and — because the
 * "no user free text / no PII in the details payload" rule of REQ-AUDIT-001 has no programmatic
 * enforcement — accidental copy-paste of a free-text or PII-bearing value into a detail. This
 * builder fixes the format in one place: {@link #of(String, Object)} starts the payload and each
 * {@link #with(String, Object)} appends {@code " key=value"}, so every migrated site emits exactly
 * {@code key=value key=value ...}.
 *
 * <p><b>The builder is the type-level seam.</b> {@code AuditService.record(...)} / {@code
 * BankAuditService.record(...)} take the {@code details} argument as {@link CharSequence}, and this
 * class implements it, so a migrated call site hands the composed {@code AuditDetails}
 * <em>directly</em> — {@code record(type, id, label, user, AuditDetails.of("k", v).with(...))} —
 * with no trailing {@code .toString()}. {@code record} renders it via {@link #toString()} before
 * persistence. A raw {@code String} is still a {@link CharSequence}, so the few non-{@code
 * key=value} payloads (bare tokens, free-form labels) keep passing a string unchanged; the builder
 * simply becomes the obvious path for the {@code key=value} shape.
 *
 * <p><b>Byte-equivalence contract (critical — audit is binding).</b> This composer is a drop-in for
 * the old concatenation: {@code AuditDetails.of("a", x).with("b", y).toString()} produces a string
 * <em>character-identical</em> to {@code "a=" + x + " b=" + y}. It achieves this by stringifying
 * every value through {@link String#valueOf(Object)} — the exact function the Java {@code +}
 * operator applies to a reference operand — so an enum renders as its {@code toString()}/{@code
 * name()}, a {@code UUID}/number/boolean as its {@code toString()}, and a {@code null} value as the
 * literal {@code "null"}, all identical to the pre-migration output. Values are never trimmed,
 * quoted, or otherwise altered.
 *
 * <p><b>Guard scope.</b> The only validation is on the <em>key</em>: it must be non-empty and
 * contain neither {@code '='} nor whitespace (a malformed key would corrupt the {@code key=value}
 * grammar or fuse two pairs). Keys are compile-time string literals at every call site, so a
 * violation is a deterministic programming error surfaced on the first test run — never data
 * dependent. The builder deliberately does <b>not</b> validate or reject value <em>content</em>:
 * {@code AuditService.record} is written to never throw and roll back the business transaction it
 * runs inside (it truncates rather than throws), and a value-content guard would be runtime-data
 * dependent and could do exactly that. The structural {@code key=value} uniformity this builder
 * enforces is the enabler for a future centralized policy check; the "no free-text/PII in values"
 * rule of REQ-AUDIT-001 remains a review-time discipline.
 */
public final class AuditDetails implements CharSequence {

  /** Accumulates the {@code key=value key=value ...} payload as it is composed. */
  private final StringBuilder buffer = new StringBuilder();

  /** Creates an empty composer; a payload is started through {@link #of(String, Object)}. */
  private AuditDetails() {}

  /**
   * Starts a detail payload with its first {@code key=value} pair.
   *
   * @param key the first key; must be non-empty and free of {@code '='} and whitespace
   * @param value the first value; stringified via {@link String#valueOf(Object)} (a {@code null}
   *     renders as the literal {@code "null"}), byte-identical to {@code "key=" + value}
   * @return a new builder holding {@code "key=value"}
   */
  @Contract("_, _ -> new")
  public static @NotNull AuditDetails of(@NotNull String key, @Nullable Object value) {
    AuditDetails details = new AuditDetails();
    details.append(key, value);
    return details;
  }

  /**
   * Appends {@code " key=value"} (a single leading space separator) to the payload.
   *
   * @param key the next key; must be non-empty and free of {@code '='} and whitespace
   * @param value the next value; stringified via {@link String#valueOf(Object)} (a {@code null}
   *     renders as the literal {@code "null"})
   * @return this builder, for chaining
   */
  @Contract("_, _ -> this")
  public @NotNull AuditDetails with(@NotNull String key, @Nullable Object value) {
    buffer.append(' ');
    append(key, value);
    return this;
  }

  /**
   * Appends {@code key=String.valueOf(value)} to the buffer after validating the key.
   *
   * @param key the key to validate and append
   * @param value the value, stringified via {@link String#valueOf(Object)}
   */
  private void append(@NotNull String key, @Nullable Object value) {
    validateKey(key);
    buffer.append(key).append('=').append(String.valueOf(value));
  }

  /**
   * Rejects a key that would corrupt the {@code key=value} grammar — a {@code null}/empty key, or
   * one containing {@code '='} (ambiguous split) or whitespace (would fuse with the space
   * separator).
   *
   * @param key the key to validate
   * @throws IllegalArgumentException if {@code key} is {@code null}, empty, or contains {@code '='}
   *     or whitespace
   */
  private static void validateKey(@Nullable String key) {
    if (key == null || key.isEmpty()) {
      throw new IllegalArgumentException("Audit detail key must be non-empty");
    }
    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      if (c == '=' || Character.isWhitespace(c)) {
        throw new IllegalArgumentException(
            "Audit detail key must not contain '=' or whitespace: '" + key + "'");
      }
    }
  }

  /**
   * The number of characters in the composed payload — the {@link CharSequence} contract, delegated
   * to the backing buffer.
   *
   * @return the current payload length
   */
  @Override
  public int length() {
    return buffer.length();
  }

  /**
   * The character at the given index of the composed payload — the {@link CharSequence} contract,
   * delegated to the backing buffer.
   *
   * @param index the zero-based character index
   * @return the character at {@code index}
   * @throws IndexOutOfBoundsException if {@code index} is negative or not less than {@link
   *     #length()}
   */
  @Override
  public char charAt(int index) {
    return buffer.charAt(index);
  }

  /**
   * A subsequence of the composed payload — the {@link CharSequence} contract, delegated to the
   * backing buffer.
   *
   * @param start the start index, inclusive
   * @param end the end index, exclusive
   * @return the requested subsequence
   * @throws IndexOutOfBoundsException if {@code start}/{@code end} are out of range or {@code start
   *     > end}
   */
  @Override
  public @NotNull CharSequence subSequence(int start, int end) {
    return buffer.subSequence(start, end);
  }

  /**
   * Renders the composed {@code key=value key=value ...} payload — also how {@code record(...)}
   * turns this {@link CharSequence} into the persisted {@code details} string.
   *
   * @return the detail string, byte-identical to the equivalent {@code "k=" + v + " k2=" + v2}
   *     concatenation
   */
  @Override
  public @NotNull String toString() {
    return buffer.toString();
  }
}

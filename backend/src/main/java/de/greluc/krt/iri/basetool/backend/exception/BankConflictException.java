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

package de.greluc.krt.iri.basetool.backend.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bank-domain state conflict carrying its own <em>stable problem code</em> (epic #556). Unlike the
 * generic {@link BusinessConflictException} (always {@code BUSINESS_CONFLICT}), the bank spec
 * mandates distinguishable 409 codes — {@code BANK_OVERDRAFT}, {@code BANK_ACCOUNT_NOT_EMPTY},
 * {@code BANK_ACCOUNT_CLOSED}, {@code BANK_GRANTEE_MISSING_ROLE}, … — so the frontend can render a
 * specific inline field error (e.g. the overdraft hint at the amount input, K1 mockup) instead of a
 * generic toast.
 *
 * <p>Mapped to HTTP {@code 409 Conflict} by {@link GlobalExceptionHandler}; the {@link #code} is
 * surfaced as the RFC 7807 {@code code} extension property and the optional {@link #properties} are
 * copied onto the problem response so clients can localize parameterized messages (available
 * balance, account number, holder handle) without parsing the human-readable {@code detail}.
 */
@Getter
public class BankConflictException extends RuntimeException {

  /** Overdraft attempt: the booking would take the account balance below zero (REQ-BANK-006). */
  public static final String CODE_BANK_OVERDRAFT = "BANK_OVERDRAFT";

  /**
   * Overdraft attempt at holder level: the booking would take the named holder's sub-balance on the
   * account below zero, even though the account balance might suffice (REQ-BANK-006).
   */
  public static final String CODE_BANK_HOLDER_OVERDRAFT = "BANK_HOLDER_OVERDRAFT";

  /** Close attempt on an account whose balance is not zero (REQ-BANK-002). */
  public static final String CODE_BANK_ACCOUNT_NOT_EMPTY = "BANK_ACCOUNT_NOT_EMPTY";

  /** Booking attempt on a {@code CLOSED} account (REQ-BANK-002). */
  public static final String CODE_BANK_ACCOUNT_CLOSED = "BANK_ACCOUNT_CLOSED";

  /** Grant creation for a user who does not hold the Bank Employee role (REQ-BANK-009). */
  public static final String CODE_BANK_GRANTEE_MISSING_ROLE = "BANK_GRANTEE_MISSING_ROLE";

  /** Transfer with identical source and destination (same account AND same holder). */
  public static final String CODE_BANK_SELF_TRANSFER = "BANK_SELF_TRANSFER";

  /** Reversal attempt on a transaction that has already been reversed (REQ-BANK-004). */
  public static final String CODE_BANK_ALREADY_REVERSED = "BANK_ALREADY_REVERSED";

  /** Booking attempt naming a deactivated holder (REQ-BANK-003). */
  public static final String CODE_BANK_HOLDER_INACTIVE = "BANK_HOLDER_INACTIVE";

  /**
   * Reversal attempt on a transaction that is not itself reversible — a {@code WIPE_RESET} (a
   * deliberate end-state) or a {@code REVERSAL} (a mistake is corrected by reversing the original,
   * never the correction). REQ-BANK-004.
   */
  public static final String CODE_BANK_NOT_REVERSIBLE = "BANK_NOT_REVERSIBLE";

  /** The stable machine-readable problem code, one of the {@code CODE_BANK_*} constants. */
  private final String code;

  /**
   * Structured, PII-free parameters copied onto the RFC 7807 response as extension properties (e.g.
   * {@code accountNo}, {@code available}, {@code holderHandle}) so the frontend can build a
   * localized message; never {@code null}, possibly empty.
   */
  private final transient Map<String, Object> properties;

  /**
   * Creates a bank conflict without structured parameters.
   *
   * @param code one of the {@code CODE_BANK_*} constants; becomes the RFC 7807 {@code code}
   * @param message human-readable detail (literal English or an i18n key, see {@code
   *     GlobalExceptionHandler#resolveDetail})
   */
  public BankConflictException(@NotNull String code, @NotNull String message) {
    this(code, message, null);
  }

  /**
   * Creates a bank conflict with structured parameters for client-side localization.
   *
   * @param code one of the {@code CODE_BANK_*} constants; becomes the RFC 7807 {@code code}
   * @param message human-readable detail (literal English or an i18n key)
   * @param properties PII-free extension properties for the problem response; copied defensively,
   *     {@code null} means none
   */
  public BankConflictException(
      @NotNull String code, @NotNull String message, @Nullable Map<String, Object> properties) {
    super(message);
    this.code = code;
    this.properties =
        properties == null
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
  }
}

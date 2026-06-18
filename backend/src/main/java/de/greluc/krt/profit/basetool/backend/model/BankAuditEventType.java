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

package de.greluc.krt.profit.basetool.backend.model;

/**
 * Classifies a {@link BankAuditEvent} row (REQ-BANK-012). Deliberately NOT mirrored by a database
 * CHECK constraint (V154, following the V113 {@code external_sync_report} precedent): the set grows
 * with later phases and this {@code @Enumerated(STRING)} mapping is the source of truth.
 */
public enum BankAuditEventType {

  /** A bank account was created (any {@link BankAccountType}). */
  ACCOUNT_CREATED,

  /** A bank account's display name was changed. */
  ACCOUNT_RENAMED,

  /** A bank account was closed (zero balance enforced, REQ-BANK-002). */
  ACCOUNT_CLOSED,

  /** A closed bank account was reopened for booking. */
  ACCOUNT_REOPENED,

  /** A holder row was registered in the bank-local registry (REQ-BANK-003). */
  HOLDER_REGISTERED,

  /** A holder was deactivated — new postings naming the holder are blocked. */
  HOLDER_DEACTIVATED,

  /** A previously deactivated holder was reactivated. */
  HOLDER_REACTIVATED,

  /** A per-account grant row was created (REQ-BANK-009). */
  GRANT_CREATED,

  /** A grant's capability flags changed; the details payload carries before/after. */
  GRANT_UPDATED,

  /** A grant row was revoked (deleted) — the grantee loses view access to the account. */
  GRANT_REVOKED,

  /** A {@code DEPOSIT} transaction was booked. */
  DEPOSIT_BOOKED,

  /** A {@code WITHDRAWAL} transaction was booked. */
  WITHDRAWAL_BOOKED,

  /** An account-to-account {@code TRANSFER} transaction was booked (REQ-BANK-011 variant 1). */
  TRANSFER_BOOKED,

  /** An intra-account holder rebooking was booked (REQ-BANK-011 variant 2 — custody move). */
  HOLDER_REBOOKED,

  /** A transaction was corrected by a {@code REVERSAL} transaction. */
  TRANSACTION_REVERSED,

  /** The admin wipe reset zeroed all balances (REQ-BANK-013); one summarizing event. */
  WIPE_RESET_EXECUTED,

  /** An account statement PDF was exported (REQ-BANK-014); details carry the period. */
  STATEMENT_EXPORTED,

  /** The management three-month report PDF was exported (REQ-BANK-015). */
  MANAGEMENT_REPORT_EXPORTED,

  /**
   * An org-unit officer/lead raised a confirm-before-post booking request (REQ-BANK-022). Audited
   * on creation while still off-ledger (PENDING), before any money moves.
   */
  BOOKING_REQUEST_CREATED,

  /**
   * A bank employee confirmed a booking request; the linked ledger transaction is booked in the
   * same step (REQ-BANK-023). The {@code DEPOSIT_BOOKED} / {@code WITHDRAWAL_BOOKED} event for the
   * actual ledger movement is recorded alongside it.
   */
  BOOKING_REQUEST_CONFIRMED,

  /**
   * A bank employee rejected a booking request; no ledger effect, the reason is in details
   * (REQ-BANK-023).
   */
  BOOKING_REQUEST_REJECTED,

  /**
   * The requesting officer/lead cancelled their own pending booking request; no ledger effect
   * (REQ-BANK-022).
   */
  BOOKING_REQUEST_CANCELLED
}

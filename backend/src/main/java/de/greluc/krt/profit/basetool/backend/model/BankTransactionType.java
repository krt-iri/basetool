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
 * The kind of value movement a {@link BankTransaction} records (REQ-BANK-004, ADR-0010). The type
 * determines the posting shape invariant enforced by {@code BankLedgerService}; the V153 CHECK
 * constraint mirrors this set.
 */
public enum BankTransactionType {

  /** Money entered the bank: exactly one positive posting naming the receiving holder. */
  DEPOSIT,

  /** Money left the bank: exactly one negative posting naming the paying holder. */
  WITHDRAWAL,

  /**
   * Two postings summing to zero. Covers account-to-account transfers (different accounts, each leg
   * naming the holder whose stash changes) and intra-account holder rebookings (same account, two
   * different holders — custody moves, the balance does not; REQ-BANK-011).
   */
  TRANSFER,

  /**
   * Admin-only reset after a Star Citizen wipe (REQ-BANK-013): one negative posting per non-zero
   * (account, holder) sub-balance, bringing the account to exactly zero while history survives.
   */
  WIPE_RESET,

  /**
   * Correction booking: its postings are the negated mirror of the reversed transaction's postings
   * and it references the original via {@link BankTransaction#getReversedTransaction()}. A
   * transaction can be reversed at most once (V153 unique constraint).
   */
  REVERSAL
}

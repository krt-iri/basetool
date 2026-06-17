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

package de.greluc.krt.iri.basetool.backend.model;

/**
 * Lifecycle state of a {@link BankBookingRequest} (REQ-BANK-022/-023). A request is born {@code
 * PENDING} (audited and visible, but off-ledger — it has moved no money), then reaches exactly one
 * terminal state: {@code CONFIRMED} when a bank employee books it onto the ledger, {@code REJECTED}
 * when a bank employee declines it, or {@code CANCELLED} when the requesting officer/lead withdraws
 * it. Only the {@code PENDING → terminal} transitions exist; a terminal request is immutable. V159
 * mirrors this set with a {@code CHECK} constraint.
 */
public enum BankBookingRequestStatus {

  /** Raised by an officer/lead, awaiting a bank employee's decision; no ledger effect yet. */
  PENDING,

  /** A bank employee confirmed the movement actually happened and booked it onto the ledger. */
  CONFIRMED,

  /** A bank employee declined the request; no ledger effect, a reason is recorded. */
  REJECTED,

  /** The requesting officer/lead withdrew the request before any decision; no ledger effect. */
  CANCELLED
}

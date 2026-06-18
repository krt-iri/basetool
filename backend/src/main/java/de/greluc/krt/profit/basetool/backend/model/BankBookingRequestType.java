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
 * The kind of value movement an org-unit officer/lead requests via a {@link BankBookingRequest}
 * (REQ-BANK-022). Only the two end-user movements are expressible — transfers and reversals stay a
 * pure bank-staff operation and never originate from a request. On confirmation the bank employee
 * books the matching {@link BankTransactionType} ({@code DEPOSIT} / {@code WITHDRAWAL}) through the
 * ledger. V159 mirrors this set with a {@code CHECK} constraint (stable, unlike the open-ended
 * audit/notification type sets).
 */
public enum BankBookingRequestType {

  /** Money entered the bank for the org-unit account; books a {@code DEPOSIT} on confirmation. */
  DEPOSIT,

  /** Money left the org-unit account; books a {@code WITHDRAWAL} on confirmation. */
  WITHDRAWAL
}

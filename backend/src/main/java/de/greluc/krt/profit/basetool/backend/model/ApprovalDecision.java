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
 * The admin decision recorded on a {@link UserApprovalEvent} audit row (epic #720, Track 1).
 *
 * <p>Distinct from {@link ApprovalStatus} (the current account state): a decision is the immutable
 * audit of a single admin action that moved the account into {@link ApprovalStatus#ACTIVE} or
 * {@link ApprovalStatus#REJECTED}.
 */
public enum ApprovalDecision {

  /** An admin approved the registration (account moved to {@link ApprovalStatus#ACTIVE}). */
  APPROVED,

  /** An admin rejected the registration (account moved to {@link ApprovalStatus#REJECTED}). */
  REJECTED
}

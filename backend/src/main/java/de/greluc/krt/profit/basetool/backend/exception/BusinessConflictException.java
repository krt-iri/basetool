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

package de.greluc.krt.profit.basetool.backend.exception;

/**
 * Thrown when a request collides with the current state of the system in a way that is neither a
 * pure duplicate (see {@link DuplicateEntityException}) nor a referential-integrity block (see
 * {@link EntityInUseException}).
 *
 * <p>Typical cases: ambiguous lookups where multiple candidates match a user-supplied identifier,
 * state machines that refuse a transition from the current step, or invariants that hold across
 * several aggregates and cannot be expressed by a simple uniqueness constraint.
 *
 * <p>Mapped to HTTP {@code 409 Conflict} by {@link
 * de.greluc.krt.profit.basetool.backend.exception.GlobalExceptionHandler}'s generic {@code
 * AppException} dispatch handler with the stable error code {@code BUSINESS_CONFLICT}. Every
 * accessor is inherited unchanged from {@link AppException} — it delegates to {@link
 * AppExceptionKind#BUSINESS_CONFLICT}, the fixed identity passed to the superclass constructor.
 */
public final class BusinessConflictException extends AppException {

  /**
   * Creates a {@code BusinessConflictException} with a human-readable description of the conflict.
   * The message is forwarded verbatim as the RFC&nbsp;7807 {@code detail}.
   *
   * @param message description of the conflicting state, suitable for the client response
   */
  public BusinessConflictException(String message) {
    super(AppExceptionKind.BUSINESS_CONFLICT, message);
  }

  /**
   * Creates a {@code BusinessConflictException} that wraps a lower-level cause. The {@code cause}
   * is preserved for server-side logging; only {@code message} reaches the client.
   *
   * @param message description of the conflicting state, suitable for the client response
   * @param cause underlying failure that surfaced the conflict
   */
  public BusinessConflictException(String message, Throwable cause) {
    super(AppExceptionKind.BUSINESS_CONFLICT, message, cause);
  }
}

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
 * Thrown by the service layer when a request cannot be fulfilled because of caller-supplied input
 * that is semantically invalid in a way that {@code @Valid} on the controller cannot express.
 *
 * <p>Typical cases: business-rule violations ("personal items cannot be assigned to a job order"),
 * cross-field constraints not modellable as a Jakarta annotation, references to entities that exist
 * but are in the wrong state, or requests that conflict with an aggregate's current state.
 *
 * <p>Mapped to HTTP {@code 400 Bad Request} by {@link
 * de.greluc.krt.profit.basetool.backend.exception.GlobalExceptionHandler}'s generic {@code
 * AppException} dispatch handler with the stable error code {@code BAD_REQUEST}. Prefer this over
 * {@code ResponseStatusException} so the code/title/detail flow stays consistent with the other RFC
 * 7807 responses. Every accessor is inherited unchanged from {@link AppException} — it delegates to
 * {@link AppExceptionKind#BAD_REQUEST}, the fixed identity passed to the superclass constructor.
 */
public final class BadRequestException extends AppException {

  /**
   * Creates a {@code BadRequestException} with a developer-facing detail message that is also
   * passed to the client as the RFC&nbsp;7807 {@code detail} field.
   *
   * @param message human-readable description of the rejected request
   */
  public BadRequestException(String message) {
    super(AppExceptionKind.BAD_REQUEST, message);
  }

  /**
   * Creates a {@code BadRequestException} that wraps a lower-level cause. The {@code cause} is kept
   * on the server side for logging only; the response body still uses {@code message} as the {@code
   * detail}.
   *
   * @param message human-readable description of the rejected request
   * @param cause underlying failure that triggered this exception
   */
  public BadRequestException(String message, Throwable cause) {
    super(AppExceptionKind.BAD_REQUEST, message, cause);
  }
}

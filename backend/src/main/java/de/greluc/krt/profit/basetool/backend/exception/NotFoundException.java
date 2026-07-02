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
 * Indicates that a requested domain entity could not be found.
 *
 * <p>Thrown by service-layer lookups (e.g. {@code findById}) when the identifier does not resolve
 * to an existing row. Handled centrally by {@link
 * de.greluc.krt.profit.basetool.backend.exception.GlobalExceptionHandler} and mapped to an HTTP
 * {@code 404 Not Found} RFC7807 problem response.
 *
 * <p>Rationale: previously the services threw a plain {@link RuntimeException}, which hit the
 * fallback {@code @ExceptionHandler(Exception.class)} in {@code GlobalExceptionHandler} and
 * produced HTTP 500 responses together with a full ERROR stacktrace in the logs (e.g. for
 * externally crawled / deleted mission IDs). 404 is the semantically correct status and keeps the
 * logs clean.
 *
 * <p>Unlike its six {@link AppException} siblings, {@code NotFoundException} is <b>not</b> routed
 * through {@code GlobalExceptionHandler}'s generic dispatch handler — it keeps its own dedicated
 * {@code @ExceptionHandler}, shared with three non-{@code AppException} JPA/JDK "not found" flavors
 * ({@code EntityNotFoundException}, {@code NoSuchElementException}, {@code
 * NoResourceFoundException}) that cannot be sealed under this hierarchy. Every accessor is still
 * inherited unchanged from {@link AppException} — it delegates to {@link
 * AppExceptionKind#NOT_FOUND}, the fixed identity passed to the superclass constructor — so that
 * dedicated handler reads its status/code/title/detail literals from that same constant (see {@code
 * GlobalExceptionHandler#handleNotFound}) instead of hardcoding a second copy of them.
 */
public final class NotFoundException extends AppException {

  /**
   * Creates a {@code NotFoundException} with a description of the missing entity.
   *
   * @param message human-readable description naming the entity type and identifier; surfaces as
   *     the RFC&nbsp;7807 {@code detail}
   */
  public NotFoundException(String message) {
    super(AppExceptionKind.NOT_FOUND, message);
  }

  /**
   * Creates a {@code NotFoundException} that wraps a lower-level cause (e.g. a downstream lookup
   * that itself raised an exception). The {@code cause} is kept on the server for logging only.
   *
   * @param message human-readable description naming the entity type and identifier
   * @param cause underlying failure
   */
  public NotFoundException(String message, Throwable cause) {
    super(AppExceptionKind.NOT_FOUND, message, cause);
  }
}

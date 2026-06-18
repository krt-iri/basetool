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

package de.greluc.krt.profit.basetool.frontend.logging;

import de.greluc.krt.profit.basetool.frontend.service.BackendServiceException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Centralised structured logging for {@link BackendServiceException} catches in page controllers.
 *
 * <p>Every backend error propagated to a user is logged on a single WARN line containing the action
 * / endpoint identifier, HTTP status, stable {@code problemCode}, {@code correlationId} (so the
 * same request can be located in the backend log), the localized-safe {@code detail} and the
 * structured {@code fieldErrors[]}. Rejected user values are intentionally never logged (PII
 * protection, see AGENTS.md).
 *
 * <p>Use a single helper call instead of bespoke {@code log.error(e)} patterns to keep the
 * operational log analysable across all controllers.
 */
public final class BackendErrorLogging {

  private BackendErrorLogging() {}

  /**
   * Log the given {@link BackendServiceException} at WARN level using a stable, parseable format.
   *
   * @param logger controller-local SLF4J logger
   * @param action short action identifier (e.g. {@code "createHandover"} or {@code "POST
   *     /api/v1/orders/{id}/handovers"}) - never user input
   * @param contextId optional resource id (e.g. JobOrder UUID) for triage; pass {@code null} when
   *     not applicable
   * @param ex the exception raised by the backend WebClient layer
   */
  public static void warn(
      @NotNull Logger logger,
      @NotNull String action,
      @Nullable Object contextId,
      @NotNull BackendServiceException ex) {
    if (!logger.isWarnEnabled()) {
      return;
    }
    if (contextId != null) {
      logger.warn(
          "Backend call failed [action={}, contextId={}]: status={}, code={}, correlationId={},"
              + " detail={}, fieldErrors={}",
          action,
          contextId,
          ex.getStatusCode(),
          ex.getProblemCode(),
          ex.getCorrelationId(),
          ex.getProblemDetail(),
          ex.getFieldErrors());
    } else {
      logger.warn(
          "Backend call failed [action={}]: status={}, code={}, correlationId={}, detail={},"
              + " fieldErrors={}",
          action,
          ex.getStatusCode(),
          ex.getProblemCode(),
          ex.getCorrelationId(),
          ex.getProblemDetail(),
          ex.getFieldErrors());
    }
  }

  /** Convenience overload without a {@code contextId}. */
  public static void warn(
      @NotNull Logger logger, @NotNull String action, @NotNull BackendServiceException ex) {
    warn(logger, action, null, ex);
  }
}

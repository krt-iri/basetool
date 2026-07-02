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
 * Thrown when generating a downloadable report (PDF, CSV, …) fails because of an unexpected problem
 * in the report pipeline — typically an {@code IOException} from the PDF library, a missing
 * required field on the input model, or an unsupported font/encoding.
 *
 * <p>Mapped to HTTP {@code 500 Internal Server Error} by {@link
 * de.greluc.krt.profit.basetool.backend.exception.GlobalExceptionHandler}'s generic {@code
 * AppException} dispatch handler with the stable error code {@code REPORT_GENERATION_FAILED}.
 * Compared to the generic 500 fallback this gives monitoring/alerting a dedicated handle on a
 * specific failure mode (a report problem is rarely a code bug, but it is also not a user-input
 * problem in the sense of {@code BadRequestException}).
 *
 * <p>{@link #disclosurePolicy()} is {@link ErrorDisclosurePolicy#SUPPRESSED} — inherited from
 * {@link AppExceptionKind#REPORT_GENERATION_FAILED}, the fixed identity passed to the superclass
 * constructor — so the original cause is preserved so the server log shows the full stack trace;
 * the client receives a localized generic detail instead of the raw upstream message.
 */
public final class ReportGenerationException extends AppException {

  /**
   * Creates a {@code ReportGenerationException} with a description of the report-pipeline failure.
   *
   * @param message human-readable summary of the report failure for server logging
   */
  public ReportGenerationException(String message) {
    super(AppExceptionKind.REPORT_GENERATION_FAILED, message);
  }

  /**
   * Creates a {@code ReportGenerationException} that wraps the original failure (typically an
   * {@code IOException} from the PDF/CSV library). The cause is preserved for the server log; the
   * client receives a localized generic detail.
   *
   * @param message human-readable summary of the report failure for server logging
   * @param cause underlying library or I/O failure
   */
  public ReportGenerationException(String message, Throwable cause) {
    super(AppExceptionKind.REPORT_GENERATION_FAILED, message, cause);
  }
}

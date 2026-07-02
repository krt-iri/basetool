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
 * Strategy an {@link AppException} declares for how {@code GlobalExceptionHandler}'s generic
 * dispatch handler treats its message and log level (S4, #910).
 *
 * <p>Two exceptions — {@code ExternalServiceException} and {@code ReportGenerationException} —
 * deliberately deviate from the common case: their {@code getMessage()} may carry upstream response
 * bodies, library-internal paths or font/encoding details that must never reach the client (CWE-209
 * information exposure). {@link #SUPPRESSED} is that carve-out; every other {@code AppException}
 * uses {@link #STANDARD}.
 */
public enum ErrorDisclosurePolicy {

  /**
   * The common case: {@code getMessage()} flows to the client through {@code resolveDetail}
   * (i18n-key-or-verbatim), and the dispatch handler logs the problem at WARN via {@code
   * logProblem} — an expected, user-driven 4xx/409 outcome, not a server-side failure.
   */
  STANDARD,

  /**
   * Info-leak protection: the client receives a generic, purely-localized detail ({@code
   * getMessage()} is never consulted), and the full exception — message and stack trace — is logged
   * at ERROR with the correlation id instead, so triage retains everything the WARN path would have
   * discarded.
   */
  SUPPRESSED
}

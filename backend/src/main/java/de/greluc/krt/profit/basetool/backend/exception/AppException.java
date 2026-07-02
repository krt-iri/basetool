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

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Sealed base for the backend's domain exceptions, carrying the full RFC&nbsp;7807 error-code
 * contract on the type itself instead of leaving it disconnected in {@code GlobalExceptionHandler}
 * (S4, part of #905/#910).
 *
 * <p>Before this class, {@code GlobalExceptionHandler} held 18 {@code CODE_*} constants and 7
 * near-identical dedicated {@code @ExceptionHandler} methods for {@link BadRequestException},
 * {@link NotFoundException}, {@link BusinessConflictException}, {@link DuplicateEntityException},
 * {@link EntityInUseException}, {@link ExternalServiceException} and {@link
 * ReportGenerationException} (plus {@link BankConflictException}, which already carried its own
 * {@code code}) — each hand-copying the same {@code problem(...)} + {@code resolveDetail(...)} +
 * {@code logProblem(...)} sequence. That collapses to <b>one</b> dispatch handler ( {@code
 * GlobalExceptionHandler.handleAppException}) reading the abstract accessors below, plus the {@link
 * AppExceptionKind} enum holding the seven fixed-per-type identities and {@link
 * ErrorDisclosurePolicy} covering the one behavioural fork (message suppression + ERROR logging for
 * {@code ExternalServiceException}/{@code ReportGenerationException}).
 *
 * <p><b>{@code NotFoundException} stays partially special.</b> Its {@code GlobalExceptionHandler}
 * handler is NOT collapsed into the generic dispatch — it must also catch three non-{@code
 * AppException} JPA/JDK types ({@code EntityNotFoundException}, {@code NoSuchElementException},
 * {@code NoResourceFoundException}) that cannot be sealed under this hierarchy, so it remains its
 * own dedicated {@code @ExceptionHandler}. {@code NotFoundException} is still a permitted subtype
 * here so the sealed hierarchy stays exhaustive and it exposes the same accessor contract as every
 * other {@code AppException} for consistency, even though that dedicated handler does not consult
 * it.
 *
 * <p>The permits-list below is exhaustive over the {@code exception} package — all eight subclasses
 * live here, so the seal is well-formed with zero external subclasses (verified, ADR-0047: no
 * package-cycle risk since {@code exception} already depends only on {@code support} / the JDK /
 * Spring framework types).
 */
public abstract sealed class AppException extends RuntimeException
    permits BadRequestException,
        BankConflictException,
        BusinessConflictException,
        DuplicateEntityException,
        EntityInUseException,
        ExternalServiceException,
        NotFoundException,
        ReportGenerationException {

  /**
   * Creates an {@code AppException} with a developer-facing detail message.
   *
   * @param message human-readable description; either a verbatim {@code detail} or an i18n key (see
   *     {@code GlobalExceptionHandler#resolveDetail})
   */
  protected AppException(String message) {
    super(message);
  }

  /**
   * Creates an {@code AppException} that wraps a lower-level cause, kept for server-side logging
   * only.
   *
   * @param message human-readable description; either a verbatim {@code detail} or an i18n key
   * @param cause underlying failure that triggered this exception
   */
  protected AppException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * The HTTP status the RFC&nbsp;7807 response carries.
   *
   * @return the status for this exception
   */
  public abstract HttpStatus status();

  /**
   * The stable, machine-readable {@code code} extension property. Must never change once published
   * — the frontend selects its localized message by this value.
   *
   * @return the code for this exception
   */
  public abstract String code();

  /**
   * The {@code MessageSource} key resolved for the RFC&nbsp;7807 {@code title}.
   *
   * @return the title bundle key for this exception
   */
  public abstract String titleKey();

  /**
   * The {@code MessageSource} fallback key {@code GlobalExceptionHandler} uses when resolving the
   * RFC&nbsp;7807 {@code detail} ({@code resolveDetail}'s fallback, or the direct {@code tr} key
   * when {@link #disclosurePolicy()} is {@link ErrorDisclosurePolicy#SUPPRESSED}).
   *
   * @return the detail bundle key for this exception
   */
  public abstract String detailKey();

  /**
   * The suffix appended to {@code AppProblemProperties#getBaseUri()} for the RFC&nbsp;7807 {@code
   * type}.
   *
   * @return the problem-type suffix for this exception
   */
  public abstract String typeSuffix();

  /**
   * The short phrase the dispatch handler's WARN/ERROR log line names this exception by (e.g.
   * {@code "Duplicate entity"}, {@code "Upstream service error"}).
   *
   * @return the log label for this exception
   */
  public abstract String logLabel();

  /**
   * Whether the dispatch handler must suppress {@code getMessage()} (info-leak protection) and log
   * at ERROR with the full stack trace instead of the standard WARN. Defaults to {@link
   * ErrorDisclosurePolicy#STANDARD}; only {@code ExternalServiceException} and {@code
   * ReportGenerationException} override this.
   *
   * @return the disclosure policy for this exception
   */
  public ErrorDisclosurePolicy disclosurePolicy() {
    return ErrorDisclosurePolicy.STANDARD;
  }

  /**
   * Extension properties to copy onto the RFC&nbsp;7807 response beyond {@code code} / {@code
   * correlationId} — e.g. {@code BankConflictException}'s structured, PII-free parameters (account
   * number, available balance, holder handle) so the frontend can render a localized inline field
   * error without parsing {@code detail}. Empty for every other subtype.
   *
   * @return extension properties to copy onto the problem response; never {@code null}
   */
  public Map<String, Object> extraProperties() {
    return Map.of();
  }

  /**
   * Additional structured fields appended to the dispatch handler's WARN log line (via {@code
   * logProblem}'s {@code extra} parameter) beyond the standard method/URI/status/code/correlationId
   * — e.g. {@code BankConflictException} logs its bank-specific {@code code} under the key {@code
   * "bankCode"}. {@code null} (the {@code logProblem} default: no extra fields) for every other
   * subtype.
   *
   * @return extra fields for the WARN log line, or {@code null} for none
   */
  public Map<String, ?> logExtra() {
    return null;
  }
}

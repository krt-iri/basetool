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

package de.greluc.krt.iri.basetool.ingest.web;

import de.greluc.krt.iri.basetool.ingest.ratelimit.RateLimitedException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates gateway failures into RFC 7807 {@code application/problem+json} (REQ-INGEST-001,
 * REQ-API-*). Validation and malformed bodies are 400s; a backend 4xx is relayed as-is (so the
 * backend's envelope-reject semantics reach the extractor verbatim — REQ-REFINERY-001/003); a
 * backend 5xx, a connection failure, or an open circuit becomes 502; anything else is a generic
 * 500. The handler never echoes a token or PII into the response (REQ-OBS-*).
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so the framework's standard MVC exceptions (and
 * therefore Spring Boot's auto-configured problem-details advice, which is conditional on no
 * user-provided handler) are owned here — the {@code code} extension is then attached consistently
 * to validation and body-parse problems too.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  /** Stable {@code code} extension values, so clients can branch without parsing prose. */
  private static final String CODE_VALIDATION = "VALIDATION_FAILED";

  private static final String CODE_BAD_REQUEST = "BAD_REQUEST";
  private static final String CODE_UPSTREAM = "BACKEND_RELAY_FAILED";
  private static final String CODE_INTERNAL = "INTERNAL_ERROR";
  private static final String CODE_RATE_LIMITED = "RATE_LIMITED";

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      @NotNull MethodArgumentNotValidException ex,
      @NotNull HttpHeaders headers,
      @NotNull HttpStatusCode status,
      @NotNull WebRequest request) {
    List<String> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .toList();
    ProblemDetail problem =
        problem(HttpStatus.BAD_REQUEST, "Validation failed", CODE_VALIDATION, "Validation failed.");
    problem.setProperty("fieldErrors", fieldErrors);
    return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      @NotNull HttpMessageNotReadableException ex,
      @NotNull HttpHeaders headers,
      @NotNull HttpStatusCode status,
      @NotNull WebRequest request) {
    ProblemDetail problem =
        problem(
            HttpStatus.BAD_REQUEST,
            "Malformed request body",
            CODE_BAD_REQUEST,
            "The request body could not be read as JSON.");
    return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
  }

  /**
   * Gateway-detected client problems → 400.
   *
   * @param ex the bad-request exception (its message is a safe, non-sensitive detail)
   * @return a 400 problem
   */
  @ExceptionHandler(BadRequestException.class)
  public @NotNull ProblemDetail handleBadRequest(@NotNull BadRequestException ex) {
    return problem(HttpStatus.BAD_REQUEST, "Bad request", CODE_BAD_REQUEST, ex.getMessage());
  }

  /**
   * The backend returned an error status. A 4xx is relayed verbatim (preserving the backend's
   * localized problem detail and its envelope-reject semantics); a 5xx is collapsed to 502 so the
   * gateway never surfaces backend internals.
   *
   * @param ex the WebClient response exception
   * @return a relayed 4xx problem, or a 502 for backend 5xx
   */
  @ExceptionHandler(WebClientResponseException.class)
  public @NotNull ProblemDetail handleBackendResponse(@NotNull WebClientResponseException ex) {
    HttpStatusCode status = ex.getStatusCode();
    if (status.is4xxClientError()) {
      return problem(status, "Backend rejected the import", CODE_BAD_REQUEST, backendDetail(ex));
    }
    log.warn("Backend relay returned {} — surfacing as 502", status.value());
    return problem(
        HttpStatus.BAD_GATEWAY,
        "Backend unavailable",
        CODE_UPSTREAM,
        "The import backend returned an error. Please try again.");
  }

  /**
   * The authenticated caller exhausted their per-subject ingest budget → 429 with {@code
   * Retry-After} (REQ-INGEST-005). Returned as a {@link ResponseEntity} rather than a bare {@link
   * ProblemDetail} so the {@code Retry-After} header can be attached.
   *
   * @param ex the rate-limit exception carrying the suggested retry delay
   * @return a 429 problem with a {@code Retry-After} header
   */
  @ExceptionHandler(RateLimitedException.class)
  public @NotNull ResponseEntity<ProblemDetail> handleRateLimited(
      @NotNull RateLimitedException ex) {
    ProblemDetail problem =
        problem(
            HttpStatus.TOO_MANY_REQUESTS,
            "Rate limit exceeded",
            CODE_RATE_LIMITED,
            "Too many ingest requests. Please retry later.");
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
        .body(problem);
  }

  /**
   * Transport failure reaching the backend (connection refused, timeout) or an open circuit → 502.
   *
   * @param ex the request/circuit exception
   * @return a 502 problem
   */
  @ExceptionHandler({WebClientRequestException.class, CallNotPermittedException.class})
  public @NotNull ProblemDetail handleBackendUnreachable(@NotNull Exception ex) {
    log.warn("Backend relay failed: {}", ex.getClass().getSimpleName());
    return problem(
        HttpStatus.BAD_GATEWAY,
        "Backend unavailable",
        CODE_UPSTREAM,
        "The import backend could not be reached. Please try again.");
  }

  /**
   * Catch-all → 500, with the cause logged but never leaked into the response.
   *
   * @param ex the unexpected exception
   * @return a generic 500 problem
   */
  @ExceptionHandler(Exception.class)
  public @NotNull ProblemDetail handleUnexpected(@NotNull Exception ex) {
    log.error("Unexpected ingest failure", ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Internal error",
        CODE_INTERNAL,
        "An unexpected error occurred.");
  }

  /**
   * Builds a {@link ProblemDetail} with the stable {@code code} and the current correlation id
   * (when present in the MDC) attached as extension members.
   *
   * @param status the HTTP status
   * @param title a short, stable title
   * @param code the stable machine-readable code
   * @param detail the human-readable, non-sensitive detail
   * @return the assembled problem
   */
  private static @NotNull ProblemDetail problem(
      @NotNull HttpStatusCode status, @NotNull String title, @NotNull String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? "" : detail);
    problem.setTitle(title);
    problem.setProperty("code", code);
    String correlationId = MDC.get("correlationId");
    if (correlationId != null) {
      problem.setProperty("correlationId", correlationId);
    }
    return problem;
  }

  /**
   * Extracts a safe detail string from a backend response error. The backend's problem+json body is
   * already a sanitized, localized message, so it is safe to relay; falls back to a generic phrase
   * when the body is empty.
   *
   * @param ex the backend response exception
   * @return the backend's response body, or a generic fallback
   */
  private static @NotNull String backendDetail(@NotNull WebClientResponseException ex) {
    String body = ex.getResponseBodyAsString();
    return body == null || body.isBlank() ? "The import backend rejected the request." : body;
  }
}

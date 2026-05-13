package de.greluc.krt.iri.basetool.backend.exception;

/**
 * Indicates that a requested domain entity could not be found.
 *
 * <p>Thrown by service-layer lookups (e.g. {@code findById}) when the identifier does not resolve
 * to an existing row. Handled centrally by {@link
 * de.greluc.krt.iri.basetool.backend.exception.GlobalExceptionHandler} and mapped to an HTTP {@code
 * 404 Not Found} RFC7807 problem response.
 *
 * <p>Rationale: previously the services threw a plain {@link RuntimeException}, which hit the
 * fallback {@code @ExceptionHandler(Exception.class)} in {@code GlobalExceptionHandler} and
 * produced HTTP 500 responses together with a full ERROR stacktrace in the logs (e.g. for
 * externally crawled / deleted mission IDs). 404 is the semantically correct status and keeps the
 * logs clean.
 */
public class NotFoundException extends RuntimeException {

  /**
   * Creates a {@code NotFoundException} with a description of the missing entity.
   *
   * @param message human-readable description naming the entity type and identifier; surfaces as
   *     the RFC&nbsp;7807 {@code detail}
   */
  public NotFoundException(String message) {
    super(message);
  }

  /**
   * Creates a {@code NotFoundException} that wraps a lower-level cause (e.g. a downstream lookup
   * that itself raised an exception). The {@code cause} is kept on the server for logging only.
   *
   * @param message human-readable description naming the entity type and identifier
   * @param cause underlying failure
   */
  public NotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}

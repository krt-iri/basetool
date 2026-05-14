package de.greluc.krt.iri.basetool.backend.exception;

/**
 * Thrown by the service layer when a request cannot be fulfilled because of caller-supplied input
 * that is semantically invalid in a way that {@code @Valid} on the controller cannot express.
 *
 * <p>Typical cases: business-rule violations ("personal items cannot be assigned to a job order"),
 * cross-field constraints not modellable as a Jakarta annotation, references to entities that exist
 * but are in the wrong state, or requests that conflict with an aggregate's current state.
 *
 * <p>Mapped to HTTP {@code 400 Bad Request} by {@link
 * de.greluc.krt.iri.basetool.backend.exception.GlobalExceptionHandler} with the stable error code
 * {@code BAD_REQUEST}. Prefer this over {@code ResponseStatusException} so the code/title/detail
 * flow stays consistent with the other RFC 7807 responses.
 */
public class BadRequestException extends RuntimeException {

  /**
   * Creates a {@code BadRequestException} with a developer-facing detail message that is also
   * passed to the client as the RFC&nbsp;7807 {@code detail} field.
   *
   * @param message human-readable description of the rejected request
   */
  public BadRequestException(String message) {
    super(message);
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
    super(message, cause);
  }
}

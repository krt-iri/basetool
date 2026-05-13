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

  public BadRequestException(String message) {
    super(message);
  }

  public BadRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}

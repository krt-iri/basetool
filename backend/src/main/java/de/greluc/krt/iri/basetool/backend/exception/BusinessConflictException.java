package de.greluc.krt.iri.basetool.backend.exception;

/**
 * Thrown when a request collides with the current state of the system in a way that is neither a
 * pure duplicate (see {@link DuplicateEntityException}) nor a referential-integrity block (see
 * {@link EntityInUseException}).
 *
 * <p>Typical cases: ambiguous lookups where multiple candidates match a user-supplied identifier,
 * state machines that refuse a transition from the current step, or invariants that hold across
 * several aggregates and cannot be expressed by a simple uniqueness constraint.
 *
 * <p>Mapped to HTTP {@code 409 Conflict} by {@link
 * de.greluc.krt.iri.basetool.backend.exception.GlobalExceptionHandler} with the stable error code
 * {@code BUSINESS_CONFLICT}.
 */
public class BusinessConflictException extends RuntimeException {

  /**
   * Creates a {@code BusinessConflictException} with a human-readable description of the conflict.
   * The message is forwarded verbatim as the RFC&nbsp;7807 {@code detail}.
   *
   * @param message description of the conflicting state, suitable for the client response
   */
  public BusinessConflictException(String message) {
    super(message);
  }

  /**
   * Creates a {@code BusinessConflictException} that wraps a lower-level cause. The {@code cause}
   * is preserved for server-side logging; only {@code message} reaches the client.
   *
   * @param message description of the conflicting state, suitable for the client response
   * @param cause underlying failure that surfaced the conflict
   */
  public BusinessConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}

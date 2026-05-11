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
 * <p>Mapped to HTTP {@code 409 Conflict} by
 * {@link de.greluc.krt.iri.basetool.backend.exception.GlobalExceptionHandler} with the stable
 * error code {@code BUSINESS_CONFLICT}.
 */
public class BusinessConflictException extends RuntimeException {

    public BusinessConflictException(String message) {
        super(message);
    }

    public BusinessConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}

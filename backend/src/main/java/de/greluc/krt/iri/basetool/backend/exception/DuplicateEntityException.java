package de.greluc.krt.iri.basetool.backend.exception;

/**
 * Thrown when an insert or update would violate a uniqueness constraint that the service layer
 * checks explicitly (e.g. duplicate Keycloak {@code sub}, duplicate name within a scope).
 *
 * <p>Mapped to HTTP {@code 409 Conflict} by {@link
 * de.greluc.krt.iri.basetool.backend.exception.GlobalExceptionHandler} with the stable error code
 * {@code DUPLICATE_ENTITY}. Use this rather than letting a database {@code
 * DataIntegrityViolationException} bubble up so the client receives a localized message instead of
 * a raw SQL error string.
 */
public class DuplicateEntityException extends RuntimeException {

  /**
   * Creates a {@code DuplicateEntityException} with a description of the duplicate.
   *
   * @param message human-readable description naming the entity and the duplicated identifier
   */
  public DuplicateEntityException(String message) {
    super(message);
  }
}

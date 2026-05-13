package de.greluc.krt.iri.basetool.backend.exception;

/** Thrown when an entity cannot be deleted because it is still referenced by other entities. */
public class EntityInUseException extends RuntimeException {

  /**
   * Creates an {@code EntityInUseException} with a description of the blocking reference.
   *
   * @param message human-readable explanation of which referencing entities still exist
   */
  public EntityInUseException(String message) {
    super(message);
  }
}

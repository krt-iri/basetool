package de.greluc.krt.iri.basetool.backend.exception;

/** Thrown when an entity cannot be deleted because it is still referenced by other entities. */
public class EntityInUseException extends RuntimeException {

  public EntityInUseException(String message) {
    super(message);
  }
}

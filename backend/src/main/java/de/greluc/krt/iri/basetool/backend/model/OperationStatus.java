package de.greluc.krt.iri.basetool.backend.model;

public enum OperationStatus {
  PLANNED,
  ACTIVE,
  COMPLETED,
  CANCELED;

  /**
   * Project-wide state machine for an operation's lifecycle. ADMIN callers bypass this gate at the
   * controller boundary; every other caller must respect it.
   */
  public boolean canTransitionTo(OperationStatus next) {
    if (this == next) {
      return true;
    }
    return switch (this) {
      case PLANNED -> next == ACTIVE || next == CANCELED;
      case ACTIVE -> next == COMPLETED || next == CANCELED;
      case COMPLETED, CANCELED -> false;
    };
  }
}

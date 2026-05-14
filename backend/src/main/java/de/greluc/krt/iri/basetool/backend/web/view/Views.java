package de.greluc.krt.iri.basetool.backend.web.view;

/**
 * Jackson {@code @JsonView} marker classes used to drop sensitive fields from public responses.
 *
 * <p>Fields tagged with {@link Internal} are only rendered for authenticated callers; fields tagged
 * with {@link Public} are rendered for everyone. The {@code Internal extends Public} relation lets
 * a single annotation on an internal field implicitly include all public fields when the internal
 * view is selected.
 */
public class Views {
  /** Marker for fields that may appear in unauthenticated (guest) responses. */
  public static class Public {}

  /**
   * Marker for fields that must only appear in authenticated responses. Extends {@link Public} so
   * selecting the internal view automatically includes the public surface.
   */
  public static class Internal extends Public {}
}

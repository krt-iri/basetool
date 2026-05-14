package de.greluc.krt.iri.basetool.backend.config;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Global {@code @ControllerAdvice} that registers the {@link NormalizedStringEditor} for every
 * {@code String} property bound on incoming requests.
 *
 * <p>The editor trims leading/trailing whitespace, caps every string at 8000 characters and
 * collapses Windows {@code \r\n} into {@code \n}. Applied globally so individual controllers and
 * DTOs do not need to repeat the normalization — and so that a forgotten {@code @NotBlank} combined
 * with an all-whitespace input still ends up rejected.
 */
@ControllerAdvice
public class GlobalBindingAdvice {
  /**
   * Registers {@link NormalizedStringEditor} for every {@code String} binding target. The editor's
   * {@code maxLength=8000} matches the longest free-text field stored in the database and {@code
   * trim=true} is what callers of every controller expect.
   *
   * @param binder Spring's data binder for the current request
   */
  @InitBinder
  public void initBinder(WebDataBinder binder) {
    binder.registerCustomEditor(String.class, new NormalizedStringEditor(8000, true));
  }
}

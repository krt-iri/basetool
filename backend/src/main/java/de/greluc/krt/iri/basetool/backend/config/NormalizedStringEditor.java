package de.greluc.krt.iri.basetool.backend.config;

import java.beans.PropertyEditorSupport;
import java.text.Normalizer;
import lombok.RequiredArgsConstructor;

/**
 * Spring {@link PropertyEditorSupport} that performs the same trim + NFC-normalize + length-cap as
 * {@link NormalizedStringDeserializer}, but for form-bound (non-JSON) string fields.
 *
 * <p>Registered globally via {@link GlobalBindingAdvice} so a controller never has to repeat the
 * normalization, and so a form post and a JSON post of the same value always reach the database in
 * the same canonical form. The {@code emptyAsNull} flag controls whether a blank input becomes
 * {@code null} (the default for write-DTOs) or stays the empty string (rare; only useful when a
 * client genuinely needs to distinguish "not set" from "explicitly cleared").
 */
@RequiredArgsConstructor
public class NormalizedStringEditor extends PropertyEditorSupport {

  private final int maxLength;
  private final boolean emptyAsNull;

  @Override
  public void setAsText(String text) throws IllegalArgumentException {
    if (text == null) {
      setValue(null);
      return;
    }
    String trimmed = text.trim();
    if (emptyAsNull && trimmed.isEmpty()) {
      setValue(null);
      return;
    }
    String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFC);
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException("String exceeds maximum allowed length of " + maxLength);
    }
    setValue(normalized);
  }
}

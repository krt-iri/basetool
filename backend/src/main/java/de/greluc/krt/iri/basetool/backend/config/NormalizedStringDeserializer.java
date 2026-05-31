package de.greluc.krt.iri.basetool.backend.config;

import java.text.Normalizer;
import org.springframework.util.StringUtils;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Jackson deserializer that trims, NFC-normalizes and length-caps every JSON string field.
 *
 * <p>Trimming is unconditional; an all-whitespace string deserializes as {@code null} rather than
 * an empty string so {@code @NotBlank} fires consistently. NFC normalization collapses Unicode
 * combining sequences into precomposed code points so {@code "café"} (one code point) and {@code
 * "cafe + ́"} (two code points) compare equal in the database. The 8000-character cap matches the
 * longest free-text column in the schema; an oversized payload throws {@link
 * IllegalArgumentException} which {@code GlobalExceptionHandler} maps to a 400.
 */
public class NormalizedStringDeserializer extends ValueDeserializer<String> {

  private static final int MAX_LENGTH = 8000;

  @Override
  public String deserialize(JsonParser p, DeserializationContext ctxt) {
    String text = p.getValueAsString();
    if (text == null) {
      return null;
    }
    String trimmed = text.trim();
    if (!StringUtils.hasText(trimmed)) {
      return null;
    }
    String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFC);
    if (normalized.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("String exceeds maximum allowed length of " + MAX_LENGTH);
    }
    return normalized;
  }
}

package de.greluc.krt.iri.basetool.backend.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.text.Normalizer;

public class NormalizedStringDeserializer extends JsonDeserializer<String> {

    private static final int MAX_LENGTH = 8000;

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
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

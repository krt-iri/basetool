package de.greluc.krt.iri.basetool.backend.config;

import lombok.RequiredArgsConstructor;

import java.beans.PropertyEditorSupport;
import java.text.Normalizer;

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

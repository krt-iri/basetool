package de.greluc.krt.iri.basetool.frontend.logging;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Frontend counterpart of the backend {@code LogMasker}. Masks sensitive / PII values at
 * call-sites before they are handed to a logger.
 *
 * <p>This complements {@link PiiMaskingPatternLayout}, which masks well-known patterns on the
 * logback pattern layer as a last line of defense. {@code LogMasker} is the preferred approach
 * whenever a developer explicitly logs a known-sensitive value (e-mail, identifier, token,
 * phone number, Keycloak {@code sub}).
 *
 * <p>All methods are null-safe and never throw – they return a placeholder on blank /
 * malformed input.
 */
public final class LogMasker {

    /** Placeholder used when the input is {@code null} or blank. */
    public static final String NULL_PLACEHOLDER = "<null>";

    /** Replacement used when a value is completely hidden. */
    public static final String FULL_MASK = "***";

    private LogMasker() {
        // utility class
    }

    @Contract(pure = true)
    public static @NotNull String maskEmail(@Nullable String email) {
        if (isBlank(email)) {
            return NULL_PLACEHOLDER;
        }
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return FULL_MASK;
        }
        char first = email.charAt(0);
        return first + "***" + email.substring(at);
    }

    @Contract(pure = true)
    public static @NotNull String maskId(@Nullable Object id) {
        if (id == null) {
            return NULL_PLACEHOLDER;
        }
        String value = id.toString();
        if (isBlank(value)) {
            return NULL_PLACEHOLDER;
        }
        if (value.length() < 5) {
            return FULL_MASK;
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }

    @Contract(pure = true)
    public static @NotNull String maskToken(@Nullable String token) {
        if (isBlank(token)) {
            return NULL_PLACEHOLDER;
        }
        if (token.length() <= 8) {
            return FULL_MASK;
        }
        return token.substring(0, 4) + "***";
    }

    @Contract(pure = true)
    public static @NotNull String maskPhone(@Nullable String phone) {
        if (isBlank(phone)) {
            return NULL_PLACEHOLDER;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 3) {
            return FULL_MASK;
        }
        return "***" + digits.substring(digits.length() - 2);
    }

    @Contract(pure = true)
    public static @NotNull String mask(@Nullable Object value) {
        if (value == null) {
            return NULL_PLACEHOLDER;
        }
        String s = value.toString();
        if (isBlank(s)) {
            return NULL_PLACEHOLDER;
        }
        return "***(len=" + s.length() + ")";
    }

    @Contract(value = "null -> true", pure = true)
    private static boolean isBlank(@Nullable String s) {
        return s == null || s.isBlank();
    }
}

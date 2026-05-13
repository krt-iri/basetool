package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Regressionstest fuer den Bugfix der doppelten Sommerzeit-Umrechnung in Raffinerieauftraegen. Der
 * Parser MUSS ISO-Instants (UTC mit 'Z'), ISO-Offsets und reine Datumseingaben deterministisch als
 * UTC-Instant liefern und DARF keine {@code ZoneId.systemDefault()}-Semantik verwenden.
 */
class RefineryOrderStartedAtParsingTest {

  @Test
  void shouldParseIsoInstantWithZAsUtc() {
    // Given: 16:30 Europe/Berlin Sommerzeit == 14:30:00Z
    String input = "2026-04-19T14:30:00Z";

    // When
    Instant parsed = RefineryOrderPageController.parseStartedAt(input);

    // Then
    assertEquals(Instant.parse("2026-04-19T14:30:00Z"), parsed);
  }

  @Test
  void shouldParseIsoInstantWithMillis() {
    String input = "2026-04-19T14:30:00.000Z";

    Instant parsed = RefineryOrderPageController.parseStartedAt(input);

    assertEquals(Instant.parse("2026-04-19T14:30:00Z"), parsed);
  }

  @Test
  void shouldParseIsoOffsetDateTimeSummerTime() {
    // Given: Sommerzeit Europe/Berlin (+02:00), User-Eingabe 16:30 lokal
    String input = "2026-04-19T16:30:00+02:00";

    Instant parsed = RefineryOrderPageController.parseStartedAt(input);

    assertEquals(Instant.parse("2026-04-19T14:30:00Z"), parsed);
    assertEquals(OffsetDateTime.parse(input).toInstant(), parsed);
  }

  @Test
  void shouldParseIsoOffsetDateTimeWinterTime() {
    // Given: Winterzeit Europe/Berlin (+01:00), User-Eingabe 16:30 lokal
    String input = "2026-11-15T16:30:00+01:00";

    Instant parsed = RefineryOrderPageController.parseStartedAt(input);

    assertEquals(Instant.parse("2026-11-15T15:30:00Z"), parsed);
  }

  @Test
  void shouldParseDateOnlyAsUtcStartOfDay() {
    String input = "2026-04-19";

    Instant parsed = RefineryOrderPageController.parseStartedAt(input);

    assertEquals(Instant.parse("2026-04-19T00:00:00Z"), parsed);
  }

  @Test
  void shouldTreatLocalDateTimeWithoutZoneAsUtcDefensively() {
    // Defensiver Fallback: Ein LocalDateTime ohne Zone darf KEINE
    // System-Default-Zone anwenden (frueherer Bug: ZoneId.systemDefault()),
    // da dies zu doppelter DST-Umrechnung fuehrt.
    String input = "2026-04-19T14:30";

    Instant parsed = RefineryOrderPageController.parseStartedAt(input);

    assertEquals(Instant.parse("2026-04-19T14:30:00Z"), parsed);
  }

  @Test
  void shouldReturnNowForNullOrBlank() {
    Instant parsedNull = RefineryOrderPageController.parseStartedAt(null);
    Instant parsedBlank = RefineryOrderPageController.parseStartedAt("   ");

    assertNotNull(parsedNull);
    assertNotNull(parsedBlank);
  }

  @Test
  void shouldBeIdempotentAcrossRoundTrip() {
    // Given: Ein Instant, als ISO-String serialisiert
    Instant original =
        OffsetDateTime.of(2026, 4, 19, 16, 30, 0, 0, ZoneOffset.ofHours(2)).toInstant();

    // When: durch den Parser geschickt
    Instant roundTripped = RefineryOrderPageController.parseStartedAt(original.toString());

    // Then: exakt derselbe Instant
    assertEquals(original, roundTripped);
  }
}

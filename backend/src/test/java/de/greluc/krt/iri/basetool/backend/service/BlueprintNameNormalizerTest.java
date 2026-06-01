package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link BlueprintNameNormalizer}. */
class BlueprintNameNormalizerTest {

  private final BlueprintNameNormalizer normalizer = new BlueprintNameNormalizer();

  @Test
  void nullAndBlankYieldEmptyString() {
    assertEquals("", normalizer.normalize(null));
    assertEquals("", normalizer.normalize("   "));
  }

  @Test
  void trimsCollapsesWhitespaceAndLowercases() {
    assertEquals("arclight pistol", normalizer.normalize("  Arclight   Pistol  "));
  }

  @Test
  void foldsUnicodeDoubleQuotesToAscii() {
    // SC Wiki uses curly quotes, the SCMDB export uses straight quotes — they must match.
    assertEquals(
        normalizer.normalize("Arclight \"Nightstalker\" Pistol"),
        normalizer.normalize("Arclight “Nightstalker” Pistol"));
  }

  @Test
  void foldsUnicodeApostropheToAscii() {
    assertEquals(normalizer.normalize("Gallenson's"), normalizer.normalize("Gallenson’s"));
  }

  @Test
  void keepsDistinctNamesDistinct() {
    assertEquals("adp core", normalizer.normalize("ADP Core"));
    assertEquals("adp-mk4 core woodland", normalizer.normalize("ADP-mk4 Core Woodland"));
  }
}

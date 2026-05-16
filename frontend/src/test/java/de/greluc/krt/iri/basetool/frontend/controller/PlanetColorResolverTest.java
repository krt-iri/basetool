package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link PlanetColorResolver}. */
class PlanetColorResolverTest {

  @Test
  void nullPlanetYieldsUnknownClass() {
    assertEquals(
        PlanetColorResolver.UNKNOWN_CLASS, PlanetColorResolver.cssClassFor("Stanton", null));
  }

  @Test
  void blankPlanetYieldsUnknownClass() {
    assertEquals(
        PlanetColorResolver.UNKNOWN_CLASS, PlanetColorResolver.cssClassFor("Stanton", "   "));
  }

  @Test
  void canonicalPlanetsResolveToCanonicalClass() {
    assertEquals("planet-hurston", PlanetColorResolver.cssClassFor("Stanton", "Hurston"));
    assertEquals("planet-crusader", PlanetColorResolver.cssClassFor("Stanton", "Crusader"));
    assertEquals("planet-arccorp", PlanetColorResolver.cssClassFor("Stanton", "ArcCorp"));
    assertEquals("planet-microtech", PlanetColorResolver.cssClassFor("Stanton", "microTech"));
    assertEquals("planet-pyro-1", PlanetColorResolver.cssClassFor("Pyro", "Pyro I"));
    assertEquals("planet-pyro-2", PlanetColorResolver.cssClassFor("Pyro", "Pyro II"));
    assertEquals("planet-pyro-2", PlanetColorResolver.cssClassFor("Pyro", "Monox"));
    assertEquals("planet-terra", PlanetColorResolver.cssClassFor("Terra", "Terra"));
  }

  @Test
  void canonicalLookupIsCaseInsensitive() {
    assertEquals("planet-hurston", PlanetColorResolver.cssClassFor("stanton", "HURSTON"));
    assertEquals("planet-hurston", PlanetColorResolver.cssClassFor("stanton", "  hurston  "));
  }

  @Test
  void unknownPlanetFallsBackToHashedClass() {
    String result = PlanetColorResolver.cssClassFor("Nyx", "MadePlanet");
    assertTrue(result.startsWith("planet-hash-"), "expected hash fallback, got: " + result);
    int index =
        assertDoesNotThrow(
            () -> Integer.parseInt(result.substring("planet-hash-".length())),
            "hash class suffix must be a valid integer, got: " + result);
    assertTrue(
        index >= 0 && index < PlanetColorResolver.HASH_PALETTE_SIZE,
        "hash bucket out of range: " + index);
  }

  @Test
  void hashFallbackIsDeterministic() {
    String a = PlanetColorResolver.cssClassFor("Nyx", "MadePlanet");
    String b = PlanetColorResolver.cssClassFor("Nyx", "MadePlanet");
    assertEquals(a, b, "same input must yield same class across calls");
  }

  @Test
  void hashFallbackDiscriminatesAcrossSystems() {
    // Same planet name in two different systems should land in (typically) different buckets.
    // We don't assert strict inequality for every hash combo, but at least verify the
    // (system, planet) tuple - not just planet - participates in the hash.
    String inA = PlanetColorResolver.cssClassFor("SystemA", "Unknown");
    String inB = PlanetColorResolver.cssClassFor("SystemB", "Unknown");
    // For the chosen names the buckets do differ; if a future palette resize collides, swap the
    // sentinel system names rather than weakening the test.
    assertNotEquals(inA, inB);
  }

  @Test
  void nullSystemNameDoesNotBlowUp() {
    String result = PlanetColorResolver.cssClassFor(null, "UnknownPlanet");
    assertTrue(result.startsWith("planet-hash-"));
  }
}

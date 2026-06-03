package de.greluc.krt.iri.basetool.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Pins the SCU storage-precision invariant of {@link InventoryItem}: every persisted amount is
 * rounded to three decimals (HALF_UP). The {@code amount} column is a {@code double}, so summing
 * fractional refinery yields can produce values like {@code 37.160000000000004}; the entity's
 * {@code @PrePersist}/{@code @PreUpdate} hook is the single chokepoint that normalises them.
 */
class InventoryItemTest {

  @Test
  void roundToScuScaleStripsFloatingPointNoiseBeyondThreeDecimals() {
    // The exact dirty values observed in production (refinery-sum artefacts in the book-out
    // dialog).
    assertEquals(37.16, InventoryItem.roundToScuScale(37.160000000000004));
    assertEquals(2.93, InventoryItem.roundToScuScale(2.9299999999999997));
    assertEquals(1.59, InventoryItem.roundToScuScale(1.5899999999999999));
  }

  @Test
  void roundToScuScaleRoundsHalfUpAtTheThirdDecimal() {
    assertEquals(1.235, InventoryItem.roundToScuScale(1.2345));
    assertEquals(1.234, InventoryItem.roundToScuScale(1.2344));
  }

  @Test
  void roundToScuScaleLeavesCleanValuesAndNullUntouched() {
    assertEquals(5.0, InventoryItem.roundToScuScale(5.0));
    assertEquals(12.5, InventoryItem.roundToScuScale(12.5));
    assertNull(InventoryItem.roundToScuScale(null));
  }

  @Test
  void lifecycleCallbackRoundsTheAmountField() {
    // Given an entity carrying a floating-point artefact
    InventoryItem item = new InventoryItem();
    item.setAmount(37.160000000000004);

    // When the JPA @PrePersist/@PreUpdate hook fires
    item.roundAmountToScuScale();

    // Then the stored amount is clamped to three decimals
    assertEquals(37.16, item.getAmount());
  }
}

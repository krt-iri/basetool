package de.greluc.krt.iri.basetool.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GameItemKind#mergeMoreSpecific}, the single shared "more-specific-wins,
 * never downgrade to GENERIC" arbiter used by both the UEX and the Wiki item syncs (§6.3.1).
 */
class GameItemKindTest {

  @Test
  void mergeMoreSpecific_nullExisting_yieldsToIncoming() {
    assertEquals(GameItemKind.ARMOR, GameItemKind.mergeMoreSpecific(null, GameItemKind.ARMOR));
  }

  @Test
  void mergeMoreSpecific_genericExisting_yieldsToIncoming() {
    assertEquals(
        GameItemKind.VEHICLE_ITEM,
        GameItemKind.mergeMoreSpecific(GameItemKind.GENERIC, GameItemKind.VEHICLE_ITEM));
  }

  @Test
  void mergeMoreSpecific_incomingGeneric_keepsExistingSpecific() {
    // The HIGH-2 invariant: a specific kind set by an earlier pass is never downgraded to GENERIC.
    assertEquals(
        GameItemKind.VEHICLE_ITEM,
        GameItemKind.mergeMoreSpecific(GameItemKind.VEHICLE_ITEM, GameItemKind.GENERIC));
    assertEquals(
        GameItemKind.ARMOR,
        GameItemKind.mergeMoreSpecific(GameItemKind.ARMOR, GameItemKind.GENERIC));
  }

  @Test
  void mergeMoreSpecific_equalKinds_keepsExisting() {
    assertEquals(
        GameItemKind.WEAPON,
        GameItemKind.mergeMoreSpecific(GameItemKind.WEAPON, GameItemKind.WEAPON));
  }

  @Test
  void mergeMoreSpecific_weaponRefinedByAttachment() {
    assertEquals(
        GameItemKind.WEAPON_ATTACHMENT,
        GameItemKind.mergeMoreSpecific(GameItemKind.WEAPON, GameItemKind.WEAPON_ATTACHMENT));
  }

  @Test
  void mergeMoreSpecific_attachmentNotDowngradedByWeapon() {
    // Reverse direction: an already-specific WEAPON_ATTACHMENT is not flipped back to WEAPON.
    assertEquals(
        GameItemKind.WEAPON_ATTACHMENT,
        GameItemKind.mergeMoreSpecific(GameItemKind.WEAPON_ATTACHMENT, GameItemKind.WEAPON));
  }

  @Test
  void mergeMoreSpecific_vehicleItemRefinedByVehicleWeapon() {
    assertEquals(
        GameItemKind.VEHICLE_WEAPON,
        GameItemKind.mergeMoreSpecific(GameItemKind.VEHICLE_ITEM, GameItemKind.VEHICLE_WEAPON));
  }

  @Test
  void mergeMoreSpecific_unrelatedSpecificKinds_keepExisting_noCrossFlip() {
    assertEquals(
        GameItemKind.ARMOR,
        GameItemKind.mergeMoreSpecific(GameItemKind.ARMOR, GameItemKind.WEAPON));
    assertEquals(
        GameItemKind.CLOTHING,
        GameItemKind.mergeMoreSpecific(GameItemKind.CLOTHING, GameItemKind.FOOD));
  }
}

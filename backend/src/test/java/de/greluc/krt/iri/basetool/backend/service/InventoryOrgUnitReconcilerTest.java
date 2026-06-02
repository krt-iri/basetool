package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.OrgUnit;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link InventoryOrgUnitReconciler} — the merge-safe re-stamp of a user's shared
 * inventory when they cross the membershipless boundary. Pins the auto-promote (NULL → org) and
 * auto-demote (org → NULL) policies and, crucially, that re-stamping collapses stacks that become
 * identical (the eighth merge-key dimension) instead of leaving duplicates.
 */
@ExtendWith(MockitoExtension.class)
class InventoryOrgUnitReconcilerTest {

  @Mock private InventoryItemRepository inventoryItemRepository;

  @InjectMocks private InventoryOrgUnitReconciler reconciler;

  private Material matA;
  private Material matB;
  private Location loc;

  @BeforeEach
  void setUp() {
    matA = new Material();
    matA.setId(UUID.randomUUID());
    matB = new Material();
    matB.setId(UUID.randomUUID());
    loc = new Location();
    loc.setId(UUID.randomUUID());
  }

  @Test
  void onUserGainedFirstOrgUnit_stampsEveryOwnerlessSharedRowWithTheNewOrg() {
    UUID userId = UUID.randomUUID();
    OrgUnit org = squadron();
    InventoryItem r1 = sharedRow(null, 5.0, matA, 900);
    InventoryItem r2 = sharedRow(null, 3.0, matB, 800);
    when(inventoryItemRepository.findByUserIdAndPersonalFalse(userId)).thenReturn(List.of(r1, r2));

    reconciler.onUserGainedFirstOrgUnit(userId, org);

    assertSame(org, r1.getOwningOrgUnit(), "ownerless row adopts the new org unit");
    assertSame(org, r2.getOwningOrgUnit());
    // A membershipless owner only ever holds NULL-org stock, so promotion never collides.
    verify(inventoryItemRepository, never()).delete(any());
  }

  @Test
  void onUserLostLastOrgUnit_demotesToNullAndMergesCollidingStacks() {
    UUID userId = UUID.randomUUID();
    OrgUnit orgA = squadron();
    OrgUnit orgB = squadron();
    // Same natural key (matA / loc / 900) in two different org units → collide once both go NULL.
    InventoryItem a = sharedRow(orgA, 4.0, matA, 900);
    InventoryItem b = sharedRow(orgB, 6.0, matA, 900);
    // A distinct stack (different material) that just gets nulled, never merged.
    InventoryItem c = sharedRow(orgA, 2.0, matB, 900);
    when(inventoryItemRepository.findByUserIdAndPersonalFalse(userId)).thenReturn(List.of(a, b, c));

    reconciler.onUserLostLastOrgUnit(userId);

    assertNull(a.getOwningOrgUnit());
    assertNull(b.getOwningOrgUnit());
    assertNull(c.getOwningOrgUnit());
    assertEquals(10.0, a.getAmount(), "colliding stacks merge into the first row (4 + 6)");
    verify(inventoryItemRepository).delete(b);
    verify(inventoryItemRepository, never()).delete(a);
    verify(inventoryItemRepository, never()).delete(c);
  }

  @Test
  void onUserLostLastOrgUnit_distinctStacks_demoteWithoutMerging() {
    UUID userId = UUID.randomUUID();
    OrgUnit orgA = squadron();
    InventoryItem a = sharedRow(orgA, 4.0, matA, 900);
    InventoryItem b = sharedRow(orgA, 6.0, matB, 900); // different material → distinct key
    when(inventoryItemRepository.findByUserIdAndPersonalFalse(userId)).thenReturn(List.of(a, b));

    reconciler.onUserLostLastOrgUnit(userId);

    assertNull(a.getOwningOrgUnit());
    assertNull(b.getOwningOrgUnit());
    assertEquals(4.0, a.getAmount());
    assertEquals(6.0, b.getAmount());
    verify(inventoryItemRepository, never()).delete(any());
  }

  @Test
  void reconcile_noSharedRows_isNoOp() {
    UUID userId = UUID.randomUUID();
    when(inventoryItemRepository.findByUserIdAndPersonalFalse(userId)).thenReturn(List.of());

    reconciler.onUserLostLastOrgUnit(userId);

    verify(inventoryItemRepository, never()).delete(any());
  }

  // --- helpers --------------------------------------------------------------

  private static OrgUnit squadron() {
    Squadron s = new Squadron();
    s.setId(UUID.randomUUID());
    return s;
  }

  private InventoryItem sharedRow(OrgUnit org, double amount, Material material, int quality) {
    InventoryItem i = new InventoryItem();
    i.setId(UUID.randomUUID());
    i.setOwningOrgUnit(org);
    i.setAmount(amount);
    i.setMaterial(material);
    i.setLocation(loc);
    i.setQuality(quality);
    i.setPersonal(false);
    return i;
  }
}

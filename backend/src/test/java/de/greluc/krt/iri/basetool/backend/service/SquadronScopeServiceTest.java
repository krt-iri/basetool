package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.Squadron;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/**
 * Smoke test for the {@link SquadronScopeService} compatibility shim introduced in R2.c. The
 * real implementation lives on {@link OwnerScopeService} (see {@link OwnerScopeServiceTest} for
 * the behavioural matrix); this test only verifies that every method on the shim forwards verbatim
 * to the delegate.
 *
 * <p>The shim exists so that the ~30 {@code @PreAuthorize("@squadronScopeService.*")} SpEL strings
 * scattered across the controller layer keep resolving while R2.d migrates them onto
 * {@code @ownerScopeService.*} at its own pace. Once the migration is complete the shim and this
 * smoke test are deleted in the same PR.
 */
@ExtendWith(MockitoExtension.class)
class SquadronScopeServiceTest {

  @Mock private OwnerScopeService delegate;

  @InjectMocks private SquadronScopeService shim;

  @Test
  void exposesActiveSquadronHeaderConstant() {
    // The constant survives the rename so existing callers that import
    // SquadronScopeService.ACTIVE_SQUADRON_HEADER keep compiling.
    assertEquals(OwnerScopeService.ACTIVE_SQUADRON_HEADER, SquadronScopeService.ACTIVE_SQUADRON_HEADER);
  }

  @Test
  void currentSquadronId_delegates() {
    UUID id = UUID.randomUUID();
    when(delegate.currentSquadronId()).thenReturn(Optional.of(id));

    assertEquals(Optional.of(id), shim.currentSquadronId());
    verify(delegate).currentSquadronId();
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void currentSquadron_delegates() {
    Squadron squadron = new Squadron();
    when(delegate.currentSquadron()).thenReturn(Optional.of(squadron));

    assertEquals(Optional.of(squadron), shim.currentSquadron());
    verify(delegate).currentSquadron();
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void canSeeSquadron_delegates() {
    UUID id = UUID.randomUUID();
    when(delegate.canSeeSquadron(id)).thenReturn(true);

    assertTrue(shim.canSeeSquadron(id));
    verify(delegate).canSeeSquadron(id);
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void canEditSquadron_delegates() {
    UUID id = UUID.randomUUID();
    when(delegate.canEditSquadron(id)).thenReturn(false);

    assertFalse(shim.canEditSquadron(id));
    verify(delegate).canEditSquadron(id);
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void canSeeMission_delegates() {
    UUID id = UUID.randomUUID();
    when(delegate.canSeeMission(id)).thenReturn(true);

    assertTrue(shim.canSeeMission(id));
    verify(delegate).canSeeMission(id);
  }

  @Test
  void canEditMission_delegates() {
    UUID id = UUID.randomUUID();
    when(delegate.canEditMission(id)).thenReturn(true);

    assertTrue(shim.canEditMission(id));
    verify(delegate).canEditMission(id);
  }

  @Test
  void canSeeInventoryItem_delegates() {
    UUID id = UUID.randomUUID();
    when(delegate.canSeeInventoryItem(id)).thenReturn(true);

    assertTrue(shim.canSeeInventoryItem(id));
    verify(delegate).canSeeInventoryItem(id);
  }

  @Test
  void canEditInventoryItem_delegates() {
    UUID id = UUID.randomUUID();
    when(delegate.canEditInventoryItem(id)).thenReturn(false);

    assertFalse(shim.canEditInventoryItem(id));
    verify(delegate).canEditInventoryItem(id);
  }

  @Test
  void canSeeRefineryOrder_delegates() {
    UUID id = UUID.randomUUID();
    when(delegate.canSeeRefineryOrder(id)).thenReturn(true);

    assertTrue(shim.canSeeRefineryOrder(id));
    verify(delegate).canSeeRefineryOrder(id);
  }

  @Test
  void canEditRefineryOrder_delegates() {
    UUID id = UUID.randomUUID();
    when(delegate.canEditRefineryOrder(id)).thenReturn(true);

    assertTrue(shim.canEditRefineryOrder(id));
    verify(delegate).canEditRefineryOrder(id);
  }

  @Test
  void canSeeOperation_delegates() {
    UUID id = UUID.randomUUID();
    when(delegate.canSeeOperation(id)).thenReturn(true);

    assertTrue(shim.canSeeOperation(id));
    verify(delegate).canSeeOperation(id);
  }

  @Test
  void canEditOperation_delegates() {
    UUID id = UUID.randomUUID();
    when(delegate.canEditOperation(id)).thenReturn(false);

    assertFalse(shim.canEditOperation(id));
    verify(delegate).canEditOperation(id);
  }

  @Test
  void canSeeShip_delegates() {
    UUID id = UUID.randomUUID();
    when(delegate.canSeeShip(id)).thenReturn(true);

    assertTrue(shim.canSeeShip(id));
    verify(delegate).canSeeShip(id);
  }

  @Test
  void canEditShip_delegates() {
    UUID id = UUID.randomUUID();
    when(delegate.canEditShip(id)).thenReturn(true);

    assertTrue(shim.canEditShip(id));
    verify(delegate).canEditShip(id);
  }

  @Test
  void isPromotionFeatureEnabledForCurrentScope_delegates() {
    when(delegate.isPromotionFeatureEnabledForCurrentScope()).thenReturn(true);

    assertTrue(shim.isPromotionFeatureEnabledForCurrentScope());
    verify(delegate).isPromotionFeatureEnabledForCurrentScope();
  }

  @Test
  void assertPromotionFeatureEnabled_delegates() {
    shim.assertPromotionFeatureEnabled();
    verify(delegate).assertPromotionFeatureEnabled();
  }

  @Test
  void assertPromotionFeatureEnabled_propagatesDelegateException() {
    org.mockito.Mockito.doThrow(new AccessDeniedException("disabled"))
        .when(delegate)
        .assertPromotionFeatureEnabled();

    org.junit.jupiter.api.Assertions.assertThrows(
        AccessDeniedException.class, shim::assertPromotionFeatureEnabled);
  }
}

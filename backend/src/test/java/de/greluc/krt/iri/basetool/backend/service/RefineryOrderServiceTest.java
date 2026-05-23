package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.City;
import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.QuantityType;
import de.greluc.krt.iri.basetool.backend.model.RefineryGood;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.RefineryYield;
import de.greluc.krt.iri.basetool.backend.model.SpaceStation;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderStoreDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderStoreItemDto;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryYieldRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/**
 * Unit tests for {@link RefineryOrderService#storeRefineryOrder} — the bulk-update + multi-item
 * flow CLAUDE.md flags as a 409 trap. Previous coverage was 68% line / 39% branch with only a
 * single happy-path-blocked test (the "order already COMPLETED" case). This rewrite exercises every
 * branch:
 *
 * <ul>
 *   <li>access-control (owner vs non-owner vs logistician bypass)
 *   <li>per-item lookups (material / location / user / job-order {@link NotFoundException}s)
 *   <li>assignee resolution (explicit user vs order-owner fallback)
 *   <li>aggregation into an existing InventoryItem vs creation of a new one (the {@code
 *       findMatchingInventoryItemForUpdate} branch)
 *   <li>note merge semantics (null preserved, blank replaced, novel appended with newline,
 *       duplicate dropped, &gt;1000 truncated)
 *   <li>note normalisation (null / blank / trimmed)
 *   <li>{@code updateGoodOutputQuantity}: SCU vs PIECE conversion, {@code @Min(1)} clamp at zero,
 *       no-match silent skip, missing output-material guard
 *   <li>final state transition to {@link RefineryOrderStatus#COMPLETED}
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RefineryOrderServiceTest {

  @Mock private RefineryOrderRepository refineryOrderRepository;
  @Mock private UserRepository userRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private de.greluc.krt.iri.basetool.backend.repository.MissionRepository missionRepository;

  @Mock
  private de.greluc.krt.iri.basetool.backend.repository.RefiningMethodRepository
      refiningMethodRepository;

  @Mock private MaterialRepository materialRepository;
  @Mock private InventoryItemRepository inventoryItemRepository;
  @Mock private JobOrderRepository jobOrderRepository;
  @Mock private RefineryYieldRepository refineryYieldRepository;
  @Mock private OwnerScopeService ownerScopeService;

  @InjectMocks private RefineryOrderService refineryOrderService;

  private static final UUID ORDER_ID = UUID.randomUUID();
  private static final UUID OWNER_ID = UUID.randomUUID();
  private static final UUID OTHER_USER_ID = UUID.randomUUID();
  private static final UUID MATERIAL_ID = UUID.randomUUID();
  private static final UUID LOCATION_ID = UUID.randomUUID();
  private static final UUID JOB_ORDER_ID = UUID.randomUUID();

  private RefineryOrder order;
  private User owner;
  private Material material;
  private Location location;

  @BeforeEach
  void setUpEntities() {
    owner = new User();
    owner.setId(OWNER_ID);
    owner.setUsername("alice");

    material = new Material();
    material.setId(MATERIAL_ID);
    material.setName("Quantanium");

    location = new Location();
    location.setId(LOCATION_ID);
    location.setName("ARC-L1");

    order = new RefineryOrder();
    order.setId(ORDER_ID);
    order.setOwner(owner);
    order.setStatus(RefineryOrderStatus.IN_PROGRESS);
  }

  // ------------------------------------------------------------------
  // The kept-from-the-original test (regression coverage).
  // ------------------------------------------------------------------

  @Test
  void shouldThrowExceptionWhenStoringCompletedOrder() {
    RefineryOrder completedOrder = new RefineryOrder();
    completedOrder.setId(ORDER_ID);
    completedOrder.setStatus(RefineryOrderStatus.COMPLETED);

    when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(completedOrder));

    RefineryOrderStoreDto dto = new RefineryOrderStoreDto(Collections.emptyList());

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> refineryOrderService.storeRefineryOrder(OWNER_ID, ORDER_ID, dto, false));

    assertEquals("Refinery order is already completed and stored.", ex.getMessage());
  }

  // ------------------------------------------------------------------
  // Access control
  // ------------------------------------------------------------------

  @Nested
  class AccessControlTests {

    @Test
    void throwsNotFound_whenOrderDoesNotExist() {
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

      assertThrows(
          NotFoundException.class,
          () ->
              refineryOrderService.storeRefineryOrder(
                  OWNER_ID, ORDER_ID, new RefineryOrderStoreDto(List.of()), false));
      verify(refineryOrderRepository, never()).save(any());
    }

    @Test
    void throwsAccessDenied_whenNonLogisticianIsNotOwner() {
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

      assertThrows(
          AccessDeniedException.class,
          () ->
              refineryOrderService.storeRefineryOrder(
                  OTHER_USER_ID, ORDER_ID, new RefineryOrderStoreDto(List.of()), false));
      verify(refineryOrderRepository, never()).save(any());
    }

    @Test
    void throwsAccessDenied_whenOrderOwnerIsNull_andCallerIsNotLogistician() {
      order.setOwner(null);
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

      assertThrows(
          AccessDeniedException.class,
          () ->
              refineryOrderService.storeRefineryOrder(
                  OWNER_ID, ORDER_ID, new RefineryOrderStoreDto(List.of()), false));
    }

    @Test
    void throwsAccessDenied_whenOwnerHasNullId_andCallerIsNotLogistician() {
      owner.setId(null);
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

      assertThrows(
          AccessDeniedException.class,
          () ->
              refineryOrderService.storeRefineryOrder(
                  OWNER_ID, ORDER_ID, new RefineryOrderStoreDto(List.of()), false));
    }

    @Test
    void logisticianBypassesOwnershipCheck_evenForSomeoneElsesOrder() {
      // Empty items list means we exit the for-loop without needing repository stubs
      // for material/location lookups. We just need the order to be reachable
      // and the COMPLETED check to pass.
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

      // No throw: logistician overrides owner mismatch.
      refineryOrderService.storeRefineryOrder(
          OTHER_USER_ID, ORDER_ID, new RefineryOrderStoreDto(List.of()), true);

      assertEquals(RefineryOrderStatus.COMPLETED, order.getStatus());
      verify(refineryOrderRepository, times(1)).save(order);
    }
  }

  // ------------------------------------------------------------------
  // Per-item entity lookup failures
  // ------------------------------------------------------------------

  @Nested
  class ItemLookupFailureTests {

    @Test
    void throwsNotFound_whenMaterialIsMissing() {
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
      when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.empty());

      assertThrows(
          NotFoundException.class,
          () ->
              refineryOrderService.storeRefineryOrder(
                  OWNER_ID, ORDER_ID, new RefineryOrderStoreDto(List.of(item(null, null))), false));
    }

    @Test
    void throwsNotFound_whenLocationIsMissing() {
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
      when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.of(material));
      when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.empty());

      assertThrows(
          NotFoundException.class,
          () ->
              refineryOrderService.storeRefineryOrder(
                  OWNER_ID, ORDER_ID, new RefineryOrderStoreDto(List.of(item(null, null))), false));
    }

    @Test
    void throwsNotFound_whenAssigneeUserIsMissing() {
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
      when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.of(material));
      when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(location));
      when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.empty());

      assertThrows(
          NotFoundException.class,
          () ->
              refineryOrderService.storeRefineryOrder(
                  OWNER_ID,
                  ORDER_ID,
                  new RefineryOrderStoreDto(List.of(item(OTHER_USER_ID, null))),
                  false));
    }

    @Test
    void throwsNotFound_whenJobOrderIsMissing() {
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
      when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.of(material));
      when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(location));
      when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.empty());

      assertThrows(
          NotFoundException.class,
          () ->
              refineryOrderService.storeRefineryOrder(
                  OWNER_ID,
                  ORDER_ID,
                  new RefineryOrderStoreDto(List.of(item(null, JOB_ORDER_ID))),
                  false));
    }
  }

  // ------------------------------------------------------------------
  // Assignee fallback
  // ------------------------------------------------------------------

  @Nested
  class AssigneeResolutionTests {

    @Test
    void usesOrderOwnerAsAssignee_whenItemUserIdIsNull() {
      stubLookupsForSingleItem();
      when(inventoryItemRepository.findMatchingInventoryItemForUpdate(
              any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(Collections.emptyList());

      refineryOrderService.storeRefineryOrder(
          OWNER_ID, ORDER_ID, new RefineryOrderStoreDto(List.of(item(null, null))), false);

      ArgumentCaptor<InventoryItem> captor = ArgumentCaptor.forClass(InventoryItem.class);
      verify(inventoryItemRepository, times(1)).save(captor.capture());
      assertSame(owner, captor.getValue().getUser());
    }

    @Test
    void usesExplicitlyProvidedUserAsAssignee_whenItemUserIdIsSet() {
      User other = new User();
      other.setId(OTHER_USER_ID);
      other.setUsername("bob");

      stubLookupsForSingleItem();
      when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(other));
      when(inventoryItemRepository.findMatchingInventoryItemForUpdate(
              any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(Collections.emptyList());

      refineryOrderService.storeRefineryOrder(
          OWNER_ID, ORDER_ID, new RefineryOrderStoreDto(List.of(item(OTHER_USER_ID, null))), false);

      ArgumentCaptor<InventoryItem> captor = ArgumentCaptor.forClass(InventoryItem.class);
      verify(inventoryItemRepository, times(1)).save(captor.capture());
      assertSame(other, captor.getValue().getUser());
    }
  }

  // ------------------------------------------------------------------
  // Aggregation vs creation
  // ------------------------------------------------------------------

  @Nested
  class AggregationTests {

    @Test
    void createsNewInventoryItem_whenNoMatchExists() {
      stubLookupsForSingleItem();
      when(inventoryItemRepository.findMatchingInventoryItemForUpdate(
              any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(Collections.emptyList());

      refineryOrderService.storeRefineryOrder(
          OWNER_ID,
          ORDER_ID,
          new RefineryOrderStoreDto(List.of(itemWithAmount(50.0, "fresh note"))),
          false);

      ArgumentCaptor<InventoryItem> captor = ArgumentCaptor.forClass(InventoryItem.class);
      verify(inventoryItemRepository, times(1)).save(captor.capture());
      InventoryItem saved = captor.getValue();
      assertSame(owner, saved.getUser());
      assertSame(material, saved.getMaterial());
      assertSame(location, saved.getLocation());
      assertEquals(50.0, saved.getAmount());
      assertEquals(500, saved.getQuality());
      assertEquals("fresh note", saved.getNote());
    }

    @Test
    void incrementsExistingInventoryItem_whenMatchFound() {
      InventoryItem existing = new InventoryItem();
      existing.setAmount(40.0);
      existing.setNote(null);

      stubLookupsForSingleItem();
      when(inventoryItemRepository.findMatchingInventoryItemForUpdate(
              any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(List.of(existing));

      refineryOrderService.storeRefineryOrder(
          OWNER_ID,
          ORDER_ID,
          new RefineryOrderStoreDto(List.of(itemWithAmount(10.0, null))),
          false);

      assertEquals(50.0, existing.getAmount(), "existing amount must accumulate, not replace");
      verify(inventoryItemRepository, times(1)).save(existing);
    }
  }

  // ------------------------------------------------------------------
  // Note merge semantics
  // ------------------------------------------------------------------

  @Nested
  class NoteMergeTests {

    @Test
    void existingItem_nullIncomingNote_keepsExistingNote() {
      InventoryItem existing = new InventoryItem();
      existing.setAmount(40.0);
      existing.setNote("keep this");

      stubLookupsForSingleItem();
      when(inventoryItemRepository.findMatchingInventoryItemForUpdate(
              any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(List.of(existing));

      refineryOrderService.storeRefineryOrder(
          OWNER_ID,
          ORDER_ID,
          new RefineryOrderStoreDto(List.of(itemWithAmount(10.0, null))),
          false);

      assertEquals("keep this", existing.getNote());
    }

    @Test
    void existingItem_blankIncomingNote_keepsExistingNote() {
      // normalizeNote() turns blank-only strings into null, so this path
      // collapses into the null-incoming-note branch.
      InventoryItem existing = new InventoryItem();
      existing.setAmount(40.0);
      existing.setNote("keep this");

      stubLookupsForSingleItem();
      when(inventoryItemRepository.findMatchingInventoryItemForUpdate(
              any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(List.of(existing));

      refineryOrderService.storeRefineryOrder(
          OWNER_ID,
          ORDER_ID,
          new RefineryOrderStoreDto(List.of(itemWithAmount(10.0, "   "))),
          false);

      assertEquals("keep this", existing.getNote());
    }

    @Test
    void existingItem_nullExistingNote_replacedByIncoming() {
      InventoryItem existing = new InventoryItem();
      existing.setAmount(40.0);
      existing.setNote(null);

      stubLookupsForSingleItem();
      when(inventoryItemRepository.findMatchingInventoryItemForUpdate(
              any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(List.of(existing));

      refineryOrderService.storeRefineryOrder(
          OWNER_ID,
          ORDER_ID,
          new RefineryOrderStoreDto(List.of(itemWithAmount(10.0, "fresh"))),
          false);

      assertEquals("fresh", existing.getNote());
    }

    @Test
    void existingItem_blankExistingNote_replacedByIncoming() {
      InventoryItem existing = new InventoryItem();
      existing.setAmount(40.0);
      existing.setNote("   ");

      stubLookupsForSingleItem();
      when(inventoryItemRepository.findMatchingInventoryItemForUpdate(
              any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(List.of(existing));

      refineryOrderService.storeRefineryOrder(
          OWNER_ID,
          ORDER_ID,
          new RefineryOrderStoreDto(List.of(itemWithAmount(10.0, "fresh"))),
          false);

      assertEquals("fresh", existing.getNote());
    }

    @Test
    void existingItem_novelIncomingNote_appendedWithNewline() {
      InventoryItem existing = new InventoryItem();
      existing.setAmount(40.0);
      existing.setNote("first batch");

      stubLookupsForSingleItem();
      when(inventoryItemRepository.findMatchingInventoryItemForUpdate(
              any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(List.of(existing));

      refineryOrderService.storeRefineryOrder(
          OWNER_ID,
          ORDER_ID,
          new RefineryOrderStoreDto(List.of(itemWithAmount(10.0, "second batch"))),
          false);

      assertEquals(
          "first batch\nsecond batch",
          existing.getNote(),
          "novel notes append with newline separator");
    }

    @Test
    void existingItem_duplicateIncomingNote_doesNotDuplicate() {
      InventoryItem existing = new InventoryItem();
      existing.setAmount(40.0);
      existing.setNote("first batch (urgent)");

      stubLookupsForSingleItem();
      when(inventoryItemRepository.findMatchingInventoryItemForUpdate(
              any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(List.of(existing));

      refineryOrderService.storeRefineryOrder(
          OWNER_ID,
          ORDER_ID,
          new RefineryOrderStoreDto(List.of(itemWithAmount(10.0, "urgent"))),
          false);

      assertEquals(
          "first batch (urgent)",
          existing.getNote(),
          "existing note already contains the substring -> no append");
    }

    @Test
    void existingItem_combinedNoteOverflowing1000Chars_isTruncated() {
      // Existing note: 900 chars. Incoming note: 150 chars.
      // Combined would be 900 + 1 + 150 = 1051 -> truncated to 1000.
      String existingNote = "x".repeat(900);
      String incomingNote = "y".repeat(150);
      InventoryItem existing = new InventoryItem();
      existing.setAmount(40.0);
      existing.setNote(existingNote);

      stubLookupsForSingleItem();
      when(inventoryItemRepository.findMatchingInventoryItemForUpdate(
              any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(List.of(existing));

      refineryOrderService.storeRefineryOrder(
          OWNER_ID,
          ORDER_ID,
          new RefineryOrderStoreDto(List.of(itemWithAmount(10.0, incomingNote))),
          false);

      assertEquals(1000, existing.getNote().length());
      assertTrue(
          existing.getNote().startsWith(existingNote),
          "the existing note must be preserved verbatim at the front");
    }

    @Test
    void newItem_storesNormalizedIncomingNote() {
      stubLookupsForSingleItem();
      when(inventoryItemRepository.findMatchingInventoryItemForUpdate(
              any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(Collections.emptyList());

      refineryOrderService.storeRefineryOrder(
          OWNER_ID,
          ORDER_ID,
          new RefineryOrderStoreDto(List.of(itemWithAmount(10.0, "  trim me  "))),
          false);

      ArgumentCaptor<InventoryItem> captor = ArgumentCaptor.forClass(InventoryItem.class);
      verify(inventoryItemRepository).save(captor.capture());
      assertEquals("trim me", captor.getValue().getNote());
    }

    @Test
    void newItem_blankIncomingNote_storedAsNull() {
      stubLookupsForSingleItem();
      when(inventoryItemRepository.findMatchingInventoryItemForUpdate(
              any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(Collections.emptyList());

      refineryOrderService.storeRefineryOrder(
          OWNER_ID,
          ORDER_ID,
          new RefineryOrderStoreDto(List.of(itemWithAmount(10.0, "   "))),
          false);

      ArgumentCaptor<InventoryItem> captor = ArgumentCaptor.forClass(InventoryItem.class);
      verify(inventoryItemRepository).save(captor.capture());
      assertNull(captor.getValue().getNote());
    }
  }

  // ------------------------------------------------------------------
  // updateGoodOutputQuantity (SCU vs PIECE, @Min(1) clamp, no-match)
  // ------------------------------------------------------------------

  @Nested
  class GoodOutputQuantityTests {

    @Test
    void scuMaterial_amountConvertedToUnits_x100() {
      Material scuMaterial = newMaterial(QuantityType.SCU);
      RefineryGood good = newGoodWithOutput(scuMaterial);
      order.setGoods(new HashSet<>(Set.of(good)));

      storeWithMaterial(scuMaterial, 1.234);

      // 1.234 SCU -> round(123.4) -> 123 units
      assertEquals(123, good.getOutputQuantity());
    }

    @Test
    void pieceMaterial_amountUsedDirectly() {
      Material pieceMaterial = newMaterial(QuantityType.PIECE);
      RefineryGood good = newGoodWithOutput(pieceMaterial);
      order.setGoods(new HashSet<>(Set.of(good)));

      storeWithMaterial(pieceMaterial, 42.4);

      // round(42.4) = 42 units
      assertEquals(42, good.getOutputQuantity());
    }

    @Test
    void nullQuantityType_amountUsedDirectly() {
      Material untyped = newMaterial(null);
      RefineryGood good = newGoodWithOutput(untyped);
      order.setGoods(new HashSet<>(Set.of(good)));

      storeWithMaterial(untyped, 10.7);

      // The non-SCU branch rounds: round(10.7) = 11.
      assertEquals(11, good.getOutputQuantity());
    }

    @Test
    void zeroAmount_isClampedToOne() {
      // @Min(1) on RefineryGood.outputQuantity makes 0 invalid;
      // the service must clamp upwards rather than persist an invalid value.
      Material pieceMaterial = newMaterial(QuantityType.PIECE);
      RefineryGood good = newGoodWithOutput(pieceMaterial);
      order.setGoods(new HashSet<>(Set.of(good)));

      storeWithMaterial(pieceMaterial, 0.0);

      assertEquals(1, good.getOutputQuantity());
    }

    @Test
    void noMatchingGood_silentlySkipsTheUpdate() {
      // Good's output material is DIFFERENT from the stored item's material;
      // no update should happen, and outputQuantity must keep its previous value.
      Material storedMaterial = newMaterial(QuantityType.SCU);
      Material differentOutputMaterial = newMaterial(QuantityType.SCU);
      RefineryGood good = newGoodWithOutput(differentOutputMaterial);
      good.setOutputQuantity(999);
      order.setGoods(new HashSet<>(Set.of(good)));

      storeWithMaterial(storedMaterial, 50.0);

      assertEquals(
          999,
          good.getOutputQuantity(),
          "no match -> previous outputQuantity is preserved verbatim");
    }

    @Test
    void goodWithNullOutputMaterial_silentlyIgnored() {
      // No NPE even if a malformed RefineryGood has no output material set.
      Material stored = newMaterial(QuantityType.SCU);
      RefineryGood broken = new RefineryGood();
      broken.setOutputMaterial(null);
      RefineryGood good = newGoodWithOutput(stored);
      order.setGoods(new HashSet<>(Set.of(broken, good)));

      storeWithMaterial(stored, 5.0);

      assertEquals(
          500,
          good.getOutputQuantity(),
          "broken sibling RefineryGood with null outputMaterial must not "
              + "crash the loop nor block the legitimate match");
    }

    @Test
    void goodsCollectionIsNull_silentlyReturns() {
      // Edge: order.goods == null. Should not NPE; just no update happens.
      order.setGoods(null);

      // Use a single item with a valid material; the storeRefineryOrder path
      // still calls updateGoodOutputQuantity, which must return on null goods.
      stubLookupsForSingleItem();
      when(inventoryItemRepository.findMatchingInventoryItemForUpdate(
              any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(Collections.emptyList());

      // Just verify it doesn't throw.
      refineryOrderService.storeRefineryOrder(
          OWNER_ID,
          ORDER_ID,
          new RefineryOrderStoreDto(List.of(itemWithAmount(10.0, null))),
          false);

      verify(refineryOrderRepository, times(1)).save(order);
    }
  }

  // ------------------------------------------------------------------
  // Final state transition
  // ------------------------------------------------------------------

  @Test
  void afterAllItemsProcessed_orderStatusIsCOMPLETED_andOrderSaved() {
    stubLookupsForSingleItem();
    when(inventoryItemRepository.findMatchingInventoryItemForUpdate(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());

    refineryOrderService.storeRefineryOrder(
        OWNER_ID, ORDER_ID, new RefineryOrderStoreDto(List.of(itemWithAmount(10.0, null))), false);

    assertEquals(RefineryOrderStatus.COMPLETED, order.getStatus());
    verify(refineryOrderRepository, times(1)).save(order);
  }

  @Test
  void multipleItems_allInsertedAndOrderCompletedExactlyOnce() {
    stubLookupsForSingleItem();
    // Also stub for a second material id; we'll reuse the same material to keep
    // the fixture light — the stub uses ArgumentMatchers.any() in this case.
    when(inventoryItemRepository.findMatchingInventoryItemForUpdate(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());

    refineryOrderService.storeRefineryOrder(
        OWNER_ID,
        ORDER_ID,
        new RefineryOrderStoreDto(
            List.of(itemWithAmount(10.0, "note1"), itemWithAmount(20.0, "note2"))),
        false);

    verify(inventoryItemRepository, times(2)).save(any(InventoryItem.class));
    verify(refineryOrderRepository, times(1)).save(order); // order saved exactly once
    assertEquals(RefineryOrderStatus.COMPLETED, order.getStatus());
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  /**
   * Stubs the repositories required by a single-item store call where the item references the
   * default material + location + no explicit user + no job order.
   */
  private void stubLookupsForSingleItem() {
    lenient().when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
    lenient().when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.of(material));
    lenient().when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(location));
  }

  private void storeWithMaterial(Material material, double amount) {
    lenient().when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
    lenient()
        .when(materialRepository.findById(eq(material.getId())))
        .thenReturn(Optional.of(material));
    lenient().when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(location));
    lenient()
        .when(
            inventoryItemRepository.findMatchingInventoryItemForUpdate(
                any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());

    RefineryOrderStoreItemDto dto =
        new RefineryOrderStoreItemDto(material.getId(), LOCATION_ID, 500, amount, null, null, null);
    refineryOrderService.storeRefineryOrder(
        OWNER_ID, ORDER_ID, new RefineryOrderStoreDto(List.of(dto)), false);
  }

  private static RefineryOrderStoreItemDto item(UUID userId, UUID jobOrderId) {
    return itemWithAmount(10.0, null, userId, jobOrderId);
  }

  private static RefineryOrderStoreItemDto itemWithAmount(double amount, String note) {
    return itemWithAmount(amount, note, null, null);
  }

  private static RefineryOrderStoreItemDto itemWithAmount(
      double amount, String note, UUID userId, UUID jobOrderId) {
    return new RefineryOrderStoreItemDto(
        MATERIAL_ID, LOCATION_ID, 500, amount, userId, jobOrderId, note);
  }

  private static Material newMaterial(QuantityType type) {
    Material m = new Material();
    m.setId(UUID.randomUUID());
    m.setName("Material " + m.getId());
    m.setQuantityType(type);
    return m;
  }

  private static RefineryGood newGoodWithOutput(Material outputMaterial) {
    RefineryGood good = new RefineryGood();
    good.setOutputMaterial(outputMaterial);
    good.setOutputQuantity(1); // initial value to detect when it's overwritten
    return good;
  }

  /**
   * Tests for the UEX-derived yield-bonus lookup. The contract: pick the right name field
   * (city.name vs spaceStation.name) and map back to {@code materialId → yieldBonus}; never
   * fabricate data for a location that has no city/station hook into the universe sync.
   */
  @Nested
  class GetYieldBonusByMaterialForLocationTests {

    @Test
    void nullLocation_returnsEmpty() {
      assertTrue(refineryOrderService.getYieldBonusByMaterialForLocation(null).isEmpty());
    }

    @Test
    void locationWithoutCityAndStation_returnsEmpty() {
      Location naked = new Location();
      naked.setId(UUID.randomUUID());
      naked.setName("Custom");

      Map<UUID, Integer> result = refineryOrderService.getYieldBonusByMaterialForLocation(naked);

      assertTrue(result.isEmpty());
    }

    @Test
    void cityLocation_queriesByCityName_andReturnsMaterialBonusMap() {
      UUID matA = UUID.randomUUID();
      UUID matB = UUID.randomUUID();
      Material a = new Material();
      a.setId(matA);
      Material b = new Material();
      b.setId(matB);

      RefineryYield y1 = new RefineryYield();
      y1.setMaterial(a);
      y1.setYieldBonus(5);
      RefineryYield y2 = new RefineryYield();
      y2.setMaterial(b);
      y2.setYieldBonus(-3);

      City lorville = new City();
      lorville.setName("Lorville");

      Location loc = new Location();
      loc.setId(UUID.randomUUID());
      loc.setCity(lorville);

      when(refineryYieldRepository.findAllForLocation(eq("Lorville"), eq(null)))
          .thenReturn(List.of(y1, y2));

      Map<UUID, Integer> result = refineryOrderService.getYieldBonusByMaterialForLocation(loc);

      assertEquals(2, result.size());
      assertEquals(5, result.get(matA));
      assertEquals(-3, result.get(matB));
    }

    @Test
    void spaceStationLocation_queriesByStationName() {
      UUID matA = UUID.randomUUID();
      Material a = new Material();
      a.setId(matA);

      RefineryYield y = new RefineryYield();
      y.setMaterial(a);
      y.setYieldBonus(2);

      SpaceStation arcL1 = new SpaceStation();
      arcL1.setName("ARC-L1 Wide Forest Station");

      Location loc = new Location();
      loc.setId(UUID.randomUUID());
      loc.setSpaceStation(arcL1);

      when(refineryYieldRepository.findAllForLocation(eq(null), eq("ARC-L1 Wide Forest Station")))
          .thenReturn(List.of(y));

      Map<UUID, Integer> result = refineryOrderService.getYieldBonusByMaterialForLocation(loc);

      assertEquals(1, result.size());
      assertEquals(2, result.get(matA));
    }

    @Test
    void zeroBonusValue_isPreserved_notTreatedAsMissing() {
      // A 0% yield row is a real UEX-published value (the commodity refines at the baseline
      // yield) — it must end up in the map so the UI can distinguish it from "no data".
      UUID matA = UUID.randomUUID();
      Material a = new Material();
      a.setId(matA);

      RefineryYield y = new RefineryYield();
      y.setMaterial(a);
      y.setYieldBonus(0);

      SpaceStation station = new SpaceStation();
      station.setName("CRU-L1");

      Location loc = new Location();
      loc.setId(UUID.randomUUID());
      loc.setSpaceStation(station);

      when(refineryYieldRepository.findAllForLocation(eq(null), eq("CRU-L1")))
          .thenReturn(List.of(y));

      Map<UUID, Integer> result = refineryOrderService.getYieldBonusByMaterialForLocation(loc);

      assertTrue(result.containsKey(matA));
      assertEquals(0, result.get(matA));
    }

    @Test
    void byLocationId_nullId_returnsEmpty() {
      assertTrue(refineryOrderService.getYieldBonusByMaterialForLocationId(null).isEmpty());
    }

    @Test
    void byLocationId_unknownId_returnsEmpty() {
      UUID missing = UUID.randomUUID();
      when(locationRepository.findById(missing)).thenReturn(Optional.empty());

      Map<UUID, Integer> result =
          refineryOrderService.getYieldBonusByMaterialForLocationId(missing);

      assertTrue(result.isEmpty());
    }

    @Test
    void byLocationId_delegatesToLocationVariant() {
      UUID matA = UUID.randomUUID();
      Material a = new Material();
      a.setId(matA);

      RefineryYield y = new RefineryYield();
      y.setMaterial(a);
      y.setYieldBonus(7);

      City lorville = new City();
      lorville.setName("Lorville");

      UUID locId = UUID.randomUUID();
      Location loc = new Location();
      loc.setId(locId);
      loc.setCity(lorville);

      when(locationRepository.findById(locId)).thenReturn(Optional.of(loc));
      when(refineryYieldRepository.findAllForLocation(eq("Lorville"), eq(null)))
          .thenReturn(List.of(y));

      Map<UUID, Integer> result = refineryOrderService.getYieldBonusByMaterialForLocationId(locId);

      assertEquals(1, result.size());
      assertEquals(7, result.get(matA));
    }

    @Test
    void yieldWithNullMaterial_isSkipped() {
      RefineryYield orphan = new RefineryYield();
      orphan.setMaterial(null);
      orphan.setYieldBonus(99);

      City city = new City();
      city.setName("Area18");

      Location loc = new Location();
      loc.setId(UUID.randomUUID());
      loc.setCity(city);

      when(refineryYieldRepository.findAllForLocation(eq("Area18"), eq(null)))
          .thenReturn(List.of(orphan));

      Map<UUID, Integer> result = refineryOrderService.getYieldBonusByMaterialForLocation(loc);

      assertTrue(result.isEmpty());
    }
  }

  // --- R5.d.b createRefineryOrder picker delegation -------------------------
  // The membership-validation + Squadron-resolution logic itself is pinned by
  // OwnerScopeServiceTest. These tests verify that createRefineryOrder routes the picker output
  // through the shared resolver instead of stamping the order owner's home Staffel directly.

  @Nested
  class CreateOrderPickerDelegationTests {

    @Test
    void createRefineryOrder_delegatesPickerResolutionToOwnerScopeService() {
      UUID userId = UUID.randomUUID();
      UUID pickedOrgUnitId = UUID.randomUUID();

      User user = new User();
      user.setId(userId);
      when(userRepository.findById(userId)).thenReturn(Optional.of(user));

      de.greluc.krt.iri.basetool.backend.model.Squadron resolved =
          new de.greluc.krt.iri.basetool.backend.model.Squadron();
      resolved.setId(pickedOrgUnitId);
      when(ownerScopeService.resolveOrgUnitForPickerOutput(user, pickedOrgUnitId))
          .thenReturn(resolved);

      Location loc = new Location();
      loc.setId(UUID.randomUUID());
      SpaceStation station = new SpaceStation();
      station.setHasRefinery(true);
      loc.setSpaceStation(station);
      RefineryOrder transientOrder = new RefineryOrder();
      transientOrder.setLocation(loc);
      when(locationRepository.findById(loc.getId())).thenReturn(Optional.of(loc));
      when(refineryOrderRepository.save(any(RefineryOrder.class)))
          .thenAnswer(i -> i.getArgument(0));

      RefineryOrder saved =
          refineryOrderService.createRefineryOrder(userId, transientOrder, pickedOrgUnitId);

      assertSame(
          resolved,
          saved.getOwningOrgUnit(),
          "the picker output must be honoured verbatim, not user.getSquadron()");
    }
  }
}

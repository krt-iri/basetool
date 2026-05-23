package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.mapper.JobOrderHandoverMapper;
import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderHandover;
import de.greluc.krt.iri.basetool.backend.model.QuantityType;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverItemCreateDto;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderHandoverRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderMaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobOrderHandoverServiceTest {

  @Mock private JobOrderRepository jobOrderRepository;
  @Mock private JobOrderHandoverRepository jobOrderHandoverRepository;
  @Mock private InventoryItemRepository inventoryItemRepository;
  @Mock private JobOrderHandoverMapper jobOrderHandoverMapper;
  @Mock private JobOrderMaterialRepository jobOrderMaterialRepository;
  @Mock private JobOrderService jobOrderService;
  @Mock private UserService userService;

  @InjectMocks private JobOrderHandoverService service;

  private UUID orderId;
  private UUID inventoryId;
  private UUID materialId;
  private JobOrder order;
  private InventoryItem inventoryItem;
  private de.greluc.krt.iri.basetool.backend.model.Material material;
  private de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial jobOrderMaterial;

  @BeforeEach
  void setUp() {
    orderId = UUID.randomUUID();
    inventoryId = UUID.randomUUID();
    materialId = UUID.randomUUID();
    order = new JobOrder();
    order.setId(orderId);

    material = new de.greluc.krt.iri.basetool.backend.model.Material();
    material.setId(materialId);

    jobOrderMaterial = new de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial();
    jobOrderMaterial.setId(UUID.randomUUID());
    jobOrderMaterial.setMaterial(material);
    jobOrderMaterial.setAmount(10.0);
    order.addMaterial(jobOrderMaterial);

    inventoryItem = new InventoryItem();
    inventoryItem.setId(inventoryId);
    inventoryItem.setJobOrder(order);
    inventoryItem.setMaterial(material);
    inventoryItem.setAmount(10.0);
  }

  @Test
  void createHandover_shouldReduceInventoryAmount_whenAmountIsSmallerThanStock() {
    // Given
    JobOrderHandoverItemCreateDto itemDto = new JobOrderHandoverItemCreateDto(inventoryId, 4.0);
    JobOrderHandoverCreateDto createDto =
        new JobOrderHandoverCreateDto(Instant.now(), "HanSolo", "Rogue", List.of(itemDto));

    // findById is called twice: once at the start and once after the loop to re-fetch the managed
    // entity
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(inventoryItemRepository.findByIdForUpdate(inventoryId))
        .thenReturn(Optional.of(inventoryItem));
    when(jobOrderHandoverRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(jobOrderHandoverMapper.toDto(any(JobOrderHandover.class)))
        .thenReturn(mock(JobOrderHandoverDto.class));

    // When
    service.createHandover(orderId, createDto);

    // Then
    assertEquals(6.0, inventoryItem.getAmount());
    assertEquals(6.0, jobOrderMaterial.getAmount());
    verify(inventoryItemRepository).save(inventoryItem);
    verify(inventoryItemRepository, never()).delete(any());
    verify(inventoryItemRepository, never()).unlinkJobOrderMaterial(any(), any());
    verify(jobOrderService, never()).completeJobOrderWithinTransaction(any());
    verify(jobOrderHandoverRepository).save(any(JobOrderHandover.class));
    // findById called twice: initial load + re-fetch after clearAutomatically evicts session cache
    verify(jobOrderRepository, times(2)).findById(orderId);
  }

  @Test
  void createHandover_shouldDeleteInventoryItem_whenAmountIsFullyHandedOver() {
    // Given
    JobOrderHandoverItemCreateDto itemDto = new JobOrderHandoverItemCreateDto(inventoryId, 10.0);
    JobOrderHandoverCreateDto createDto =
        new JobOrderHandoverCreateDto(Instant.now(), "HanSolo", null, List.of(itemDto));

    // findById is called twice: once at the start and once after the loop to re-fetch the managed
    // entity
    // (fix for clearAutomatically=true detaching jobOrder from session)
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(inventoryItemRepository.findByIdForUpdate(inventoryId))
        .thenReturn(Optional.of(inventoryItem));

    de.greluc.krt.iri.basetool.backend.model.JobOrderHandover[] persistedHandover =
        new de.greluc.krt.iri.basetool.backend.model.JobOrderHandover[1];
    when(jobOrderHandoverRepository.save(any()))
        .thenAnswer(
            i -> {
              persistedHandover[0] = i.getArgument(0);
              return persistedHandover[0];
            });
    when(jobOrderHandoverMapper.toDto(any(JobOrderHandover.class)))
        .thenReturn(mock(JobOrderHandoverDto.class));

    // When
    service.createHandover(orderId, createDto);

    // Then
    assertEquals(0.0, jobOrderMaterial.getAmount());
    verify(inventoryItemRepository).delete(inventoryItem);
    verify(inventoryItemRepository, never()).save(any());
    verify(inventoryItemRepository).unlinkJobOrderMaterial(orderId, materialId);
    // completeJobOrderWithinTransaction called with the re-fetched managed entity (same object in
    // unit test)
    verify(jobOrderService).completeJobOrderWithinTransaction(order);
    // findById called twice: initial load + re-fetch after clearAutomatically evicts session cache
    verify(jobOrderRepository, times(2)).findById(orderId);
    verify(jobOrderHandoverRepository).save(any(JobOrderHandover.class));
    // The persisted handover item carries the snapshot data (material, quality, amount) only;
    // there is no longer a reference to the (potentially deleted) inventory item. This is the
    // structural fix for the JobOrder Übergabe booking bug — see CHANGELOG / V64 migration.
    assertNotNull(persistedHandover[0]);
    assertEquals(1, persistedHandover[0].getItems().size());
    var snapshot = persistedHandover[0].getItems().iterator().next();
    assertEquals(material, snapshot.getMaterial());
    assertEquals(10.0, snapshot.getAmount());
  }

  @Test
  void createHandover_shouldThrowException_whenAmountExceedsStock() {
    // Given
    JobOrderHandoverItemCreateDto itemDto = new JobOrderHandoverItemCreateDto(inventoryId, 11.0);
    JobOrderHandoverCreateDto createDto =
        new JobOrderHandoverCreateDto(Instant.now(), "HanSolo", null, List.of(itemDto));

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(inventoryItemRepository.findByIdForUpdate(inventoryId))
        .thenReturn(Optional.of(inventoryItem));

    // When & Then
    BadRequestException ex =
        assertThrows(BadRequestException.class, () -> service.createHandover(orderId, createDto));
    assertTrue(ex.getMessage().contains("Cannot hand over more than the available amount"));
  }

  @Test
  void createHandover_shouldThrowException_whenJobOrderIsNullOnInventoryItem() {
    // Given — reproduces the bug where findByIdForUpdate loaded the InventoryItem without
    // eagerly fetching jobOrder (no @EntityGraph), causing jobOrder to be null even though
    // the item belongs to the order in the DB. The fix adds @EntityGraph to findByIdForUpdate.
    inventoryItem.setJobOrder(null);

    JobOrderHandoverItemCreateDto itemDto = new JobOrderHandoverItemCreateDto(inventoryId, 5.0);
    JobOrderHandoverCreateDto createDto =
        new JobOrderHandoverCreateDto(Instant.now(), "swing-by", null, List.of(itemDto));

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(inventoryItemRepository.findByIdForUpdate(inventoryId))
        .thenReturn(Optional.of(inventoryItem));

    // When & Then — before fix: this threw "Inventory item does not belong to this JobOrder"
    // because jobOrder was null (not eagerly loaded). After fix (@EntityGraph on findByIdForUpdate)
    // the jobOrder is always loaded and the check works correctly. The cross-staffel pre-write
    // guard (MULTI_SQUADRON_PLAN.md §4.4) now raises IllegalStateException — GlobalExceptionHandler
    // maps it to 400 so the wire format is unchanged.
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> service.createHandover(orderId, createDto));
    assertTrue(ex.getMessage().contains("Inventory item does not belong to this JobOrder"));
  }

  @Test
  void createHandover_shouldReduceBothJobOrderMaterialAmounts_whenTwoItemsHandedOver() {
    // Given — reproduces the bug: second JobOrderMaterial.amount was not reduced because
    // jobOrderMaterialRepository.save(mat) was missing, so the change was lost after
    // clearAutomatically = true flushed the Hibernate first-level cache.
    UUID inventoryId2 = UUID.randomUUID();
    UUID materialId2 = UUID.randomUUID();

    de.greluc.krt.iri.basetool.backend.model.Material material2 =
        new de.greluc.krt.iri.basetool.backend.model.Material();
    material2.setId(materialId2);

    de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial jobOrderMaterial2 =
        new de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial();
    jobOrderMaterial2.setId(UUID.randomUUID());
    jobOrderMaterial2.setMaterial(material2);
    jobOrderMaterial2.setAmount(8.0);
    order.addMaterial(jobOrderMaterial2);

    InventoryItem inventoryItem2 = new InventoryItem();
    inventoryItem2.setId(inventoryId2);
    inventoryItem2.setJobOrder(order);
    inventoryItem2.setMaterial(material2);
    inventoryItem2.setAmount(8.0);

    // Hand over 5.0 of material1 (partial) and 8.0 of material2 (full)
    JobOrderHandoverItemCreateDto itemDto1 = new JobOrderHandoverItemCreateDto(inventoryId, 5.0);
    JobOrderHandoverItemCreateDto itemDto2 = new JobOrderHandoverItemCreateDto(inventoryId2, 8.0);
    JobOrderHandoverCreateDto createDto =
        new JobOrderHandoverCreateDto(Instant.now(), "swing-by", null, List.of(itemDto1, itemDto2));

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(inventoryItemRepository.findByIdForUpdate(inventoryId))
        .thenReturn(Optional.of(inventoryItem));
    when(inventoryItemRepository.findByIdForUpdate(inventoryId2))
        .thenReturn(Optional.of(inventoryItem2));
    when(jobOrderHandoverRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(jobOrderHandoverMapper.toDto(any(JobOrderHandover.class)))
        .thenReturn(mock(JobOrderHandoverDto.class));

    // When
    assertDoesNotThrow(() -> service.createHandover(orderId, createDto));

    // Then — both JobOrderMaterial amounts must be correctly reduced via Hibernate dirty
    // checking. We deliberately do NOT call jobOrderMaterialRepository.save(mat) inside the
    // loop anymore: a save() on a (potentially) detached entity silently triggers a merge()
    // and produces a second version bump, which used to cause
    // ObjectOptimisticLockingFailureException (HTTP 409) when an entire JobOrder was
    // fulfilled by a single multi-material handover. The dirty changes are flushed by the
    // post-loop bulk unlink (flushAutomatically=true) and/or at transaction commit.
    assertEquals(
        5.0,
        jobOrderMaterial.getAmount(),
        0.0001,
        "First material's open amount must be reduced from 10.0 to 5.0");
    assertEquals(
        0.0,
        jobOrderMaterial2.getAmount(),
        0.0001,
        "Second material's open amount must be reduced from 8.0 to 0.0");
    // No explicit save() on JobOrderMaterial — dirty checking handles it.
    verify(jobOrderMaterialRepository, never()).save(any());
    // material2 fully handed over → inventory deleted and unlinked
    verify(inventoryItemRepository).delete(inventoryItem2);
    verify(inventoryItemRepository).unlinkJobOrderMaterial(orderId, materialId2);
    // material1 partial → inventory saved, not deleted
    verify(inventoryItemRepository).save(inventoryItem);
    // Not all fulfilled → order must NOT be completed
    verify(jobOrderService, never()).completeJobOrderWithinTransaction(any());
  }

  @Test
  void createHandover_shouldSucceed_whenMultipleItemsHandedOver_andFirstItemFullyConsumed() {
    // Given — reproduces the bug where unlinkJobOrderMaterial() (a @Modifying bulk-update)
    // invalidated the Hibernate first-level cache, causing the second item's jobOrder
    // association to appear null on the next findByIdForUpdate call → HTTP 400.
    // Fix: @Modifying(clearAutomatically = true) on unlinkJobOrderMaterial.
    UUID inventoryId2 = UUID.randomUUID();
    UUID materialId2 = UUID.randomUUID();

    de.greluc.krt.iri.basetool.backend.model.Material material2 =
        new de.greluc.krt.iri.basetool.backend.model.Material();
    material2.setId(materialId2);

    de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial jobOrderMaterial2 =
        new de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial();
    jobOrderMaterial2.setId(UUID.randomUUID());
    jobOrderMaterial2.setMaterial(material2);
    jobOrderMaterial2.setAmount(5.0);
    order.addMaterial(jobOrderMaterial2);

    InventoryItem inventoryItem2 = new InventoryItem();
    inventoryItem2.setId(inventoryId2);
    inventoryItem2.setJobOrder(order);
    inventoryItem2.setMaterial(material2);
    inventoryItem2.setAmount(5.0);

    // First item is fully consumed (triggers unlinkJobOrderMaterial), second is partial
    JobOrderHandoverItemCreateDto itemDto1 = new JobOrderHandoverItemCreateDto(inventoryId, 10.0);
    JobOrderHandoverItemCreateDto itemDto2 = new JobOrderHandoverItemCreateDto(inventoryId2, 3.0);
    JobOrderHandoverCreateDto createDto =
        new JobOrderHandoverCreateDto(Instant.now(), "swing-by", null, List.of(itemDto1, itemDto2));

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(inventoryItemRepository.findByIdForUpdate(inventoryId))
        .thenReturn(Optional.of(inventoryItem));
    when(inventoryItemRepository.findByIdForUpdate(inventoryId2))
        .thenReturn(Optional.of(inventoryItem2));
    when(jobOrderHandoverRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(jobOrderHandoverMapper.toDto(any(JobOrderHandover.class)))
        .thenReturn(mock(JobOrderHandoverDto.class));

    // When — must not throw (before fix: threw "Inventory item does not belong to this JobOrder"
    // for the second item because the bulk-update cleared the cache and jobOrder appeared null)
    assertDoesNotThrow(() -> service.createHandover(orderId, createDto));

    // Then
    verify(inventoryItemRepository).delete(inventoryItem);
    verify(inventoryItemRepository).unlinkJobOrderMaterial(orderId, materialId);
    assertEquals(2.0, inventoryItem2.getAmount());
    verify(inventoryItemRepository).save(inventoryItem2);
  }

  @Test
  void createHandover_shouldNotCompleteOrder_whenMaterialStillOpen() {
    // Given — only part of the required material is handed over; the order must NOT be completed
    JobOrderHandoverItemCreateDto itemDto = new JobOrderHandoverItemCreateDto(inventoryId, 4.0);
    JobOrderHandoverCreateDto createDto =
        new JobOrderHandoverCreateDto(Instant.now(), "HanSolo", null, List.of(itemDto));

    // jobOrderMaterial.amount = 10.0, only 4.0 handed over → 6.0 still open
    // findById is called twice: once at the start and once for the re-fetch after the loop
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(inventoryItemRepository.findByIdForUpdate(inventoryId))
        .thenReturn(Optional.of(inventoryItem));
    when(jobOrderHandoverRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(jobOrderHandoverMapper.toDto(any(JobOrderHandover.class)))
        .thenReturn(mock(JobOrderHandoverDto.class));

    // When
    service.createHandover(orderId, createDto);

    // Then — status must NOT be set to COMPLETED because material is still open
    verify(jobOrderService, never()).updateJobOrderStatus(any(), any());
    assertEquals(6.0, jobOrderMaterial.getAmount(), 0.0001);
  }

  @Test
  void createHandover_shouldNotCompleteOrder_whenInventoryItemLinkedToOrder() {
    // Given — inventory item still belongs to the order (not fully handed over)
    JobOrderHandoverItemCreateDto itemDto = new JobOrderHandoverItemCreateDto(inventoryId, 3.0);
    JobOrderHandoverCreateDto createDto =
        new JobOrderHandoverCreateDto(Instant.now(), "HanSolo", null, List.of(itemDto));

    // findById is called twice: once at the start and once for the re-fetch after the loop
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(inventoryItemRepository.findByIdForUpdate(inventoryId))
        .thenReturn(Optional.of(inventoryItem));
    when(jobOrderHandoverRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(jobOrderHandoverMapper.toDto(any(JobOrderHandover.class)))
        .thenReturn(mock(JobOrderHandoverDto.class));

    // When
    service.createHandover(orderId, createDto);

    // Then — inventory item must still be linked (saved with reduced amount, not deleted/unlinked)
    verify(inventoryItemRepository).save(inventoryItem);
    verify(inventoryItemRepository, never()).delete(any());
    verify(inventoryItemRepository, never()).unlinkJobOrderMaterial(any(), any());
    // Order must remain open
    verify(jobOrderService, never()).completeJobOrderWithinTransaction(any());
  }

  @Test
  void createHandover_shouldThrowException_whenItemDoesNotBelongToOrder() {
    // Given
    JobOrder otherOrder = new JobOrder();
    otherOrder.setId(UUID.randomUUID());
    inventoryItem.setJobOrder(otherOrder);

    JobOrderHandoverItemCreateDto itemDto = new JobOrderHandoverItemCreateDto(inventoryId, 5.0);
    JobOrderHandoverCreateDto createDto =
        new JobOrderHandoverCreateDto(Instant.now(), "HanSolo", null, List.of(itemDto));

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(inventoryItemRepository.findByIdForUpdate(inventoryId))
        .thenReturn(Optional.of(inventoryItem));

    // When & Then — plan §4.4 cross-staffel pre-write guard raises IllegalStateException
    // (GlobalExceptionHandler maps it to HTTP 400).
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> service.createHandover(orderId, createDto));
    assertTrue(ex.getMessage().contains("Inventory item does not belong to this JobOrder"));
  }

  @Test
  void createHandover_shouldThrowException_whenAmountExceedsRemainingAmount() {
    // Given — inventoryItem.amount = 10.0, but 15.0 is requested (exceeds remaining)
    JobOrderHandoverItemCreateDto itemDto = new JobOrderHandoverItemCreateDto(inventoryId, 15.0);
    JobOrderHandoverCreateDto createDto =
        new JobOrderHandoverCreateDto(Instant.now(), "HanSolo", null, List.of(itemDto));
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(inventoryItemRepository.findByIdForUpdate(inventoryId))
        .thenReturn(Optional.of(inventoryItem));

    // When & Then — must reject with 400 Bad Request
    BadRequestException ex =
        assertThrows(BadRequestException.class, () -> service.createHandover(orderId, createDto));
    assertTrue(
        ex.getMessage().contains("Cannot hand over more than the available amount"),
        "Exception message must indicate amount exceeds available stock");
    // Inventory item must NOT be modified
    verify(inventoryItemRepository, never()).save(any());
    verify(inventoryItemRepository, never()).delete(any());
  }

  @Test
  void createHandover_shouldCallCompleteJobOrderWithinTransaction_whenAllMaterialsHandedOver() {
    // Given — reproduces the bug: when ALL remaining materials of a JobOrder are handed over in a
    // single handover, the old code called jobOrderService.updateJobOrderStatus() which executed
    // its own findById() + save() + flush() inside the same transaction. Since the jobOrder entity
    // was already modified (via cascade on jobOrderHandoverRepository.save()), this caused a
    // double-save that triggered ObjectOptimisticLockingFailureException (HTTP 409).
    // Fix: call completeJobOrderWithinTransaction(managedJobOrder) where managedJobOrder is the
    // re-fetched entity (after clearAutomatically=true evicts the session cache).
    JobOrderHandoverItemCreateDto itemDto = new JobOrderHandoverItemCreateDto(inventoryId, 10.0);
    JobOrderHandoverCreateDto createDto =
        new JobOrderHandoverCreateDto(Instant.now(), "swing-by", null, List.of(itemDto));

    // findById is called twice: once at the start and once after the loop (re-fetch fix)
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(inventoryItemRepository.findByIdForUpdate(inventoryId))
        .thenReturn(Optional.of(inventoryItem));
    when(jobOrderHandoverRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(jobOrderHandoverMapper.toDto(any(JobOrderHandover.class)))
        .thenReturn(mock(JobOrderHandoverDto.class));

    // When — must succeed without any OptimisticLockingFailureException
    assertDoesNotThrow(() -> service.createHandover(orderId, createDto));

    // Then — completeJobOrderWithinTransaction must be called with the re-fetched managed entity
    verify(jobOrderService).completeJobOrderWithinTransaction(order);
    verify(jobOrderService, never()).updateJobOrderStatus(any(), any());
    // findById called twice: initial load + re-fetch after clearAutomatically evicts session cache
    verify(jobOrderRepository, times(2)).findById(orderId);
  }

  @Test
  void
      createHandover_shouldCompleteOrder_whenLastRemainingMaterialHandedOverAfterPreviousPartialHandover() {
    // Given — reproduces the bug: after a previous partial handover, jobOrderMaterial.amount was
    // already reduced (e.g. from 10.0 to 4.0). The final handover hands over the last 4.0 SCU.
    // unlinkJobOrderMaterial() uses @Modifying(clearAutomatically = true) which evicts the
    // Hibernate first-level cache, DETACHING the jobOrder entity from the session. Without the
    // re-fetch fix, jobOrder.setStatus(COMPLETED) was called on a detached entity and never
    // flushed to DB → status remained OPEN.
    // Fix: re-fetch managedJobOrder via jobOrderRepository.findById() after the loop.
    jobOrderMaterial.setAmount(4.0); // simulates state after a previous partial handover
    inventoryItem.setAmount(4.0); // only 4.0 SCU left in inventory

    JobOrderHandoverItemCreateDto itemDto = new JobOrderHandoverItemCreateDto(inventoryId, 4.0);
    JobOrderHandoverCreateDto createDto =
        new JobOrderHandoverCreateDto(Instant.now(), "swing-by", null, List.of(itemDto));

    // findById is called twice: once at the start and once after the loop (re-fetch fix)
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(inventoryItemRepository.findByIdForUpdate(inventoryId))
        .thenReturn(Optional.of(inventoryItem));
    when(jobOrderHandoverRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(jobOrderHandoverMapper.toDto(any(JobOrderHandover.class)))
        .thenReturn(mock(JobOrderHandoverDto.class));

    // When
    assertDoesNotThrow(() -> service.createHandover(orderId, createDto));

    // Then — all materials fulfilled → order must be completed
    assertEquals(0.0, jobOrderMaterial.getAmount(), 0.0001);
    verify(jobOrderService).completeJobOrderWithinTransaction(order);
    verify(jobOrderService, never()).updateJobOrderStatus(any(), any());
    // findById called twice: initial load + re-fetch after clearAutomatically evicts session cache
    verify(jobOrderRepository, times(2)).findById(orderId);
  }

  @Test
  void createHandover_shouldThrowException_whenPieceMaterialHasDecimalAmount() {
    // Given — material is of type PIECE; decimal amounts are not allowed
    material.setQuantityType(QuantityType.PIECE);
    inventoryItem.setAmount(5.0);
    jobOrderMaterial.setAmount(5.0);

    JobOrderHandoverItemCreateDto itemDto = new JobOrderHandoverItemCreateDto(inventoryId, 2.5);
    JobOrderHandoverCreateDto createDto =
        new JobOrderHandoverCreateDto(Instant.now(), "HanSolo", null, List.of(itemDto));
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(inventoryItemRepository.findByIdForUpdate(inventoryId))
        .thenReturn(Optional.of(inventoryItem));

    // When & Then — must reject with 400 Bad Request because 2.5 is not a whole number
    BadRequestException ex =
        assertThrows(BadRequestException.class, () -> service.createHandover(orderId, createDto));
    assertTrue(
        ex.getMessage().contains("Amount must be a whole number for PIECE materials"),
        "Exception message must indicate that only integers are allowed for PIECE materials");
    // Inventory item must NOT be modified
    verify(inventoryItemRepository, never()).save(any());
    verify(inventoryItemRepository, never()).delete(any());
  }

  @Test
  void createHandover_shouldSucceed_whenInventoryItemBelongsToForeignSquadron() {
    // The cross-staffel Job-Order workspace is the central reason JobOrder lives
    // un-filtered: a Logistician from squadron C must be able to fulfil an order
    // authored by squadron A using an InventoryItem that is owned by squadron B,
    // as long as the item is linked to the order. The Phase-3 guard at
    // JobOrderHandoverService:116-119 only checks the linkage, NOT the squadron
    // identity, and this test pins that contract. Plan §11 "JobOrderHandoverService".
    de.greluc.krt.iri.basetool.backend.model.Squadron squadronA =
        new de.greluc.krt.iri.basetool.backend.model.Squadron();
    squadronA.setId(UUID.randomUUID());
    squadronA.setShorthand("ALF");
    de.greluc.krt.iri.basetool.backend.model.Squadron squadronB =
        new de.greluc.krt.iri.basetool.backend.model.Squadron();
    squadronB.setId(UUID.randomUUID());
    squadronB.setShorthand("BRV");

    order.setCreatingOrgUnit(squadronA);
    order.setRequestingOrgUnit(squadronA);
    inventoryItem.setOwningOrgUnit(squadronB);

    JobOrderHandoverItemCreateDto itemDto = new JobOrderHandoverItemCreateDto(inventoryId, 3.0);
    JobOrderHandoverCreateDto createDto =
        new JobOrderHandoverCreateDto(
            Instant.now(), "CrossSquadronHandler", "BRV", List.of(itemDto));

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(inventoryItemRepository.findByIdForUpdate(inventoryId))
        .thenReturn(Optional.of(inventoryItem));
    when(jobOrderHandoverRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(jobOrderHandoverMapper.toDto(any(JobOrderHandover.class)))
        .thenReturn(mock(JobOrderHandoverDto.class));

    // When — no exception even though item.owningSquadron != order.requestingSquadron
    service.createHandover(orderId, createDto);

    // Then — handover applied to the foreign-squadron item exactly like a same-squadron one
    assertEquals(7.0, inventoryItem.getAmount());
    assertEquals(7.0, jobOrderMaterial.getAmount());
    verify(inventoryItemRepository).save(inventoryItem);
    verify(jobOrderHandoverRepository).save(any(JobOrderHandover.class));
  }
}

package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.JobOrderMapper;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial;
import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderMaterialDto;
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderMaterialDto;
import de.greluc.krt.iri.basetool.backend.model.dto.LocationReferenceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UpdateJobOrderStatusDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UserReferenceDto;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import java.time.Instant;
import java.util.ArrayList;
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
class JobOrderServiceTest {

  @Mock private JobOrderRepository jobOrderRepository;

  @Mock private MaterialRepository materialRepository;

  @Mock private InventoryItemRepository inventoryItemRepository;

  @Mock private de.greluc.krt.iri.basetool.backend.repository.UserRepository userRepository;

  @Mock private SquadronRepository squadronRepository;

  @Mock private SquadronScopeService squadronScopeService;

  @Mock private JobOrderMapper jobOrderMapper;

  @Mock private de.greluc.krt.iri.basetool.backend.mapper.InventoryItemMapper inventoryItemMapper;

  @InjectMocks private JobOrderService jobOrderService;

  private Material material;
  private MaterialDto materialDto;
  private JobOrder jobOrder;
  private JobOrderDto baseJobOrderDto;
  private UUID orderId;
  private UUID materialId;

  @BeforeEach
  void setUp() {
    orderId = UUID.randomUUID();
    materialId = UUID.randomUUID();

    // Multi-tenant: every create/update path resolves the caller's current squadron via the
    // scope service plus an optional repository lookup by shorthand. Stub lenient defaults so
    // the existing fixtures still see the squadron string they wrote ("Alpha" / "Beta" / ...)
    // round-tripped through requestingSquadron.shorthand back into the legacy `squadron`
    // column. Read-path tests do not call these methods and are unaffected (lenient suppresses
    // UnnecessaryStubbingException).
    Squadron currentSquadron = new Squadron();
    currentSquadron.setShorthand("Alpha");
    org.mockito.Mockito.lenient()
        .when(squadronScopeService.currentSquadron())
        .thenReturn(java.util.Optional.of(currentSquadron));
    org.mockito.Mockito.lenient()
        .when(squadronRepository.findByShorthand(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              Squadron s = new Squadron();
              s.setShorthand(inv.getArgument(0));
              return java.util.Optional.of(s);
            });

    material = new Material();
    material.setId(materialId);
    material.setName("Gold");

    materialDto =
        new MaterialDto(
            materialId,
            "Gold",
            "RAW",
            "SCU",
            "Some desc",
            null,
            null,
            false,
            false,
            false,
            false,
            false,
            0L);

    jobOrder = new JobOrder();
    jobOrder.setId(orderId);
    // V88 removed JobOrder.squadron (legacy VARCHAR). Pre-V88 fixtures used
    // setSquadron("Alpha") as the only squadron stamp; post-V88 tests must set
    // requestingSquadron / creatingSquadron explicitly.
    Squadron alpha = new Squadron();
    alpha.setShorthand("Alpha");
    jobOrder.setRequestingSquadron(alpha);
    jobOrder.setCreatingSquadron(alpha);
    jobOrder.setHandle("Tester");
    jobOrder.setPriority(1);

    JobOrderMaterial jom = new JobOrderMaterial();
    jom.setId(UUID.randomUUID());
    jom.setMaterial(material);
    jom.setMinQuality(100);
    jom.setAmount(50.0);
    jobOrder.addMaterial(jom);

    JobOrderMaterialDto jomDto =
        new JobOrderMaterialDto(jom.getId(), materialDto, 100, 50.0, null, 1L);
    baseJobOrderDto =
        new JobOrderDto(
            orderId,
            1,
            "Alpha",
            null,
            null,
            "Tester",
            1,
            JobOrderStatus.OPEN,
            List.of(jomDto),
            List.of(),
            List.of(),
            Instant.now(),
            1L);
  }

  @Test
  void createJobOrder_ShouldCalculateStockAndReturnDto() {
    // Given
    CreateJobOrderMaterialDto createMat = new CreateJobOrderMaterialDto(materialId, 700, 50.0);
    CreateJobOrderDto createDto =
        new CreateJobOrderDto("Alpha", null, null, "Tester", List.of(createMat), null);

    when(jobOrderRepository.lockAllJobOrders()).thenReturn(new ArrayList<>());
    when(jobOrderRepository.findMaxPriority()).thenReturn(Optional.of(0));
    when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));
    when(jobOrderRepository.save(any(JobOrder.class)))
        .thenAnswer(
            i -> {
              JobOrder saved = i.getArgument(0);
              saved.setId(orderId);
              return saved;
            });
    when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);
    when(inventoryItemRepository.sumAmountByMaterialAndJobOrderAndMinQuality(any(), any(), any()))
        .thenReturn(25.0);

    // When
    JobOrderDto result = jobOrderService.createJobOrder(createDto);

    // Then
    assertNotNull(result);
    assertEquals(orderId, result.id());
    assertEquals("Alpha", result.squadron());
    assertEquals(1, result.priority());
    assertEquals(1, result.materials().size());
    assertEquals(25L, result.materials().get(0).currentStock());

    verify(jobOrderRepository, times(2)).lockAllJobOrders();
    verify(jobOrderRepository).findMaxPriority();
    verify(jobOrderRepository).save(any(JobOrder.class));
  }

  @Test
  void createJobOrder_ShouldAlwaysSetMinQualityTo700() {
    // Given — DTO carries 700 (the only valid value), service must persist exactly 700
    CreateJobOrderMaterialDto createMat = new CreateJobOrderMaterialDto(materialId, 700, 10.0);
    CreateJobOrderDto createDto =
        new CreateJobOrderDto("Alpha", null, null, "Tester", List.of(createMat), null);

    when(jobOrderRepository.lockAllJobOrders()).thenReturn(new ArrayList<>());
    when(jobOrderRepository.findMaxPriority()).thenReturn(Optional.of(0));
    when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));
    when(jobOrderRepository.save(any(JobOrder.class)))
        .thenAnswer(
            i -> {
              JobOrder saved = i.getArgument(0);
              saved.setId(orderId);
              return saved;
            });
    when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);
    when(inventoryItemRepository.sumAmountByMaterialAndJobOrderAndMinQuality(any(), any(), any()))
        .thenReturn(0.0);

    // When
    jobOrderService.createJobOrder(createDto);

    // Then — the saved JobOrder must have minQuality == 700 on every material
    verify(jobOrderRepository)
        .save(argThat(jo -> jo.getMaterials().stream().allMatch(m -> m.getMinQuality() == 700)));
  }

  @Test
  void createJobOrder_MaterialNotFound_ShouldThrowException() {
    // Given
    CreateJobOrderMaterialDto createMat = new CreateJobOrderMaterialDto(materialId, 700, 50.0);
    CreateJobOrderDto createDto =
        new CreateJobOrderDto("Alpha", null, null, "Tester", List.of(createMat), null);

    when(jobOrderRepository.findMaxPriority()).thenReturn(Optional.of(0));
    when(materialRepository.findById(materialId)).thenReturn(Optional.empty());

    // When/Then
    assertThrows(NotFoundException.class, () -> jobOrderService.createJobOrder(createDto));
    verify(jobOrderRepository, never()).save(any(JobOrder.class));
  }

  @Test
  void updateJobOrderPriority_ShouldReorderAndNormalize() {
    // Given
    JobOrder otherJob = new JobOrder();
    otherJob.setId(UUID.randomUUID());
    otherJob.setPriority(2);

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(jobOrderRepository.lockAllJobOrders())
        .thenReturn(new ArrayList<>(List.of(jobOrder, otherJob)));
    when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);
    when(inventoryItemRepository.sumAmountByMaterialAndJobOrderAndMinQuality(
            any(UUID.class), any(UUID.class), any()))
        .thenReturn(10.0);

    // When
    JobOrderDto result = jobOrderService.updateJobOrderPriority(orderId, 2);

    // Then
    assertEquals(2, jobOrder.getPriority());
    assertEquals(1, otherJob.getPriority());
    assertNotNull(result);
  }

  @Test
  void updateJobOrderStatus_ToCompleted_ShouldRemovePriorityAndNormalize() {
    // Given
    jobOrder.setPriority(3);
    jobOrder.setStatus(JobOrderStatus.IN_PROGRESS);
    jobOrder.setVersion(1L);
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(jobOrderRepository.save(any(JobOrder.class))).thenReturn(jobOrder);
    when(jobOrderRepository.lockAllJobOrders()).thenReturn(new ArrayList<>(List.of(jobOrder)));
    when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);
    when(inventoryItemRepository.sumAmountByMaterialAndJobOrderAndMinQuality(
            any(UUID.class), any(UUID.class), any()))
        .thenReturn(10.0);

    // When
    JobOrderDto result =
        jobOrderService.updateJobOrderStatus(
            orderId, new UpdateJobOrderStatusDto(JobOrderStatus.COMPLETED, 1L));

    // Then
    assertNull(jobOrder.getPriority());
    assertEquals(JobOrderStatus.COMPLETED, jobOrder.getStatus());
    assertNotNull(result);
    verify(jobOrderRepository).lockAllJobOrders();
    verify(inventoryItemRepository).unlinkJobOrder(orderId);
  }

  @Test
  void updateJobOrderStatus_ToRejected_ShouldRemovePriorityAndNormalizeAndUnlink() {
    // Given
    jobOrder.setPriority(3);
    jobOrder.setStatus(JobOrderStatus.IN_PROGRESS);
    jobOrder.setVersion(1L);
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(jobOrderRepository.save(any(JobOrder.class))).thenReturn(jobOrder);
    when(jobOrderRepository.lockAllJobOrders()).thenReturn(new ArrayList<>(List.of(jobOrder)));
    when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);
    when(inventoryItemRepository.sumAmountByMaterialAndJobOrderAndMinQuality(
            any(UUID.class), any(UUID.class), any()))
        .thenReturn(10.0);

    // When
    JobOrderDto result =
        jobOrderService.updateJobOrderStatus(
            orderId, new UpdateJobOrderStatusDto(JobOrderStatus.REJECTED, 1L));

    // Then
    assertNull(jobOrder.getPriority());
    assertEquals(JobOrderStatus.REJECTED, jobOrder.getStatus());
    assertNotNull(result);
    verify(jobOrderRepository).lockAllJobOrders();
    verify(inventoryItemRepository).unlinkJobOrder(orderId);
  }

  @Test
  void updateJobOrderStatus_ToInProgress_ShouldNotUnlink() {
    // Given
    jobOrder.setPriority(2);
    jobOrder.setStatus(JobOrderStatus.OPEN);
    jobOrder.setVersion(1L);
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(jobOrderRepository.save(any(JobOrder.class))).thenReturn(jobOrder);
    when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);
    when(inventoryItemRepository.sumAmountByMaterialAndJobOrderAndMinQuality(
            any(UUID.class), any(UUID.class), any()))
        .thenReturn(10.0);

    // When
    JobOrderDto result =
        jobOrderService.updateJobOrderStatus(
            orderId, new UpdateJobOrderStatusDto(JobOrderStatus.IN_PROGRESS, 1L));

    // Then
    assertEquals(JobOrderStatus.IN_PROGRESS, jobOrder.getStatus());
    assertNotNull(result);
    verify(inventoryItemRepository, never()).unlinkJobOrder(any());
  }

  @Test
  void updateJobOrderStatus_VersionMismatch_ShouldThrow409() {
    // Given
    jobOrder.setVersion(5L);
    jobOrder.setStatus(JobOrderStatus.OPEN);
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));

    // When / Then
    assertThrows(
        org.springframework.orm.ObjectOptimisticLockingFailureException.class,
        () ->
            jobOrderService.updateJobOrderStatus(
                orderId, new UpdateJobOrderStatusDto(JobOrderStatus.COMPLETED, 1L)));
    verify(jobOrderRepository, never()).save(any());
    verify(inventoryItemRepository, never()).unlinkJobOrder(any());
  }

  @Test
  void updateJobOrderStatus_ToActive_FromCompleted_ShouldAssignNewPriority() {
    // Given
    jobOrder.setPriority(null);
    jobOrder.setStatus(JobOrderStatus.COMPLETED);
    jobOrder.setVersion(2L);
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(jobOrderRepository.findMaxPriority()).thenReturn(Optional.of(5));
    when(jobOrderRepository.save(any(JobOrder.class))).thenReturn(jobOrder);
    when(jobOrderRepository.lockAllJobOrders()).thenReturn(new ArrayList<>(List.of(jobOrder)));
    when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);
    when(inventoryItemRepository.sumAmountByMaterialAndJobOrderAndMinQuality(
            any(UUID.class), any(UUID.class), any()))
        .thenReturn(10.0);

    // When
    JobOrderDto result =
        jobOrderService.updateJobOrderStatus(
            orderId, new UpdateJobOrderStatusDto(JobOrderStatus.OPEN, 2L));

    // Then
    assertEquals(1, jobOrder.getPriority());
    assertEquals(JobOrderStatus.OPEN, jobOrder.getStatus());
    assertNotNull(result);
  }

  @Test
  void updateJobOrderPriority_CompletedJobOrder_ShouldThrowException() {
    // Given
    jobOrder.setPriority(null);
    jobOrder.setStatus(JobOrderStatus.COMPLETED);
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));

    // When/Then
    assertThrows(
        BadRequestException.class,
        () -> {
          jobOrderService.updateJobOrderPriority(orderId, 2);
        });
    verify(jobOrderRepository, never()).save(any(JobOrder.class));
  }

  @Test
  void deleteJobOrder_ShouldLockAndNormalize() {
    // Given
    jobOrder.setPriority(3);
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(jobOrderRepository.lockAllJobOrders()).thenReturn(new ArrayList<>(List.of(jobOrder)));

    // When
    jobOrderService.deleteJobOrder(orderId);

    // Then
    verify(jobOrderRepository, times(2)).lockAllJobOrders();
    verify(jobOrderRepository).delete(jobOrder);
    verify(inventoryItemRepository).unlinkJobOrder(orderId);
  }

  @Test
  void updateJobOrder_OptimisticLockingFailure_ShouldThrowException() {
    // Given
    jobOrder.setVersion(2L);
    CreateJobOrderMaterialDto updateMat = new CreateJobOrderMaterialDto(materialId, 700, 50.0);
    CreateJobOrderDto updateDto =
        new CreateJobOrderDto(
            "Alpha", null, null, "Tester", List.of(updateMat), 1L); // version mismatch

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));

    // When/Then
    assertThrows(
        org.springframework.orm.ObjectOptimisticLockingFailureException.class,
        () -> {
          jobOrderService.updateJobOrder(orderId, updateDto);
        });
    verify(jobOrderRepository, never()).save(any(JobOrder.class));
  }

  @Test
  void updateJobOrder_ShouldNotModifyCreatingSquadron_WhenRequestingSquadronChanges() {
    // Plan §8 + §11: creatingSquadron is immutable after the order is persisted; only
    // requestingSquadron may be retargeted by a Logistician+ during the order's lifetime.
    // This pins the contract that updateJobOrder NEVER touches creatingSquadron even when
    // the inbound DTO carries a different squadron string (which only retargets requesting).
    Squadron creatingOriginal = new Squadron();
    creatingOriginal.setId(UUID.randomUUID());
    creatingOriginal.setShorthand("ALF");
    jobOrder.setCreatingSquadron(creatingOriginal);

    Squadron requestingOriginal = new Squadron();
    requestingOriginal.setId(UUID.randomUUID());
    requestingOriginal.setShorthand("ALF");
    jobOrder.setRequestingSquadron(requestingOriginal);

    CreateJobOrderMaterialDto updateMat = new CreateJobOrderMaterialDto(materialId, 700, 50.0);
    // The DTO carries the squadron STRING that gets looked up against shorthand and turns
    // into requestingSquadron — "Bravo" is intentionally different from creatingOriginal/
    // requestingOriginal's "ALF".
    CreateJobOrderDto updateDto =
        new CreateJobOrderDto("Bravo", null, null, "Tester", List.of(updateMat), null);

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));
    when(jobOrderRepository.save(any(JobOrder.class))).thenReturn(jobOrder);
    when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);

    jobOrderService.updateJobOrder(orderId, updateDto);

    // The creating squadron must be the SAME reference (and same id) as before the update.
    assertSame(
        creatingOriginal,
        jobOrder.getCreatingSquadron(),
        "creatingSquadron is immutable per Plan §8 — update must not retag the authoring"
            + " squadron.");
    // The requesting squadron must reflect the new "Bravo" shorthand.
    assertNotNull(jobOrder.getRequestingSquadron());
    assertEquals("Bravo", jobOrder.getRequestingSquadron().getShorthand());
    // V88 removed the legacy `squadron` VARCHAR mirror from the entity; the wire-shape
    // assertion against the DTO `squadron` field lives in JobOrderMapperTest now, sourced
    // from requestingSquadron.shorthand via @Mapping.
  }

  @Test
  void updateJobOrder_ShouldReject_WhenCreatingSquadronIdChanges() {
    // Plan §8 + §11: passing a different creatingSquadronId on update must reject with 400
    // rather than silently dropping the field. Pins the strict-reject contract that the prior
    // "silently ignore" implementation violated.
    Squadron creatingOriginal = new Squadron();
    creatingOriginal.setId(UUID.randomUUID());
    creatingOriginal.setShorthand("ALF");
    jobOrder.setCreatingSquadron(creatingOriginal);

    UUID attemptedOverride = UUID.randomUUID();
    CreateJobOrderMaterialDto updateMat = new CreateJobOrderMaterialDto(materialId, 700, 50.0);
    CreateJobOrderDto updateDto =
        new CreateJobOrderDto(null, attemptedOverride, null, "Tester", List.of(updateMat), null);

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));

    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> jobOrderService.updateJobOrder(orderId, updateDto));
    assertTrue(
        ex.getMessage().contains("creatingSquadronId"),
        "Error message should reference creatingSquadronId: " + ex.getMessage());
    verify(jobOrderRepository, never()).save(any(JobOrder.class));
  }

  @Test
  void updateJobOrder_ShouldAccept_WhenCreatingSquadronIdMatchesExisting() {
    // Companion to updateJobOrder_ShouldReject_WhenCreatingSquadronIdChanges: passing the SAME
    // creatingSquadronId (i.e. echoing the unchanged value back from a read DTO) is a no-op
    // and must NOT trip the immutability guard.
    Squadron creatingOriginal = new Squadron();
    UUID creatingId = UUID.randomUUID();
    creatingOriginal.setId(creatingId);
    creatingOriginal.setShorthand("ALF");
    jobOrder.setCreatingSquadron(creatingOriginal);

    Squadron requestingOriginal = new Squadron();
    requestingOriginal.setId(UUID.randomUUID());
    requestingOriginal.setShorthand("ALF");
    jobOrder.setRequestingSquadron(requestingOriginal);

    CreateJobOrderMaterialDto updateMat = new CreateJobOrderMaterialDto(materialId, 700, 50.0);
    CreateJobOrderDto updateDto =
        new CreateJobOrderDto("ALF", creatingId, null, "Tester", List.of(updateMat), null);

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));
    when(jobOrderRepository.save(any(JobOrder.class))).thenReturn(jobOrder);
    when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);

    assertDoesNotThrow(() -> jobOrderService.updateJobOrder(orderId, updateDto));
    assertSame(creatingOriginal, jobOrder.getCreatingSquadron());
  }

  @Test
  void updateJobOrder_ShouldUpdateFieldsAndUnlinkRemovedMaterials() {
    // Given
    UUID newMaterialId = UUID.randomUUID();
    Material newMaterial = new Material();
    newMaterial.setId(newMaterialId);

    CreateJobOrderMaterialDto updateMat = new CreateJobOrderMaterialDto(newMaterialId, 700, 50.0);
    CreateJobOrderDto updateDto =
        new CreateJobOrderDto("Beta", null, null, "NewTester", List.of(updateMat), null);

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(materialRepository.findById(newMaterialId)).thenReturn(Optional.of(newMaterial));
    when(jobOrderRepository.save(any(JobOrder.class))).thenReturn(jobOrder);
    when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);

    // When
    jobOrderService.updateJobOrder(orderId, updateDto);

    // Then — requesting squadron updated (legacy `squadron` VARCHAR mirror is gone post-V88).
    assertNotNull(jobOrder.getRequestingSquadron());
    assertEquals("Beta", jobOrder.getRequestingSquadron().getShorthand());
    assertEquals("NewTester", jobOrder.getHandle());

    // Check if the old material was unlinked
    verify(inventoryItemRepository).unlinkJobOrderMaterial(orderId, materialId);

    // Verify save
    verify(jobOrderRepository).save(jobOrder);
  }

  @Test
  void
      completeJobOrderWithinTransaction_ShouldFlushBeforeLockQuery_ToAvoidOptimisticLockConflict() {
    // Given — reproduces the root cause of the 409 bug:
    // completeJobOrderWithinTransaction() modifies jobOrder in-memory (status, priority),
    // then calls normalizePriorities() which issues a PESSIMISTIC_WRITE lock query via
    // lockAllJobOrders(). Without a flush() before that query, the DB still holds the old
    // @Version value while Hibernate has already incremented it in-memory, causing an
    // ObjectOptimisticLockingFailureException on the final transaction flush.
    // Fix: jobOrderRepository.flush() is called before normalizePriorities().
    jobOrder.setStatus(JobOrderStatus.OPEN);
    jobOrder.setPriority(1);
    when(jobOrderRepository.lockAllJobOrders()).thenReturn(new ArrayList<>(List.of(jobOrder)));

    // When — must not throw any exception
    assertDoesNotThrow(() -> jobOrderService.completeJobOrderWithinTransaction(jobOrder));

    // Then — flush() must be called BEFORE lockAllJobOrders() to sync the @Version to DB
    var inOrder = inOrder(jobOrderRepository);
    inOrder.verify(jobOrderRepository).flush();
    inOrder.verify(jobOrderRepository).lockAllJobOrders();

    assertEquals(JobOrderStatus.COMPLETED, jobOrder.getStatus());
    assertNull(jobOrder.getPriority());
    verify(inventoryItemRepository).unlinkJobOrder(orderId);
  }

  @Test
  void completeJobOrderWithinTransaction_ShouldNotNormalize_WhenAlreadyTerminal() {
    // Given — if the order is already COMPLETED, normalizePriorities() must NOT be called
    jobOrder.setStatus(JobOrderStatus.COMPLETED);
    jobOrder.setPriority(null);

    // When
    assertDoesNotThrow(() -> jobOrderService.completeJobOrderWithinTransaction(jobOrder));

    // Then — no flush, no lock query, no unlink since wasTerminal=true
    verify(jobOrderRepository, never()).flush();
    verify(jobOrderRepository, never()).lockAllJobOrders();
    verify(inventoryItemRepository, never()).unlinkJobOrder(any());
  }

  @Test
  void getInventoryItemsForJobOrderMaterial_ShouldReturnMappedDtos() {
    // Given
    de.greluc.krt.iri.basetool.backend.model.InventoryItem item =
        new de.greluc.krt.iri.basetool.backend.model.InventoryItem();
    item.setId(UUID.randomUUID());
    item.setAmount(10.0);

    InventoryItemDto itemDto =
        new InventoryItemDto(
            item.getId(),
            null,
            null,
            null,
            100,
            10.0,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            1L);

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));
    when(inventoryItemRepository.findByJobOrderIdAndMaterialId(orderId, materialId))
        .thenReturn(List.of(item));
    when(inventoryItemMapper.toDto(item)).thenReturn(itemDto);

    // When
    List<InventoryItemDto> result =
        jobOrderService.getInventoryItemsForJobOrderMaterial(orderId, materialId);

    // Then
    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(itemDto.id(), result.get(0).id());
    verify(jobOrderRepository).findById(orderId);
    verify(materialRepository).findById(materialId);
    verify(inventoryItemRepository).findByJobOrderIdAndMaterialId(orderId, materialId);
    verify(inventoryItemMapper).toDto(item);
  }

  @Test
  void unlinkMaterial_ShouldCallUnlinkAndRemoveMaterialFromJobOrder() {
    // Given
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(jobOrderRepository.save(any(JobOrder.class))).thenReturn(jobOrder);

    // When
    jobOrderService.unlinkMaterial(orderId, materialId);

    // Then
    verify(inventoryItemRepository).unlinkJobOrderMaterial(orderId, materialId);
    verify(jobOrderRepository).save(jobOrder);
    assertTrue(
        jobOrder.getMaterials().isEmpty(), "Material should have been removed from job order");
  }

  @Test
  void unlinkMaterial_WhenJobOrderNotFound_ShouldThrowNotFound() {
    // Given
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.empty());

    // When / Then
    NotFoundException ex =
        assertThrows(
            NotFoundException.class, () -> jobOrderService.unlinkMaterial(orderId, materialId));
    verify(inventoryItemRepository, never()).unlinkJobOrderMaterial(any(), any());
  }

  @Test
  void unlinkMaterial_WhenMaterialNotLinked_ShouldThrowNotFound() {
    // Given
    UUID otherMaterialId = UUID.randomUUID();
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));

    // When / Then
    NotFoundException ex =
        assertThrows(
            NotFoundException.class,
            () -> jobOrderService.unlinkMaterial(orderId, otherMaterialId));
    verify(inventoryItemRepository, never()).unlinkJobOrderMaterial(any(), any());
  }

  @Test
  void unlinkInventoryItem_ShouldSetJobOrderToNull() {
    // Given
    UUID inventoryItemId = UUID.randomUUID();
    de.greluc.krt.iri.basetool.backend.model.InventoryItem item =
        new de.greluc.krt.iri.basetool.backend.model.InventoryItem();
    item.setId(inventoryItemId);
    item.setJobOrder(jobOrder);

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(inventoryItemRepository.findById(inventoryItemId)).thenReturn(Optional.of(item));

    // When
    jobOrderService.unlinkInventoryItem(orderId, inventoryItemId);

    // Then
    assertNull(
        ((de.greluc.krt.iri.basetool.backend.model.InventoryItem) item).getJobOrder(),
        "JobOrder should be null after unlinking");
    verify(jobOrderRepository).findById(orderId);
    verify(inventoryItemRepository).findById(inventoryItemId);
  }

  @Test
  void unlinkInventoryItem_WhenJobOrderNotFound_ShouldThrowNotFound() {
    // Given
    UUID inventoryItemId = UUID.randomUUID();
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.empty());

    // When / Then
    NotFoundException ex =
        assertThrows(
            NotFoundException.class,
            () -> jobOrderService.unlinkInventoryItem(orderId, inventoryItemId));
    verify(inventoryItemRepository, never()).findById(any());
  }

  @Test
  void unlinkInventoryItem_WhenInventoryItemNotFound_ShouldThrowNotFound() {
    // Given
    UUID inventoryItemId = UUID.randomUUID();
    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(inventoryItemRepository.findById(inventoryItemId)).thenReturn(Optional.empty());

    // When / Then
    NotFoundException ex =
        assertThrows(
            NotFoundException.class,
            () -> jobOrderService.unlinkInventoryItem(orderId, inventoryItemId));
  }

  @Test
  void unlinkInventoryItem_WhenItemNotLinkedToOrder_ShouldThrowNotFound() {
    // Given
    UUID inventoryItemId = UUID.randomUUID();
    UUID otherOrderId = UUID.randomUUID();
    JobOrder otherOrder = new JobOrder();
    otherOrder.setId(otherOrderId);

    de.greluc.krt.iri.basetool.backend.model.InventoryItem item =
        new de.greluc.krt.iri.basetool.backend.model.InventoryItem();
    item.setId(inventoryItemId);
    item.setJobOrder(otherOrder);

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(inventoryItemRepository.findById(inventoryItemId)).thenReturn(Optional.of(item));

    // When / Then
    NotFoundException ex =
        assertThrows(
            NotFoundException.class,
            () -> jobOrderService.unlinkInventoryItem(orderId, inventoryItemId));
  }

  @Test
  void
      getInventoryItemsForJobOrderMaterial_ShouldReturnItemsSortedByOwnerAscQualityDescLocationAscAmountDesc() {
    // Given
    de.greluc.krt.iri.basetool.backend.model.InventoryItem i1 =
        new de.greluc.krt.iri.basetool.backend.model.InventoryItem();
    i1.setId(UUID.randomUUID());
    de.greluc.krt.iri.basetool.backend.model.InventoryItem i2 =
        new de.greluc.krt.iri.basetool.backend.model.InventoryItem();
    i2.setId(UUID.randomUUID());
    de.greluc.krt.iri.basetool.backend.model.InventoryItem i3 =
        new de.greluc.krt.iri.basetool.backend.model.InventoryItem();
    i3.setId(UUID.randomUUID());
    de.greluc.krt.iri.basetool.backend.model.InventoryItem i4 =
        new de.greluc.krt.iri.basetool.backend.model.InventoryItem();
    i4.setId(UUID.randomUUID());

    UserReferenceDto userAlpha =
        new UserReferenceDto(UUID.randomUUID(), "alpha", "Alpha", "Alpha", 1);
    UserReferenceDto userBeta = new UserReferenceDto(UUID.randomUUID(), "beta", "Beta", "Beta", 2);
    LocationReferenceDto locA = new LocationReferenceDto(UUID.randomUUID(), "ArcCorp");
    LocationReferenceDto locB = new LocationReferenceDto(UUID.randomUUID(), "Baijini");

    // Same owner "Alpha", same quality 80, different location → ArcCorp before Baijini
    InventoryItemDto dto1 =
        new InventoryItemDto(
            i1.getId(),
            userAlpha,
            null,
            locB,
            80,
            5.0,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            1L);
    // Same owner "Alpha", higher quality 90 → comes before quality 80
    InventoryItemDto dto2 =
        new InventoryItemDto(
            i2.getId(),
            userAlpha,
            null,
            locA,
            90,
            3.0,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            1L);
    // Owner "Beta" → after all "Alpha" entries
    InventoryItemDto dto3 =
        new InventoryItemDto(
            i3.getId(),
            userBeta,
            null,
            locA,
            70,
            20.0,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            1L);
    // Same owner "Alpha", same quality 80, same location ArcCorp, higher amount → comes before
    // lower amount
    InventoryItemDto dto4 =
        new InventoryItemDto(
            i4.getId(),
            userAlpha,
            null,
            locA,
            80,
            10.0,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            1L);

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));
    when(inventoryItemRepository.findByJobOrderIdAndMaterialId(orderId, materialId))
        .thenReturn(List.of(i1, i2, i3, i4));
    when(inventoryItemMapper.toDto(i1)).thenReturn(dto1);
    when(inventoryItemMapper.toDto(i2)).thenReturn(dto2);
    when(inventoryItemMapper.toDto(i3)).thenReturn(dto3);
    when(inventoryItemMapper.toDto(i4)).thenReturn(dto4);

    // When
    List<InventoryItemDto> result =
        jobOrderService.getInventoryItemsForJobOrderMaterial(orderId, materialId);

    // Then
    // Expected order: dto2 (Alpha, q90, ArcCorp, 3), dto4 (Alpha, q80, ArcCorp, 10), dto1 (Alpha,
    // q80, Baijini, 5), dto3 (Beta, q70, ArcCorp, 20)
    assertNotNull(result);
    assertEquals(4, result.size());
    assertEquals(dto2.id(), result.get(0).id(), "1st: Alpha, quality 90, ArcCorp");
    assertEquals(dto4.id(), result.get(1).id(), "2nd: Alpha, quality 80, ArcCorp, amount 10");
    assertEquals(dto1.id(), result.get(2).id(), "3rd: Alpha, quality 80, Baijini, amount 5");
    assertEquals(dto3.id(), result.get(3).id(), "4th: Beta, quality 70, ArcCorp");
  }
}

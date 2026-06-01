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
import de.greluc.krt.iri.basetool.backend.model.JobOrderType;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.SpecialCommand;
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
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitRepository;
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

  @Mock private OrgUnitRepository orgUnitRepository;

  @Mock private SystemSettingService systemSettingService;

  @Mock private AuthHelperService authHelperService;

  @Mock private JobOrderMapper jobOrderMapper;

  @Mock private de.greluc.krt.iri.basetool.backend.mapper.InventoryItemMapper inventoryItemMapper;

  @Mock private MaterialClaimService materialClaimService;

  @Mock private JobOrderItemService jobOrderItemService;

  @Mock
  private de.greluc.krt.iri.basetool.backend.mapper.JobOrderItemHandoverMapper
      jobOrderItemHandoverMapper;

  @InjectMocks private JobOrderService jobOrderService;

  private Material material;
  private MaterialDto materialDto;
  private JobOrder jobOrder;
  private JobOrderDto baseJobOrderDto;
  private UUID orderId;
  private UUID materialId;
  private UUID responsibleOrgUnitId;
  private UUID requestingOrgUnitId;

  @BeforeEach
  void setUp() {
    orderId = UUID.randomUUID();
    materialId = UUID.randomUUID();

    // Phase 2 org-unit stamping: createJobOrder resolves a profit-eligible responsible org unit and
    // a requesting org unit via OrgUnitRepository. Lenient defaults cover the authenticated happy
    // path — guest-path and error-path tests override isAuthenticated / the repo stubs as needed.
    responsibleOrgUnitId = UUID.randomUUID();
    requestingOrgUnitId = UUID.randomUUID();
    Squadron responsible = new Squadron();
    responsible.setId(responsibleOrgUnitId);
    responsible.setShorthand("RESP");
    responsible.setProfitEligible(true);
    Squadron requesting = new Squadron();
    requesting.setId(requestingOrgUnitId);
    requesting.setShorthand("Alpha");
    org.mockito.Mockito.lenient().when(authHelperService.isAuthenticated()).thenReturn(true);
    org.mockito.Mockito.lenient()
        .when(orgUnitRepository.findById(responsibleOrgUnitId))
        .thenReturn(java.util.Optional.of(responsible));
    org.mockito.Mockito.lenient()
        .when(orgUnitRepository.findById(requestingOrgUnitId))
        .thenReturn(java.util.Optional.of(requesting));

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
            false,
            true,
            0L);

    jobOrder = new JobOrder();
    jobOrder.setId(orderId);
    // Fixtures stamp both org-unit refs explicitly (the responsible governs visibility from Phase 3
    // on; the requesting is the customer).
    Squadron alpha = new Squadron();
    alpha.setShorthand("Alpha");
    jobOrder.setRequestingOrgUnit(alpha);
    jobOrder.setResponsibleOrgUnit(alpha);
    jobOrder.setHandle("Tester");
    jobOrder.setPriority(1);

    JobOrderMaterial jom = new JobOrderMaterial();
    jom.setId(UUID.randomUUID());
    jom.setMaterial(material);
    jom.setMinQuality(100);
    jom.setAmount(50.0);
    jobOrder.addMaterial(jom);

    JobOrderMaterialDto jomDto =
        new JobOrderMaterialDto(
            jom.getId(), materialDto, 100, 50.0, null, java.util.List.of(), null, 1L);
    baseJobOrderDto =
        new JobOrderDto(
            orderId,
            1,
            null,
            null,
            "Tester",
            null,
            1,
            JobOrderStatus.OPEN,
            JobOrderType.MATERIAL,
            List.of(jomDto),
            List.of(),
            List.of(),
            List.of(),
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
        new CreateJobOrderDto(
            responsibleOrgUnitId, requestingOrgUnitId, "Tester", null, List.of(createMat), null);

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
    assertEquals(1, result.priority());
    assertEquals(1, result.materials().size());
    assertEquals(25L, result.materials().get(0).currentStock());

    verify(jobOrderRepository, times(2)).lockAllJobOrders();
    verify(jobOrderRepository).findMaxPriority();
    verify(jobOrderRepository).save(any(JobOrder.class));
  }

  @Test
  void createJobOrder_ShouldHonorMinQualityFromDto() {
    // Given — DTO carries 700 (the predefined value); the service must persist it verbatim (700),
    // not force a default.
    CreateJobOrderMaterialDto createMat = new CreateJobOrderMaterialDto(materialId, 700, 10.0);
    CreateJobOrderDto createDto =
        new CreateJobOrderDto(
            responsibleOrgUnitId, requestingOrgUnitId, "Tester", null, List.of(createMat), null);

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

    // Then — the saved JobOrder must carry minQuality == 700 (honored, not forced) on every
    // material.
    verify(jobOrderRepository)
        .save(
            argThat(
                jo ->
                    jo.getMaterials().stream()
                        .allMatch(m -> m.getMinQuality() != null && m.getMinQuality() == 700)));
  }

  @Test
  void createJobOrder_NullMinQuality_PersistsNull() {
    // Given — DTO carries a null minQuality ("Keine"); the service must persist null (no floor),
    // not coerce it to 700 or 0.
    CreateJobOrderMaterialDto createMat = new CreateJobOrderMaterialDto(materialId, null, 10.0);
    CreateJobOrderDto createDto =
        new CreateJobOrderDto(
            responsibleOrgUnitId, requestingOrgUnitId, "Tester", null, List.of(createMat), null);

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

    // Then — every saved material's minQuality must be null. Use == null (not == 700) to avoid an
    // NPE unbox.
    verify(jobOrderRepository)
        .save(argThat(jo -> jo.getMaterials().stream().allMatch(m -> m.getMinQuality() == null)));
  }

  @Test
  void createJobOrder_PersistsComment() {
    // Given
    CreateJobOrderMaterialDto createMat = new CreateJobOrderMaterialDto(materialId, 700, 10.0);

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

    // When — comment with surrounding whitespace must be trimmed before persisting.
    jobOrderService.createJobOrder(
        new CreateJobOrderDto(
            responsibleOrgUnitId,
            requestingOrgUnitId,
            "Tester",
            "  Deliver fast  ",
            List.of(createMat),
            null));

    // Then — trimmed value persisted.
    verify(jobOrderRepository).save(argThat(jo -> "Deliver fast".equals(jo.getComment())));

    // When — a blank comment must normalise to null.
    jobOrderService.createJobOrder(
        new CreateJobOrderDto(
            responsibleOrgUnitId, requestingOrgUnitId, "Tester", "   ", List.of(createMat), null));

    // Then — null comment persisted.
    verify(jobOrderRepository).save(argThat(jo -> jo.getComment() == null));
  }

  @Test
  void createJobOrder_MaterialNotFound_ShouldThrowException() {
    // Given
    CreateJobOrderMaterialDto createMat = new CreateJobOrderMaterialDto(materialId, 700, 50.0);
    CreateJobOrderDto createDto =
        new CreateJobOrderDto(
            responsibleOrgUnitId, requestingOrgUnitId, "Tester", null, List.of(createMat), null);

    when(jobOrderRepository.findMaxPriority()).thenReturn(Optional.of(0));
    when(materialRepository.findById(materialId)).thenReturn(Optional.empty());

    // When/Then
    assertThrows(NotFoundException.class, () -> jobOrderService.createJobOrder(createDto));
    verify(jobOrderRepository, never()).save(any(JobOrder.class));
  }

  @Test
  void createJobOrder_Guest_RoutesResponsibleToIntakeSpecialCommand() {
    // Given — a guest (anonymous) creation. The responsible org unit is forced to the configured
    // intake SK regardless of what the client supplies; the requesting (customer) is honoured.
    UUID intakeId = UUID.randomUUID();
    SpecialCommand intake = new SpecialCommand();
    intake.setId(intakeId);
    intake.setShorthand("INTK");

    when(authHelperService.isAuthenticated()).thenReturn(false);
    when(systemSettingService.getSettingValue("job_order.intake_special_command_id"))
        .thenReturn(Optional.of(intakeId.toString()));
    when(orgUnitRepository.findById(intakeId)).thenReturn(Optional.of(intake));

    CreateJobOrderMaterialDto createMat = new CreateJobOrderMaterialDto(materialId, 700, 5.0);
    CreateJobOrderDto createDto =
        new CreateJobOrderDto(
            UUID.randomUUID(), requestingOrgUnitId, "anon-handle", null, List.of(createMat), null);

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

    // Then — responsible is the intake SK (client id ignored), requesting is honoured.
    verify(jobOrderRepository)
        .save(
            argThat(
                jo ->
                    jo.getResponsibleOrgUnit() == intake
                        && jo.getRequestingOrgUnit().getId().equals(requestingOrgUnitId)));
  }

  @Test
  void createJobOrder_Guest_NoIntakeConfigured_Throws() {
    // Given — a guest creation but no intake SK configured: must reject with 400, never persist.
    when(authHelperService.isAuthenticated()).thenReturn(false);
    when(systemSettingService.getSettingValue("job_order.intake_special_command_id"))
        .thenReturn(Optional.empty());

    CreateJobOrderMaterialDto createMat = new CreateJobOrderMaterialDto(materialId, 700, 5.0);
    CreateJobOrderDto createDto =
        new CreateJobOrderDto(
            null, requestingOrgUnitId, "anon-handle", null, List.of(createMat), null);

    assertThrows(BadRequestException.class, () -> jobOrderService.createJobOrder(createDto));
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
            null, null, "Tester", null, List.of(updateMat), 1L); // version mismatch

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
  void updateJobOrder_RetargetsRequesting_AndIgnoresResponsible() {
    // The regular update path retargets the requesting (customer) org unit but NEVER touches the
    // responsible (processing) org unit — that is changed only via the dedicated reassignment
    // endpoint. Any responsibleOrgUnitId in the update DTO is ignored.
    Squadron responsibleOriginal = new Squadron();
    responsibleOriginal.setId(UUID.randomUUID());
    responsibleOriginal.setShorthand("RESP");
    jobOrder.setResponsibleOrgUnit(responsibleOriginal);

    Squadron requestingOriginal = new Squadron();
    requestingOriginal.setId(UUID.randomUUID());
    requestingOriginal.setShorthand("REQ");
    jobOrder.setRequestingOrgUnit(requestingOriginal);

    UUID bravoId = UUID.randomUUID();
    Squadron bravo = new Squadron();
    bravo.setId(bravoId);
    bravo.setShorthand("Bravo");

    CreateJobOrderMaterialDto updateMat = new CreateJobOrderMaterialDto(materialId, 700, 50.0);
    // A non-null responsibleOrgUnitId is supplied but must be ignored by the update path.
    CreateJobOrderDto updateDto =
        new CreateJobOrderDto(UUID.randomUUID(), bravoId, "Tester", null, List.of(updateMat), null);

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(orgUnitRepository.findById(bravoId)).thenReturn(Optional.of(bravo));
    when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));
    when(jobOrderRepository.save(any(JobOrder.class))).thenReturn(jobOrder);
    when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);

    jobOrderService.updateJobOrder(orderId, updateDto);

    // Responsible unchanged (same reference); requesting flipped to "Bravo".
    assertSame(responsibleOriginal, jobOrder.getResponsibleOrgUnit());
    assertNotNull(jobOrder.getRequestingOrgUnit());
    assertEquals("Bravo", jobOrder.getRequestingOrgUnit().getShorthand());
  }

  @Test
  void reassignResponsibleOrgUnit_Admin_MovesToProfitEligibleTarget() {
    // Admin may reassign freely to any profit-eligible org unit.
    Squadron current = new Squadron();
    current.setId(UUID.randomUUID());
    current.setShorthand("CUR");
    jobOrder.setResponsibleOrgUnit(current);

    UUID targetId = UUID.randomUUID();
    SpecialCommand target = new SpecialCommand();
    target.setId(targetId);
    target.setShorthand("SK");
    target.setProfitEligible(true);

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(orgUnitRepository.findById(targetId)).thenReturn(Optional.of(target));
    when(authHelperService.isAdmin()).thenReturn(true);
    when(jobOrderRepository.save(any(JobOrder.class))).thenReturn(jobOrder);
    when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);
    // The order is now responsible to an SK, so mapToDtoWithStock enriches it with the claim view
    // (Phase 5, #345).
    when(materialClaimService.getClaimBucketsForOrder(any(JobOrder.class)))
        .thenReturn(java.util.List.of());

    jobOrderService.reassignResponsibleOrgUnit(orderId, targetId);

    assertSame(target, jobOrder.getResponsibleOrgUnit());
  }

  @Test
  void reassignResponsibleOrgUnit_RejectsNonProfitEligibleTarget() {
    UUID targetId = UUID.randomUUID();
    Squadron target = new Squadron();
    target.setId(targetId);
    target.setProfitEligible(false);

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(orgUnitRepository.findById(targetId)).thenReturn(Optional.of(target));

    assertThrows(
        BadRequestException.class,
        () -> jobOrderService.reassignResponsibleOrgUnit(orderId, targetId));
    verify(jobOrderRepository, never()).save(any(JobOrder.class));
  }

  @Test
  void updateJobOrder_ShouldUpdateFieldsAndUnlinkRemovedMaterials() {
    // Given
    UUID newMaterialId = UUID.randomUUID();
    Material newMaterial = new Material();
    newMaterial.setId(newMaterialId);

    // Post Phase 7 part 3 / V90 the resolver is UUID-only; pass a typed `requestingSquadronId`
    // and stub the repository to map it to a "Beta" squadron so the assertion below pins the
    // requesting-squadron-flip contract.
    UUID betaId = UUID.randomUUID();
    Squadron beta = new Squadron();
    beta.setId(betaId);
    beta.setShorthand("Beta");

    CreateJobOrderMaterialDto updateMat = new CreateJobOrderMaterialDto(newMaterialId, 700, 50.0);
    CreateJobOrderDto updateDto =
        new CreateJobOrderDto(null, betaId, "NewTester", null, List.of(updateMat), null);

    when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
    when(orgUnitRepository.findById(betaId)).thenReturn(Optional.of(beta));
    when(materialRepository.findById(newMaterialId)).thenReturn(Optional.of(newMaterial));
    when(jobOrderRepository.save(any(JobOrder.class))).thenReturn(jobOrder);
    when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);

    // When
    jobOrderService.updateJobOrder(orderId, updateDto);

    // Then — requesting squadron flipped to the resolved "Beta" target.
    assertNotNull(jobOrder.getRequestingOrgUnit());
    assertEquals("Beta", jobOrder.getRequestingOrgUnit().getShorthand());
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

  // ---------------------------------------------------------------
  // updateItemJobOrder — item-order edit (item lines + metadata)
  // ---------------------------------------------------------------

  @org.junit.jupiter.api.Nested
  class UpdateItemJobOrderTests {

    private de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderItemRequestDto oneLine(
        Long version) {
      return new de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderItemRequestDto(
          null,
          null,
          "edited",
          null,
          List.of(
              new de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderItemLineDto(
                  UUID.randomUUID(), UUID.randomUUID(), 1, List.of(), 1, null)),
          version);
    }

    private JobOrder itemOrder() {
      JobOrder order = new JobOrder();
      order.setId(orderId);
      order.setType(JobOrderType.ITEM);
      order.setStatus(de.greluc.krt.iri.basetool.backend.model.JobOrderStatus.OPEN);
      order.setVersion(1L);
      return order;
    }

    @Test
    void nonItemOrder_throwsBadRequest() {
      JobOrder material = new JobOrder();
      material.setId(orderId);
      material.setType(JobOrderType.MATERIAL);
      when(jobOrderRepository.findById(orderId)).thenReturn(java.util.Optional.of(material));

      assertThrows(
          de.greluc.krt.iri.basetool.backend.exception.BadRequestException.class,
          () -> jobOrderService.updateItemJobOrder(orderId, oneLine(null)));
      verify(jobOrderItemService, never()).buildItemLine(any());
    }

    @Test
    void orderWithItemHandover_throwsBadRequest() {
      JobOrder order = itemOrder();
      order
          .getItemHandovers()
          .add(new de.greluc.krt.iri.basetool.backend.model.JobOrderItemHandover());
      when(jobOrderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order));

      assertThrows(
          de.greluc.krt.iri.basetool.backend.exception.BadRequestException.class,
          () -> jobOrderService.updateItemJobOrder(orderId, oneLine(null)));
      verify(jobOrderItemService, never()).buildItemLine(any());
    }

    @Test
    void versionMismatch_throws409() {
      JobOrder order = itemOrder();
      when(jobOrderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order));

      assertThrows(
          org.springframework.orm.ObjectOptimisticLockingFailureException.class,
          () -> jobOrderService.updateItemJobOrder(orderId, oneLine(99L)));
      verify(jobOrderItemService, never()).buildItemLine(any());
    }

    @Test
    void happyPath_rebuildsLines_wiresSubAssembly_andWithdrawsOrphanClaims() {
      JobOrder order = itemOrder();
      when(jobOrderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order));
      when(jobOrderRepository.save(any(JobOrder.class))).thenAnswer(inv -> inv.getArgument(0));
      when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);
      // mapToDtoWithStock reads the item projections for an ITEM order.
      when(jobOrderItemService.toItemDtos(any())).thenReturn(List.of());
      when(jobOrderItemService.aggregateMaterials(any())).thenReturn(List.of());
      // Each line builds a distinct managed JobOrderItem.
      when(jobOrderItemService.buildItemLine(any()))
          .thenAnswer(inv -> new de.greluc.krt.iri.basetool.backend.model.JobOrderItem());

      // Two lines, the second adopted as a sub-assembly of the first (parentClientLineId = 1).
      de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderItemRequestDto dto =
          new de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderItemRequestDto(
              null,
              null,
              "edited",
              null,
              List.of(
                  new de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderItemLineDto(
                      UUID.randomUUID(), UUID.randomUUID(), 1, List.of(), 1, null),
                  new de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderItemLineDto(
                      UUID.randomUUID(), UUID.randomUUID(), 2, List.of(), 2, 1)),
              1L);

      jobOrderService.updateItemJobOrder(orderId, dto);

      // Both lines were (re-)built and attached, and the orphan-claim reconciliation ran.
      verify(jobOrderItemService, times(2)).buildItemLine(any());
      assertEquals(2, order.getItems().size(), "the two new lines replace the old set");
      java.util.List<de.greluc.krt.iri.basetool.backend.model.JobOrderItem> items =
          new java.util.ArrayList<>(order.getItems());
      assertTrue(
          items.stream().anyMatch(i -> i.getParentItem() != null),
          "the adopted line keeps its sub-assembly parent");
      verify(materialClaimService).withdrawOrphanedClaimsWithinTransaction(order);
      assertEquals("edited", order.getHandle());
    }
  }
}

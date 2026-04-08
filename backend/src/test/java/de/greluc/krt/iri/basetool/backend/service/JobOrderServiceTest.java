package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.mapper.JobOrderMapper;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial;
import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderMaterialDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderMaterialDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialDto;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobOrderServiceTest {

    @Mock
    private JobOrderRepository jobOrderRepository;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private JobOrderMapper jobOrderMapper;

    @InjectMocks
    private JobOrderService jobOrderService;

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

        material = new Material();
        material.setId(materialId);
        material.setName("Gold");

        materialDto = new MaterialDto(materialId, "Gold", "RAW", "SCU", "Some desc", null, null, false, false, false, 0L);

        jobOrder = new JobOrder();
        jobOrder.setId(orderId);
        jobOrder.setSquadron("Alpha");
        jobOrder.setHandle("Tester");
        jobOrder.setPriority(1);

        JobOrderMaterial jom = new JobOrderMaterial();
        jom.setId(UUID.randomUUID());
        jom.setMaterial(material);
        jom.setMinQuality(100);
        jom.setAmount(50.0);
        jobOrder.addMaterial(jom);

        JobOrderMaterialDto jomDto = new JobOrderMaterialDto(jom.getId(), materialDto, 100, 50.0, null, 1L);
        baseJobOrderDto = new JobOrderDto(orderId, 1, "Alpha", "Tester", 1, JobOrderStatus.OPEN, List.of(jomDto), List.of(), Instant.now(), 1L);
    }

    @Test
    void createJobOrder_ShouldCalculateStockAndReturnDto() {
        // Given
        CreateJobOrderMaterialDto createMat = new CreateJobOrderMaterialDto(materialId, 100, 50.0);
        CreateJobOrderDto createDto = new CreateJobOrderDto("Alpha", "Tester", List.of(createMat), null);

        when(jobOrderRepository.lockAllJobOrders()).thenReturn(new ArrayList<>());
        when(jobOrderRepository.findMaxPriority()).thenReturn(Optional.of(0));
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));
        when(jobOrderRepository.save(any(JobOrder.class))).thenAnswer(i -> {
            JobOrder saved = i.getArgument(0);
            saved.setId(orderId);
            return saved;
        });
        when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);
        when(inventoryItemRepository.sumAmountByMaterialAndJobOrderAndMinQuality(eq(materialId), any(UUID.class), eq(100))).thenReturn(25.0);

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
    void createJobOrder_MaterialNotFound_ShouldThrowException() {
        // Given
        CreateJobOrderMaterialDto createMat = new CreateJobOrderMaterialDto(materialId, 100, 50.0);
        CreateJobOrderDto createDto = new CreateJobOrderDto("Alpha", "Tester", List.of(createMat), null);

        when(jobOrderRepository.findMaxPriority()).thenReturn(Optional.of(0));
        when(materialRepository.findById(materialId)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(ResponseStatusException.class, () -> jobOrderService.createJobOrder(createDto));
        verify(jobOrderRepository, never()).save(any(JobOrder.class));
    }

    @Test
    void updateJobOrderPriority_ShouldReorderAndNormalize() {
        // Given
        JobOrder otherJob = new JobOrder();
        otherJob.setId(UUID.randomUUID());
        otherJob.setPriority(2);

        when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
        when(jobOrderRepository.lockAllJobOrders()).thenReturn(new ArrayList<>(List.of(jobOrder, otherJob)));
        when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);
        when(inventoryItemRepository.sumAmountByMaterialAndJobOrderAndMinQuality(any(UUID.class), any(UUID.class), any())).thenReturn(10.0);

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
        when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
        when(jobOrderRepository.save(any(JobOrder.class))).thenReturn(jobOrder);
        when(jobOrderRepository.lockAllJobOrders()).thenReturn(new ArrayList<>(List.of(jobOrder)));
        when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);
        when(inventoryItemRepository.sumAmountByMaterialAndJobOrderAndMinQuality(any(UUID.class), any(UUID.class), any())).thenReturn(10.0);

        // When
        JobOrderDto result = jobOrderService.updateJobOrderStatus(orderId, JobOrderStatus.COMPLETED);

        // Then
        assertNull(jobOrder.getPriority());
        assertEquals(JobOrderStatus.COMPLETED, jobOrder.getStatus());
        assertNotNull(result);
        verify(jobOrderRepository).lockAllJobOrders();
    }

    @Test
    void updateJobOrderStatus_ToActive_FromCompleted_ShouldAssignNewPriority() {
        // Given
        jobOrder.setPriority(null);
        jobOrder.setStatus(JobOrderStatus.COMPLETED);
        when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));
        when(jobOrderRepository.findMaxPriority()).thenReturn(Optional.of(5));
        when(jobOrderRepository.save(any(JobOrder.class))).thenReturn(jobOrder);
        when(jobOrderRepository.lockAllJobOrders()).thenReturn(new ArrayList<>(List.of(jobOrder)));
        when(jobOrderMapper.toDto(any(JobOrder.class))).thenReturn(baseJobOrderDto);
        when(inventoryItemRepository.sumAmountByMaterialAndJobOrderAndMinQuality(any(UUID.class), any(UUID.class), any())).thenReturn(10.0);

        // When
        JobOrderDto result = jobOrderService.updateJobOrderStatus(orderId, JobOrderStatus.OPEN);

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
        assertThrows(ResponseStatusException.class, () -> {
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
        CreateJobOrderMaterialDto updateMat = new CreateJobOrderMaterialDto(materialId, 100, 50.0);
        CreateJobOrderDto updateDto = new CreateJobOrderDto("Alpha", "Tester", List.of(updateMat), 1L); // version mismatch

        when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(jobOrder));

        // When/Then
        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class, () -> {
            jobOrderService.updateJobOrder(orderId, updateDto);
        });
        verify(jobOrderRepository, never()).save(any(JobOrder.class));
    }
}
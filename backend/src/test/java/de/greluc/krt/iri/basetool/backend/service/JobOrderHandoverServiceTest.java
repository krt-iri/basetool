package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.mapper.JobOrderHandoverMapper;
import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderHandover;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverItemCreateDto;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderHandoverRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobOrderHandoverServiceTest {

    @Mock
    private JobOrderRepository jobOrderRepository;
    @Mock
    private JobOrderHandoverRepository jobOrderHandoverRepository;
    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private JobOrderHandoverMapper jobOrderHandoverMapper;

    @InjectMocks
    private JobOrderHandoverService service;

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
        JobOrderHandoverCreateDto createDto = new JobOrderHandoverCreateDto(Instant.now(), "HanSolo", "Rogue", List.of(itemDto));

        when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(inventoryItemRepository.findByIdForUpdate(inventoryId)).thenReturn(Optional.of(inventoryItem));
        when(jobOrderHandoverRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jobOrderHandoverMapper.toDto(any(JobOrderHandover.class))).thenReturn(mock(JobOrderHandoverDto.class));

        // When
        service.createHandover(orderId, createDto);

        // Then
        assertEquals(6.0, inventoryItem.getAmount());
        assertEquals(6.0, jobOrderMaterial.getAmount());
        verify(inventoryItemRepository).save(inventoryItem);
        verify(inventoryItemRepository, never()).delete(any());
        verify(jobOrderHandoverRepository).save(any(JobOrderHandover.class));
    }

    @Test
    void createHandover_shouldDeleteInventoryItem_whenAmountIsFullyHandedOver() {
        // Given
        JobOrderHandoverItemCreateDto itemDto = new JobOrderHandoverItemCreateDto(inventoryId, 10.0);
        JobOrderHandoverCreateDto createDto = new JobOrderHandoverCreateDto(Instant.now(), "HanSolo", null, List.of(itemDto));

        when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(inventoryItemRepository.findByIdForUpdate(inventoryId)).thenReturn(Optional.of(inventoryItem));
        when(jobOrderHandoverRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jobOrderHandoverMapper.toDto(any(JobOrderHandover.class))).thenReturn(mock(JobOrderHandoverDto.class));

        // When
        service.createHandover(orderId, createDto);

        // Then
        assertEquals(0.0, jobOrderMaterial.getAmount());
        verify(inventoryItemRepository).delete(inventoryItem);
        verify(inventoryItemRepository, never()).save(any());
        verify(jobOrderHandoverRepository).save(any(JobOrderHandover.class));
    }

    @Test
    void createHandover_shouldThrowException_whenAmountExceedsStock() {
        // Given
        JobOrderHandoverItemCreateDto itemDto = new JobOrderHandoverItemCreateDto(inventoryId, 11.0);
        JobOrderHandoverCreateDto createDto = new JobOrderHandoverCreateDto(Instant.now(), "HanSolo", null, List.of(itemDto));

        when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(inventoryItemRepository.findByIdForUpdate(inventoryId)).thenReturn(Optional.of(inventoryItem));

        // When & Then
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.createHandover(orderId, createDto));
        assertTrue(ex.getMessage().contains("Cannot hand over more than the available amount"));
    }

    @Test
    void createHandover_shouldThrowException_whenItemDoesNotBelongToOrder() {
        // Given
        JobOrder otherOrder = new JobOrder();
        otherOrder.setId(UUID.randomUUID());
        inventoryItem.setJobOrder(otherOrder);

        JobOrderHandoverItemCreateDto itemDto = new JobOrderHandoverItemCreateDto(inventoryId, 5.0);
        JobOrderHandoverCreateDto createDto = new JobOrderHandoverCreateDto(Instant.now(), "HanSolo", null, List.of(itemDto));

        when(jobOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(inventoryItemRepository.findByIdForUpdate(inventoryId)).thenReturn(Optional.of(inventoryItem));

        // When & Then
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.createHandover(orderId, createDto));
        assertTrue(ex.getMessage().contains("Inventory item does not belong to this JobOrder"));
    }
}

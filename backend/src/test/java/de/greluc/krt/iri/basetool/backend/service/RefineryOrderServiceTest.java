package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderStoreDto;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefineryOrderServiceTest {

    @Mock
    private RefineryOrderRepository refineryOrderRepository;

    @InjectMocks
    private RefineryOrderService refineryOrderService;

    @Test
    void shouldThrowExceptionWhenStoringCompletedOrder() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        
        RefineryOrder completedOrder = new RefineryOrder();
        completedOrder.setId(orderId);
        completedOrder.setStatus(RefineryOrderStatus.COMPLETED);
        
        when(refineryOrderRepository.findById(orderId)).thenReturn(Optional.of(completedOrder));
        
        RefineryOrderStoreDto dto = new RefineryOrderStoreDto(Collections.emptyList());

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> 
            refineryOrderService.storeRefineryOrder(userId, orderId, dto, false)
        );
        
        assertEquals("Refinery order is already completed and stored.", exception.getMessage());
    }
}

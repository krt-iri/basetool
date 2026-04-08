package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Operation;
import de.greluc.krt.iri.basetool.backend.model.OperationStatus;
import de.greluc.krt.iri.basetool.backend.repository.OperationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationServiceTest {

    @Mock
    private OperationRepository operationRepository;

    @InjectMocks
    private OperationService operationService;

    @Test
    void shouldCreateOperation() {
        // Given
        Operation operation = new Operation();
        operation.setName("Test Op");
        operation.setStatus(OperationStatus.PLANNED);

        when(operationRepository.save(any(Operation.class))).thenReturn(operation);

        // When
        Operation result = operationService.createOperation(operation);

        // Then
        assertNotNull(result);
        assertEquals("Test Op", result.getName());
        verify(operationRepository, times(1)).save(operation);
    }

    @Test
    void shouldGetOperationById() {
        // Given
        UUID id = UUID.randomUUID();
        Operation operation = new Operation();
        operation.setId(id);
        when(operationRepository.findById(id)).thenReturn(Optional.of(operation));

        // When
        Operation result = operationService.getOperationById(id);

        // Then
        assertNotNull(result);
        assertEquals(id, result.getId());
    }

    @Test
    void shouldGetAllOperations() {
        // Given
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Operation> page = new PageImpl<>(List.of(new Operation()));
        when(operationRepository.findAll(pageable)).thenReturn(page);

        // When
        Page<Operation> result = operationService.getAllOperations(pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void shouldDeleteOperation() {
        // Given
        UUID id = UUID.randomUUID();
        Operation operation = new Operation();
        when(operationRepository.findById(id)).thenReturn(Optional.of(operation));
        doNothing().when(operationRepository).delete(operation);

        // When
        operationService.deleteOperation(id);

        // Then
        verify(operationRepository, times(1)).delete(operation);
    }
}
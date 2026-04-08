package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.OperationMapper;
import de.greluc.krt.iri.basetool.backend.model.Operation;
import de.greluc.krt.iri.basetool.backend.model.OperationStatus;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationDto;
import de.greluc.krt.iri.basetool.backend.service.OperationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationControllerTest {

    @Mock
    private OperationService operationService;

    @Mock
    private OperationMapper operationMapper;

    @InjectMocks
    private OperationController operationController;

    @Test
    void shouldCreateOperation() {
        // Given
        OperationCreateDto createDto = new OperationCreateDto("Test", "Desc", OperationStatus.PLANNED);
        Operation operation = new Operation();
        OperationDto operationDto = new OperationDto(UUID.randomUUID(), "Test", "Desc", OperationStatus.PLANNED, 0L, null, null);

        when(operationMapper.toEntity(createDto)).thenReturn(operation);
        when(operationService.createOperation(operation)).thenReturn(operation);
        when(operationMapper.toDto(operation)).thenReturn(operationDto);

        // When
        OperationDto result = operationController.createOperation(createDto);

        // Then
        assertNotNull(result);
        assertEquals("Test", result.name());
        verify(operationService, times(1)).createOperation(operation);
    }
}
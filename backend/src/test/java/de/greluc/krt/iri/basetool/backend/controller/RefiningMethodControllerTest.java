package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.RefiningMethodMapper;
import de.greluc.krt.iri.basetool.backend.model.RefiningMethod;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.RefiningMethodDto;
import de.greluc.krt.iri.basetool.backend.service.RefiningMethodService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Pure-method unit tests for {@link RefiningMethodController}. */
@ExtendWith(MockitoExtension.class)
class RefiningMethodControllerTest {

    @Mock private RefiningMethodService service;
    @Mock private RefiningMethodMapper mapper;

    @InjectMocks private RefiningMethodController controller;

    @Test
    void getAll_wrapsServicePageIntoPageResponse() {
        RefiningMethod entity = new RefiningMethod();
        RefiningMethodDto dto = new RefiningMethodDto(UUID.randomUUID(), "Cormack", null, "CORMACK", 95, 70, 30);
        when(service.getAllRefiningMethods(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toDto(entity)).thenReturn(dto);

        PageResponse<RefiningMethodDto> resp = controller.getAllRefiningMethods(null, null, null);

        assertEquals(1, resp.totalElements());
        assertSame(dto, resp.content().getFirst());
    }

    @Test
    void getById_delegatesAndMaps() {
        UUID id = UUID.randomUUID();
        RefiningMethod entity = new RefiningMethod();
        RefiningMethodDto dto = new RefiningMethodDto(id, "x", null, "X", 50, 50, 50);
        when(service.getRefiningMethod(id)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(dto);

        RefiningMethodDto result = controller.getRefiningMethod(id);

        assertSame(dto, result);
    }

    @Test
    void create_roundTripsViaMapper() {
        RefiningMethodDto request = new RefiningMethodDto(null, "Dinyx", null, "DINYX", 60, 60, 60);
        RefiningMethod entity = new RefiningMethod();
        RefiningMethod persisted = new RefiningMethod();
        RefiningMethodDto response = new RefiningMethodDto(UUID.randomUUID(), "Dinyx", null, "DINYX", 60, 60, 60);

        when(mapper.toEntity(request)).thenReturn(entity);
        when(service.createRefiningMethod(entity)).thenReturn(persisted);
        when(mapper.toDto(persisted)).thenReturn(response);

        RefiningMethodDto result = controller.createRefiningMethod(request);

        assertSame(response, result);
        verify(service).createRefiningMethod(entity);
    }

    @Test
    void update_passesIdAndMappedEntityToService() {
        UUID id = UUID.randomUUID();
        RefiningMethodDto request = new RefiningMethodDto(id, "Renamed", null, "REN", 40, 30, 100);
        RefiningMethod entity = new RefiningMethod();
        RefiningMethod updated = new RefiningMethod();
        RefiningMethodDto response = new RefiningMethodDto(id, "Renamed", null, "REN", 40, 30, 100);

        when(mapper.toEntity(request)).thenReturn(entity);
        when(service.updateRefiningMethod(id, entity)).thenReturn(updated);
        when(mapper.toDto(updated)).thenReturn(response);

        RefiningMethodDto result = controller.updateRefiningMethod(id, request);

        assertSame(response, result);
        verify(service).updateRefiningMethod(id, entity);
    }

    @Test
    void delete_delegatesIdToService() {
        UUID id = UUID.randomUUID();

        controller.deleteRefiningMethod(id);

        verify(service).deleteRefiningMethod(id);
        verifyNoMoreInteractions(service, mapper);
    }
}

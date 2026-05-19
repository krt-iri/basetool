package de.greluc.krt.iri.basetool.backend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.mapper.SquadronMapper;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronDto;
import de.greluc.krt.iri.basetool.backend.service.SquadronService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Pure-method unit tests for {@link SquadronController}. The Spring-MVC binding (`@PreAuthorize`,
 * JSON) is covered by integration tests; here we verify the controller's delegation contract and
 * pagination wrapping.
 */
@ExtendWith(MockitoExtension.class)
class SquadronControllerTest {

  @Mock private SquadronService service;
  @Mock private SquadronMapper mapper;

  @InjectMocks private SquadronController controller;

  @Test
  void getAll_wrapsServicePageIntoPageResponseAndMapsContent() {
    // Given
    Squadron entity = new Squadron();
    SquadronDto dto = new SquadronDto(UUID.randomUUID(), "Alpha", "ALP", "Test", true, true, 1L);
    Page<Squadron> servicePage = new PageImpl<>(List.of(entity));
    when(service.getAllSquadrons(any(Pageable.class), eq(false))).thenReturn(servicePage);
    when(mapper.toDto(entity)).thenReturn(dto);

    // When
    PageResponse<SquadronDto> resp = controller.getAllSquadrons(0, 20, null, false);

    // Then
    assertEquals(1, resp.totalElements());
    assertEquals(1, resp.content().size());
    assertSame(dto, resp.content().getFirst());
  }

  @Test
  void getAll_includeInactive_isForwardedToService() {
    when(service.getAllSquadrons(any(Pageable.class), eq(true)))
        .thenReturn(new PageImpl<>(List.of()));

    controller.getAllSquadrons(null, null, null, true);

    // The boolean flag controls whether inactive squadrons appear in the result;
    // mis-routing it would silently hide deleted-but-still-required entries.
    verify(service).getAllSquadrons(any(Pageable.class), eq(true));
  }

  @Test
  void getAll_appliesPaginationParameters() {
    when(service.getAllSquadrons(any(Pageable.class), eq(false)))
        .thenReturn(new PageImpl<>(List.of()));

    controller.getAllSquadrons(3, 75, "name,desc", false);

    ArgumentCaptor<Pageable> pgCap = ArgumentCaptor.forClass(Pageable.class);
    verify(service).getAllSquadrons(pgCap.capture(), eq(false));
    assertEquals(3, pgCap.getValue().getPageNumber());
    assertEquals(75, pgCap.getValue().getPageSize());
  }

  @Test
  void create_roundTripsDtoToEntityViaMapperAndBack() {
    SquadronDto request = new SquadronDto(null, "Bravo", "BRV", "Test", true, true, null);
    Squadron entity = new Squadron();
    Squadron persisted = new Squadron();
    SquadronDto response =
        new SquadronDto(UUID.randomUUID(), "Bravo", "BRV", "Test", true, true, 1L);

    when(mapper.toEntity(request)).thenReturn(entity);
    when(service.createSquadron(entity)).thenReturn(persisted);
    when(mapper.toDto(persisted)).thenReturn(response);

    SquadronDto result = controller.createSquadron(request);

    assertSame(response, result);
    verify(service).createSquadron(entity);
  }

  @Test
  void update_passesIdAndDtoDirectlyToService() {
    // Given — note: SquadronController.updateSquadron forwards the DTO
    // (not a mapped entity) to the service. Documents the actual contract.
    UUID id = UUID.randomUUID();
    SquadronDto request = new SquadronDto(id, "Renamed", "REN", "Test", true, true, 4L);
    Squadron persisted = new Squadron();
    SquadronDto response = new SquadronDto(id, "Renamed", "REN", "Test", true, true, 5L);

    when(service.updateSquadron(id, request)).thenReturn(persisted);
    when(mapper.toDto(persisted)).thenReturn(response);

    SquadronDto result = controller.updateSquadron(id, request);

    assertSame(response, result);
    verify(service).updateSquadron(id, request);
    verify(mapper, never()).toEntity(any(SquadronDto.class));
  }

  @Test
  void delete_delegatesIdToService() {
    UUID id = UUID.randomUUID();

    controller.deleteSquadron(id);

    verify(service).deleteSquadron(id);
    verifyNoMoreInteractions(service);
  }

  @Test
  void activate_delegatesIdToService() {
    UUID id = UUID.randomUUID();

    controller.activateSquadron(id);

    verify(service).activateSquadron(id);
    verifyNoMoreInteractions(service);
  }
}

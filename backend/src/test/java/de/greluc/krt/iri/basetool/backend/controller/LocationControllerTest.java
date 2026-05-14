package de.greluc.krt.iri.basetool.backend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.mapper.LocationMapper;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.dto.LocationDto;
import de.greluc.krt.iri.basetool.backend.model.dto.LocationReferenceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.service.LocationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Pure-method unit tests for {@link LocationController}. Key behaviour documented in the test
 * names:
 *
 * <ul>
 *   <li>The POST create endpoint must pass through {@link
 *       LocationMapper#stripServerManaged(Location)} so a client cannot mass-assign onto an
 *       existing row via {@code id} / {@code version}.
 *   <li>The "lookup" endpoint returns reference DTOs directly from the service — no mapper
 *       involvement.
 *   <li>Pagination wrapping ({@link PageResponse}) honours the {@code includeHidden} flag verbatim.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LocationControllerTest {

  @Mock private LocationService service;
  @Mock private LocationMapper mapper;

  @InjectMocks private LocationController controller;

  @Test
  void getAll_passesIncludeHiddenFlagToService() {
    when(service.getAllLocations(any(Pageable.class), eq(true)))
        .thenReturn(new PageImpl<>(List.of()));

    controller.getAllLocations(null, null, null, true);

    verify(service).getAllLocations(any(Pageable.class), eq(true));
  }

  @Test
  void getAll_defaultsIncludeHiddenToFalse() {
    when(service.getAllLocations(any(Pageable.class), eq(false)))
        .thenReturn(new PageImpl<>(List.of()));

    controller.getAllLocations(null, null, null, false);

    verify(service).getAllLocations(any(Pageable.class), eq(false));
  }

  @Test
  void getAll_wrapsServicePageIntoPageResponseAndMapsContent() {
    Location entity = new Location();
    LocationDto dto = new LocationDto(UUID.randomUUID(), "Lorville", null, false, 1L);
    when(service.getAllLocations(any(Pageable.class), eq(false)))
        .thenReturn(new PageImpl<>(List.of(entity)));
    when(mapper.toDto(entity)).thenReturn(dto);

    PageResponse<LocationDto> resp = controller.getAllLocations(0, 50, null, false);

    assertEquals(1, resp.totalElements());
    assertSame(dto, resp.content().getFirst());
  }

  @Test
  void lookup_returnsReferenceListDirectlyFromService_noMapper() {
    // The lookup endpoint exposes a stripped-down ReferenceDto for
    // dropdowns; the service produces it directly so the mapper is not
    // involved. A refactor that re-routed through the mapper would
    // accidentally expose internal fields.
    List<LocationReferenceDto> refs =
        List.of(
            new LocationReferenceDto(UUID.randomUUID(), "Lorville"),
            new LocationReferenceDto(UUID.randomUUID(), "Area18"));
    when(service.findAllReference()).thenReturn(refs);

    List<LocationReferenceDto> result = controller.lookupLocations();

    assertSame(refs, result);
    verifyNoInteractions(mapper);
  }

  @Test
  void getById_delegatesAndMaps() {
    UUID id = UUID.randomUUID();
    Location entity = new Location();
    LocationDto dto = new LocationDto(id, "x", null, false, 1L);
    when(service.getLocation(id)).thenReturn(entity);
    when(mapper.toDto(entity)).thenReturn(dto);

    LocationDto result = controller.getLocation(id);

    assertSame(dto, result);
  }

  @Test
  void getRefineryLocations_mapsEachEntityToDto() {
    Location loc1 = new Location();
    Location loc2 = new Location();
    LocationDto dto1 = new LocationDto(UUID.randomUUID(), "ARC-L1", null, false, 1L);
    LocationDto dto2 = new LocationDto(UUID.randomUUID(), "CRU-L4", null, false, 1L);
    when(service.getRefineryLocations()).thenReturn(List.of(loc1, loc2));
    when(mapper.toDto(loc1)).thenReturn(dto1);
    when(mapper.toDto(loc2)).thenReturn(dto2);

    List<LocationDto> result = controller.getRefineryLocations();

    assertEquals(List.of(dto1, dto2), result);
  }

  @Test
  void create_stripsServerManagedFields_andDelegatesToService() {
    // SECURITY: a client must not be able to set `id` or `version` via the
    // POST body and trigger an UPDATE instead of an INSERT. The controller
    // calls stripServerManaged() on the freshly mapped entity to guarantee
    // an INSERT path. This test pins that contract.
    LocationDto request = new LocationDto(UUID.randomUUID(), "New Loc", "desc", false, 99L);
    Location mappedEntity = new Location();
    mappedEntity.setId(request.id());
    mappedEntity.setVersion(request.version());

    when(mapper.toEntity(request)).thenReturn(mappedEntity);

    Location persisted = new Location();
    persisted.setId(UUID.randomUUID());
    persisted.setVersion(1L);
    LocationDto response = new LocationDto(persisted.getId(), "New Loc", "desc", false, 1L);

    when(service.createLocation(any())).thenReturn(persisted);
    when(mapper.toDto(persisted)).thenReturn(response);

    LocationDto result = controller.createLocation(request);

    assertSame(response, result);

    // Capture the entity actually passed to the service — its id/version
    // MUST be null (stripped).
    ArgumentCaptor<Location> entityCap = ArgumentCaptor.forClass(Location.class);
    verify(service).createLocation(entityCap.capture());
    Location forwarded = entityCap.getValue();
    assertNull(forwarded.getId(), "id must be stripped to force an INSERT");
    assertNull(forwarded.getVersion(), "version must be stripped so JPA initialises it");
  }

  @Test
  void update_forwardsIdAndDtoToService_withoutEntityMapping() {
    // Note: the update endpoint forwards the DTO directly (NOT the mapped
    // entity) so the service can apply only the user-mutable fields.
    UUID id = UUID.randomUUID();
    LocationDto request = new LocationDto(id, "Renamed", "desc", false, 4L);
    Location updated = new Location();
    LocationDto response = new LocationDto(id, "Renamed", "desc", false, 5L);

    when(service.updateLocation(id, request)).thenReturn(updated);
    when(mapper.toDto(updated)).thenReturn(response);

    LocationDto result = controller.updateLocation(id, request);

    assertSame(response, result);
    verify(service).updateLocation(id, request);
    verify(mapper, never()).toEntity(any(LocationDto.class));
  }

  @Test
  void delete_delegatesIdToService() {
    UUID id = UUID.randomUUID();

    controller.deleteLocation(id);

    verify(service).deleteLocation(id);
    verifyNoMoreInteractions(service, mapper);
  }
}

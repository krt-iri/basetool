package de.greluc.krt.iri.basetool.backend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.mapper.OutpostMapper;
import de.greluc.krt.iri.basetool.backend.model.Outpost;
import de.greluc.krt.iri.basetool.backend.model.dto.OutpostDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.service.OutpostService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/** Mockito unit tests for {@link OutpostController}. */
@ExtendWith(MockitoExtension.class)
class OutpostControllerTest {

  @Mock private OutpostService service;
  @Mock private OutpostMapper mapper;

  @InjectMocks private OutpostController controller;

  @Test
  void getAllOutposts_wrapsServicePageIntoPageResponse() {
    Outpost entity = new Outpost();
    OutpostDto dto =
        new OutpostDto(UUID.randomUUID(), "HDMS-Anderson", "Stanton", "Hurston", true, false);
    when(service.getAllOutposts(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
    when(mapper.toDto(entity)).thenReturn(dto);

    PageResponse<OutpostDto> resp = controller.getAllOutposts(null, null, null);

    assertEquals(1, resp.totalElements());
    assertSame(dto, resp.content().getFirst());
  }

  @Test
  void getOutpost_delegatesAndMaps() {
    UUID id = UUID.randomUUID();
    Outpost entity = new Outpost();
    OutpostDto dto = new OutpostDto(id, "HDMS-Anderson", "Stanton", "Hurston", true, false);
    when(service.getOutpost(id)).thenReturn(entity);
    when(mapper.toDto(entity)).thenReturn(dto);

    OutpostDto result = controller.getOutpost(id);

    assertSame(dto, result);
  }

  @Test
  void setLoadingDockOverride_forwardsValueAndReturnsDto() {
    UUID id = UUID.randomUUID();
    Outpost updated = new Outpost();
    OutpostDto response = new OutpostDto(id, "HDMS-Anderson", "Stanton", "Hurston", true, true);
    when(service.setLoadingDockOverride(id, true)).thenReturn(updated);
    when(mapper.toDto(updated)).thenReturn(response);

    OutpostDto result = controller.setLoadingDockOverride(id, true);

    assertSame(response, result);
    verify(service).setLoadingDockOverride(id, true);
  }

  @Test
  void clearLoadingDockOverride_callsServiceAndReturnsDto() {
    UUID id = UUID.randomUUID();
    Outpost updated = new Outpost();
    OutpostDto response = new OutpostDto(id, "HDMS-Anderson", "Stanton", "Hurston", true, false);
    when(service.clearLoadingDockOverride(id)).thenReturn(updated);
    when(mapper.toDto(updated)).thenReturn(response);

    OutpostDto result = controller.clearLoadingDockOverride(id);

    assertSame(response, result);
    verify(service).clearLoadingDockOverride(id);
  }
}

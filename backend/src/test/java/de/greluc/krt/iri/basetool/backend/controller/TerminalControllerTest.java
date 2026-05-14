package de.greluc.krt.iri.basetool.backend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.mapper.TerminalMapper;
import de.greluc.krt.iri.basetool.backend.model.Terminal;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.TerminalDto;
import de.greluc.krt.iri.basetool.backend.service.TerminalService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Pure-method unit tests for {@link TerminalController}. The PUT endpoint has a non-obvious
 * contract that's documented inline in the controller: even though it accepts a full {@link
 * TerminalDto}, the service only applies the {@code hidden} flag — a regression that started
 * writing the full DTO would unexpectedly let admins rename / re-link UEX-imported terminals via
 * this endpoint.
 */
@ExtendWith(MockitoExtension.class)
class TerminalControllerTest {

  @Mock private TerminalService service;
  @Mock private TerminalMapper mapper;

  @InjectMocks private TerminalController controller;

  @Test
  void getAllTerminals_wrapsServicePageIntoPageResponse() {
    Terminal entity = new Terminal();
    TerminalDto dto =
        new TerminalDto(
            UUID.randomUUID(), "TDD A18", null, "Stanton", "ArcCorp", "Area18", null, false);
    when(service.getAllTerminals(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
    when(mapper.toDto(entity)).thenReturn(dto);

    PageResponse<TerminalDto> resp = controller.getAllTerminals(null, null, null);

    assertEquals(1, resp.totalElements());
    assertSame(dto, resp.content().getFirst());
  }

  @Test
  void getTerminal_delegatesAndMaps() {
    UUID id = UUID.randomUUID();
    Terminal entity = new Terminal();
    TerminalDto dto = new TerminalDto(id, "x", null, null, null, null, null, false);
    when(service.getTerminal(id)).thenReturn(entity);
    when(mapper.toDto(entity)).thenReturn(dto);

    TerminalDto result = controller.getTerminal(id);

    assertSame(dto, result);
  }

  @Test
  void updateTerminal_onlyPropagatesHiddenFlag_notFullDto() {
    // SECURITY-ADJACENT: the controller's contract is "only `hidden` is mutable
    // through this endpoint" (see inline comment in TerminalController). A
    // refactor that switched to `service.update(id, fullDto)` would silently
    // let admins rewrite UEX-imported fields. This test pins the contract.
    UUID id = UUID.randomUUID();
    TerminalDto request =
        new TerminalDto(
            id, "Renamed", "Override", "FakeSystem", "FakePlanet", "FakeCity", "FakeStation", true);
    Terminal updated = new Terminal();
    TerminalDto response =
        new TerminalDto(id, "OriginalName", null, "Stanton", "ArcCorp", "Area18", null, true);

    when(service.updateTerminalVisibility(id, true)).thenReturn(updated);
    when(mapper.toDto(updated)).thenReturn(response);

    TerminalDto result = controller.updateTerminal(id, request);

    assertSame(response, result);
    verify(service).updateTerminalVisibility(id, true);
    // Critically: the FULL DTO is NEVER forwarded — only the boolean.
    verify(service, never()).getTerminal(any());
  }

  @Test
  void updateTerminal_passesHiddenFalseVerbatim() {
    UUID id = UUID.randomUUID();
    TerminalDto request = new TerminalDto(id, "x", null, null, null, null, null, false);
    when(service.updateTerminalVisibility(id, false)).thenReturn(new Terminal());
    when(mapper.toDto(any())).thenReturn(request);

    controller.updateTerminal(id, request);

    verify(service).updateTerminalVisibility(id, false);
  }
}

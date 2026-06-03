package de.greluc.krt.iri.basetool.backend.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.OrgChartPositionType;
import de.greluc.krt.iri.basetool.backend.model.dto.AreaLeadershipDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OrgChartDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OrgChartPositionCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.OrgChartPositionDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OrgChartPositionUpdateRequest;
import de.greluc.krt.iri.basetool.backend.service.OrgChartService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure-method unit tests for {@link OrgChartController}. Verifies that each endpoint delegates to
 * {@link OrgChartService} and returns its result unchanged; Spring-MVC binding and the
 * {@code @PreAuthorize} gates (GET = authenticated, writes = ADMIN) are covered by the security/MVC
 * integration layer, not here.
 */
@ExtendWith(MockitoExtension.class)
class OrgChartControllerTest {

  @Mock private OrgChartService orgChartService;

  @InjectMocks private OrgChartController controller;

  @Test
  void getOrgChart_delegatesToService() {
    OrgChartDto chart =
        new OrgChartDto(
            new AreaLeadershipDto(null, List.of(), List.of(), List.of()), List.of(), List.of());
    when(orgChartService.getOrgChart()).thenReturn(chart);

    assertSame(chart, controller.getOrgChart());
    verify(orgChartService).getOrgChart();
  }

  @Test
  void createPosition_delegatesRequestToService() {
    OrgChartPositionCreateRequest request =
        new OrgChartPositionCreateRequest(
            OrgChartPositionType.AREA_COORDINATOR, null, UUID.randomUUID(), null, null);
    OrgChartPositionDto response =
        new OrgChartPositionDto(
            UUID.randomUUID(),
            OrgChartPositionType.AREA_COORDINATOR,
            null,
            request.userId(),
            "Coordinator",
            null,
            0,
            0L);
    when(orgChartService.createPosition(request)).thenReturn(response);

    assertSame(response, controller.createPosition(request));
    verify(orgChartService).createPosition(request);
  }

  @Test
  void updatePosition_delegatesIdAndRequestToService() {
    UUID id = UUID.randomUUID();
    OrgChartPositionUpdateRequest request =
        new OrgChartPositionUpdateRequest(UUID.randomUUID(), null, 0L);
    OrgChartPositionDto response =
        new OrgChartPositionDto(
            id,
            OrgChartPositionType.SQUADRON_LEAD,
            UUID.randomUUID(),
            request.userId(),
            "Lead",
            null,
            0,
            1L);
    when(orgChartService.updatePosition(id, request)).thenReturn(response);

    assertSame(response, controller.updatePosition(id, request));
    verify(orgChartService).updatePosition(id, request);
  }

  @Test
  void deletePosition_delegatesIdToService() {
    UUID id = UUID.randomUUID();

    controller.deletePosition(id);

    verify(orgChartService).deletePosition(id);
    verifyNoMoreInteractions(orgChartService);
  }
}

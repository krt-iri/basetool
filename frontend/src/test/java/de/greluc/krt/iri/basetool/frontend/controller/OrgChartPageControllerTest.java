package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.frontend.model.dto.AreaLeadershipDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OrgChartDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

/**
 * Pure-method unit tests for {@link OrgChartPageController}. Verifies the read page loads the chart
 * for everyone but only preloads the user picker for admins, and that the AJAX write proxies relay
 * the backend's status + {@code {code, detail}} body on failure (so the page JS can toast the right
 * message).
 */
@SuppressWarnings("unchecked")
class OrgChartPageControllerTest {

  private static OrgChartDto emptyChart() {
    return new OrgChartDto(
        new AreaLeadershipDto(null, List.of(), List.of(), List.of()), List.of(), List.of());
  }

  private static Authentication auth(String role) {
    return new UsernamePasswordAuthenticationToken(
        "u", null, List.of(new SimpleGrantedAuthority(role)));
  }

  @Test
  void orgChart_nonAdmin_loadsChartWithoutPreloadingUsers() {
    BackendApiClient backend = mock(BackendApiClient.class);
    OrgChartPageController controller = new OrgChartPageController(backend);
    OrgChartDto chart = emptyChart();
    when(backend.get("/api/v1/org-chart", OrgChartDto.class)).thenReturn(chart);
    Model model = new ConcurrentModel();

    String view = controller.orgChart(model, auth("ROLE_SQUADRON_MEMBER"));

    assertEquals("org-chart", view);
    assertSame(chart, model.getAttribute("orgChart"));
    assertTrue(((List<?>) model.getAttribute("allUsers")).isEmpty());
    verify(backend, never()).get(eq("/api/v1/users/lookup"), any(ParameterizedTypeReference.class));
  }

  @Test
  void orgChart_admin_preloadsUserLookup() {
    BackendApiClient backend = mock(BackendApiClient.class);
    OrgChartPageController controller = new OrgChartPageController(backend);
    when(backend.get("/api/v1/org-chart", OrgChartDto.class)).thenReturn(emptyChart());
    when(backend.get(eq("/api/v1/users/lookup"), any(ParameterizedTypeReference.class)))
        .thenReturn(
            List.of(
                Map.of(
                    "id", UUID.randomUUID().toString(),
                    "username", "pilot",
                    "effectiveName", "Pilot")));
    Model model = new ConcurrentModel();

    controller.orgChart(model, auth("ROLE_ADMIN"));

    assertEquals(1, ((List<?>) model.getAttribute("allUsers")).size());
  }

  @Test
  void orgChart_backendFailure_setsErrorAttribute() {
    BackendApiClient backend = mock(BackendApiClient.class);
    OrgChartPageController controller = new OrgChartPageController(backend);
    when(backend.get("/api/v1/org-chart", OrgChartDto.class))
        .thenThrow(new BackendServiceException("boom", null, 503));
    Model model = new ConcurrentModel();

    String view = controller.orgChart(model, auth("ROLE_SQUADRON_MEMBER"));

    assertEquals("org-chart", view);
    assertEquals("error.orgChart.load", model.getAttribute("error"));
  }

  @Test
  void createPosition_success_returns200() {
    BackendApiClient backend = mock(BackendApiClient.class);
    OrgChartPageController controller = new OrgChartPageController(backend);
    when(backend.post(eq("/api/v1/org-chart/positions"), any(), eq(Object.class)))
        .thenReturn(new Object());

    ResponseEntity<Object> response =
        controller.createPosition(Map.of("positionType", "AREA_COORDINATOR"));

    assertEquals(200, response.getStatusCode().value());
  }

  @Test
  void createPosition_backendValidationError_relaysStatusAndBody() {
    BackendApiClient backend = mock(BackendApiClient.class);
    OrgChartPageController controller = new OrgChartPageController(backend);
    when(backend.post(eq("/api/v1/org-chart/positions"), any(), eq(Object.class)))
        .thenThrow(
            new BackendServiceException(
                "bad", null, 400, "BAD_REQUEST", null, List.of(), "Limit reached."));

    ResponseEntity<Object> response =
        controller.createPosition(Map.of("positionType", "COMMAND_LEAD"));

    assertEquals(400, response.getStatusCode().value());
    Map<String, Object> body = assertInstanceOf(Map.class, response.getBody());
    assertEquals("BAD_REQUEST", body.get("code"));
    assertEquals("Limit reached.", body.get("detail"));
  }

  @Test
  void updatePosition_optimisticLock_relays409() {
    BackendApiClient backend = mock(BackendApiClient.class);
    OrgChartPageController controller = new OrgChartPageController(backend);
    UUID id = UUID.randomUUID();
    when(backend.put(eq("/api/v1/org-chart/positions/" + id), any(), eq(Object.class)))
        .thenThrow(
            new BackendServiceException(
                "conflict", null, 409, "OPTIMISTIC_LOCK", null, List.of(), "Stale."));

    ResponseEntity<Object> response = controller.updatePosition(id, Map.of("version", 0));

    assertEquals(409, response.getStatusCode().value());
    Map<String, Object> body = assertInstanceOf(Map.class, response.getBody());
    assertEquals("OPTIMISTIC_LOCK", body.get("code"));
  }

  @Test
  void deletePosition_success_returns200() {
    BackendApiClient backend = mock(BackendApiClient.class);
    OrgChartPageController controller = new OrgChartPageController(backend);
    UUID id = UUID.randomUUID();

    ResponseEntity<Object> response = controller.deletePosition(id);

    assertEquals(200, response.getStatusCode().value());
    verify(backend).delete("/api/v1/org-chart/positions/" + id, Void.class);
  }

  @Test
  void deletePosition_notFound_relays404() {
    BackendApiClient backend = mock(BackendApiClient.class);
    OrgChartPageController controller = new OrgChartPageController(backend);
    UUID id = UUID.randomUUID();
    when(backend.delete("/api/v1/org-chart/positions/" + id, Void.class))
        .thenThrow(
            new BackendServiceException(
                "missing", null, 404, "NOT_FOUND", null, List.of(), "Gone."));

    ResponseEntity<Object> response = controller.deletePosition(id);

    assertEquals(404, response.getStatusCode().value());
    assertFalse(((Map<String, Object>) response.getBody()).isEmpty());
  }
}

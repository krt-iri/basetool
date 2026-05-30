package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.frontend.model.dto.BlueprintDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

/** Unit tests for {@link AdminBlueprintsPageController}. */
@SuppressWarnings("unchecked")
class AdminBlueprintsPageControllerTest {

  @Test
  void listBlueprints_populatesModelAndReturnsView() {
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    AdminBlueprintsPageController controller = new AdminBlueprintsPageController(backendApiClient);
    PageResponse<BlueprintDto> page =
        new PageResponse<>(List.of(minimalDto()), 0, 25, 1, 1, List.of());
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(page);
    Model model = new ConcurrentModel();

    String view = controller.listBlueprints("omni", 0, model);

    assertEquals("admin/blueprints", view);
    assertEquals(1, ((List<?>) model.getAttribute("blueprints")).size());
    assertEquals("omni", model.getAttribute("search"));
    assertEquals(1, model.getAttribute("totalPages"));
  }

  @Test
  void listBlueprints_backendFailure_setsErrorAndEmptyList() {
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
        .thenThrow(new RuntimeException("backend down"));
    AdminBlueprintsPageController controller = new AdminBlueprintsPageController(backendApiClient);
    Model model = new ConcurrentModel();

    String view = controller.listBlueprints(null, 0, model);

    assertEquals("admin/blueprints", view);
    assertEquals("error.admin.blueprints.load", model.getAttribute("error"));
    assertEquals(0, ((List<?>) model.getAttribute("blueprints")).size());
  }

  private static BlueprintDto minimalDto() {
    return new BlueprintDto(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "BP",
        "Omnisky",
        540,
        false,
        2,
        1,
        "4.8",
        null,
        null,
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        0L);
  }
}

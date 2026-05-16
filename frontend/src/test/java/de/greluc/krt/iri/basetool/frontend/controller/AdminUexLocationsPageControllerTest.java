package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

/**
 * Pure-method tests for {@link AdminUexLocationsPageController}.
 *
 * <p>The list-loading path is exercised end-to-end via the backend-mock; the override dispatcher
 * carries the more important contract (correct backend URL per {@code kind} + {@code action}
 * combination, plus rejection of an unknown kind without a backend call) and is tested explicitly
 * below.
 */
class AdminUexLocationsPageControllerTest {

  @Test
  void updateLoadingDockOverride_routesYesActionToPatchEndpoint() {
    BackendApiClient client = mock(BackendApiClient.class);
    AdminUexLocationsPageController controller = new AdminUexLocationsPageController(client);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
    UUID id = UUID.randomUUID();

    String view = controller.updateLoadingDockOverride("cities", id, "yes", attrs);

    assertEquals("redirect:/admin/uex-locations", view);
    verify(client)
        .patch(eq("/api/v1/cities/" + id + "/loading-dock?value=true"), any(), eq(Void.class));
  }

  @Test
  void updateLoadingDockOverride_routesNoActionToPatchEndpoint() {
    BackendApiClient client = mock(BackendApiClient.class);
    AdminUexLocationsPageController controller = new AdminUexLocationsPageController(client);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
    UUID id = UUID.randomUUID();

    controller.updateLoadingDockOverride("space-stations", id, "no", attrs);

    verify(client)
        .patch(
            eq("/api/v1/space-stations/" + id + "/loading-dock?value=false"),
            any(),
            eq(Void.class));
  }

  @Test
  void updateLoadingDockOverride_routesUexActionToDeleteEndpoint() {
    BackendApiClient client = mock(BackendApiClient.class);
    AdminUexLocationsPageController controller = new AdminUexLocationsPageController(client);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
    UUID id = UUID.randomUUID();

    controller.updateLoadingDockOverride("outposts", id, "uex", attrs);

    verify(client).delete("/api/v1/outposts/" + id + "/loading-dock-override", Void.class);
  }

  @Test
  void updateLoadingDockOverride_rejectsUnknownKindWithoutBackendCall() {
    BackendApiClient client = mock(BackendApiClient.class);
    AdminUexLocationsPageController controller = new AdminUexLocationsPageController(client);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
    UUID id = UUID.randomUUID();

    String view = controller.updateLoadingDockOverride("planets", id, "yes", attrs);

    assertEquals("redirect:/admin/uex-locations", view);
    verify(client, never()).patch(any(), any(), any());
    verify(client, never()).delete(any(), any());
  }

  @Test
  void updateLoadingDockOverride_rejectsUnknownActionWithoutBackendCall() {
    BackendApiClient client = mock(BackendApiClient.class);
    AdminUexLocationsPageController controller = new AdminUexLocationsPageController(client);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
    UUID id = UUID.randomUUID();

    controller.updateLoadingDockOverride("pois", id, "delete", attrs);

    verify(client, never()).patch(any(), any(), any());
    verify(client, never()).delete(any(), any());
  }
}

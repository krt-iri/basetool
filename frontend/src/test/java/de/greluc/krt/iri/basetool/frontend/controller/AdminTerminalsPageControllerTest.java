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
 * Mockito tests for {@link AdminTerminalsPageController}'s override dispatchers. The list-loading
 * code path is exercised via integration of the same backend client; the dispatcher carries the
 * non-trivial mapping from button action to backend URL and is tested in isolation here.
 */
class AdminTerminalsPageControllerTest {

  @Test
  void updateLoadingDockOverride_yesActionCallsBackendPatchWithTrue() {
    BackendApiClient client = mock(BackendApiClient.class);
    AdminTerminalsPageController controller = new AdminTerminalsPageController(client);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
    UUID id = UUID.randomUUID();

    String view = controller.updateLoadingDockOverride(id, "yes", attrs);

    assertEquals("redirect:/admin/terminals", view);
    verify(client)
        .patch(eq("/api/v1/terminals/" + id + "/loading-dock?value=true"), any(), eq(Void.class));
  }

  @Test
  void updateLoadingDockOverride_noActionCallsBackendPatchWithFalse() {
    BackendApiClient client = mock(BackendApiClient.class);
    AdminTerminalsPageController controller = new AdminTerminalsPageController(client);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
    UUID id = UUID.randomUUID();

    controller.updateLoadingDockOverride(id, "no", attrs);

    verify(client)
        .patch(eq("/api/v1/terminals/" + id + "/loading-dock?value=false"), any(), eq(Void.class));
  }

  @Test
  void updateLoadingDockOverride_uexActionCallsBackendDelete() {
    BackendApiClient client = mock(BackendApiClient.class);
    AdminTerminalsPageController controller = new AdminTerminalsPageController(client);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
    UUID id = UUID.randomUUID();

    controller.updateLoadingDockOverride(id, "uex", attrs);

    verify(client).delete("/api/v1/terminals/" + id + "/loading-dock-override", Void.class);
  }

  @Test
  void updateAutoLoadOverride_yesActionCallsBackendPatchWithTrue() {
    BackendApiClient client = mock(BackendApiClient.class);
    AdminTerminalsPageController controller = new AdminTerminalsPageController(client);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
    UUID id = UUID.randomUUID();

    controller.updateAutoLoadOverride(id, "yes", attrs);

    verify(client)
        .patch(eq("/api/v1/terminals/" + id + "/auto-load?value=true"), any(), eq(Void.class));
  }

  @Test
  void updateAutoLoadOverride_uexActionCallsBackendDelete() {
    BackendApiClient client = mock(BackendApiClient.class);
    AdminTerminalsPageController controller = new AdminTerminalsPageController(client);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
    UUID id = UUID.randomUUID();

    controller.updateAutoLoadOverride(id, "uex", attrs);

    verify(client).delete("/api/v1/terminals/" + id + "/auto-load-override", Void.class);
  }

  @Test
  void unknownActionDoesNotInvokeBackend() {
    BackendApiClient client = mock(BackendApiClient.class);
    AdminTerminalsPageController controller = new AdminTerminalsPageController(client);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
    UUID id = UUID.randomUUID();

    controller.updateLoadingDockOverride(id, "garbage", attrs);

    verify(client, never()).patch(any(), any(), any());
    verify(client, never()).delete(any(), any());
  }
}

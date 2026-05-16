package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.TerminalDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

/**
 * Mockito tests for {@link AdminTerminalsPageController}'s override dispatchers and list-loading
 * code path. The dispatcher carries the non-trivial mapping from button action to backend URL; the
 * list-loading code path now also has to parse the raw UEX mirror fields and derive a global
 * "latest UEX sync" attribute for the page header, so both flows are pinned here in isolation.
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

  @Test
  @SuppressWarnings("unchecked")
  void listData_parsesUexMirrorFields_andComputesLatestSyncAttribute() {
    // The backend returns the terminal projection as a JSON map; the frontend
    // controller is responsible for parsing the new `uex*` mirror fields plus an
    // ISO-8601 `uexSyncedAt` string, and for deriving the global "latest UEX sync"
    // header attribute as the max() across rows. This test pins all three
    // behaviours at once because they are tightly coupled in the same loop.
    BackendApiClient client = mock(BackendApiClient.class);
    AdminTerminalsPageController controller = new AdminTerminalsPageController(client);
    Instant olderSync = Instant.parse("2026-05-16T08:00:00Z");
    Instant newerSync = Instant.parse("2026-05-16T12:30:00Z");
    UUID idOlder = UUID.randomUUID();
    UUID idNewer = UUID.randomUUID();
    UUID idNever = UUID.randomUUID();

    Map<String, Object> rowOlder =
        Map.of(
            "id", idOlder.toString(),
            "name", "Lorville TDD",
            "hasLoadingDock", true,
            "isAutoLoad", false,
            "hasLoadingDockOverridden", true,
            "isAutoLoadOverridden", false,
            "uexHasLoadingDock", false,
            "uexIsAutoLoad", false,
            "uexSyncedAt", olderSync.toString());
    Map<String, Object> rowNewer =
        Map.of(
            "id", idNewer.toString(),
            "name", "Area 18 TDD",
            "hasLoadingDock", true,
            "isAutoLoad", true,
            "hasLoadingDockOverridden", false,
            "isAutoLoadOverridden", false,
            "uexHasLoadingDock", true,
            "uexIsAutoLoad", true,
            "uexSyncedAt", newerSync.toString());
    java.util.Map<String, Object> rowNever = new java.util.HashMap<>();
    rowNever.put("id", idNever.toString());
    rowNever.put("name", "Brand New Terminal");
    rowNever.put("hasLoadingDock", false);
    rowNever.put("isAutoLoad", false);
    rowNever.put("hasLoadingDockOverridden", false);
    rowNever.put("isAutoLoadOverridden", false);
    rowNever.put("uexHasLoadingDock", null);
    rowNever.put("uexIsAutoLoad", null);
    rowNever.put("uexSyncedAt", null);

    PageResponse<Map<String, Object>> page =
        new PageResponse<>(List.of(rowOlder, rowNewer, rowNever), 0, 10, 3, 1, List.of());
    when(client.get(
            eq("/api/v1/terminals?size=10000&sort=name,asc"),
            any(ParameterizedTypeReference.class)))
        .thenReturn(page);

    ConcurrentModel model = new ConcurrentModel();
    String view = controller.listData(model);

    assertEquals("admin/terminals", view);
    assertEquals(newerSync, model.getAttribute("latestUexSync"));
    List<TerminalDto> terminals = (List<TerminalDto>) model.getAttribute("terminals");
    assertNotNull(terminals);
    assertEquals(3, terminals.size());
    // Sorted alphabetically (case-insensitive) by name → Area 18 < Brand New < Lorville.
    assertEquals("Area 18 TDD", terminals.get(0).name());
    assertTrue(terminals.get(0).uexHasLoadingDock());
    assertTrue(terminals.get(0).uexIsAutoLoad());
    assertEquals(newerSync, terminals.get(0).uexSyncedAt());
    assertEquals("Brand New Terminal", terminals.get(1).name());
    assertNull(terminals.get(1).uexHasLoadingDock());
    assertNull(terminals.get(1).uexIsAutoLoad());
    assertNull(terminals.get(1).uexSyncedAt());
    assertEquals("Lorville TDD", terminals.get(2).name());
    assertFalse(terminals.get(2).uexHasLoadingDock());
    assertFalse(terminals.get(2).uexIsAutoLoad());
    assertEquals(olderSync, terminals.get(2).uexSyncedAt());
  }

  @Test
  @SuppressWarnings("unchecked")
  void listData_latestSyncIsNull_whenNoTerminalHasBeenSyncedYet() {
    // Fresh DB: the migration backfilled boolean mirrors but uex_synced_at is NULL on
    // every row until the first sweep runs. The page should still load; the header
    // stamp template branch renders "noch nicht gesynct" off the null attribute.
    BackendApiClient client = mock(BackendApiClient.class);
    AdminTerminalsPageController controller = new AdminTerminalsPageController(client);
    java.util.Map<String, Object> row = new java.util.HashMap<>();
    row.put("id", UUID.randomUUID().toString());
    row.put("name", "Unsynced");
    row.put("hasLoadingDock", false);
    row.put("isAutoLoad", false);
    row.put("hasLoadingDockOverridden", false);
    row.put("isAutoLoadOverridden", false);
    row.put("uexSyncedAt", null);

    PageResponse<Map<String, Object>> page =
        new PageResponse<>(List.of(row), 0, 10, 1, 1, List.of());
    when(client.get(
            eq("/api/v1/terminals?size=10000&sort=name,asc"),
            any(ParameterizedTypeReference.class)))
        .thenReturn(page);

    ConcurrentModel model = new ConcurrentModel();
    controller.listData(model);

    assertNull(model.getAttribute("latestUexSync"));
  }
}

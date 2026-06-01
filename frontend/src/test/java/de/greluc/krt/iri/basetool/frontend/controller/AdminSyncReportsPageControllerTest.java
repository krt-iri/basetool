package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.frontend.model.dto.SyncReportPurgeResultDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

/**
 * Mockito tests for the "delete reports older than X days" action on {@link
 * AdminSyncReportsPageController}. Pins the three behaviours that carry risk: (1) a blank source
 * purges the combined view and redirects to the combined tab, (2) a source tab is relayed to the
 * backend and the user lands back on that tab, and (3) invalid input / backend failure
 * short-circuit to an error flash without (respectively, regardless of) a backend call.
 */
class AdminSyncReportsPageControllerTest {

  @Test
  void deleteOld_blankSource_purgesCombinedViewAndRedirectsToCombinedTab() {
    BackendApiClient client = mock(BackendApiClient.class);
    when(client.delete(
            eq("/api/v1/sync-reports?olderThanDays=30"), eq(SyncReportPurgeResultDto.class)))
        .thenReturn(new SyncReportPurgeResultDto(7));
    AdminSyncReportsPageController controller = new AdminSyncReportsPageController(client);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();

    String view = controller.deleteOld("", 30, attrs);

    assertEquals("redirect:/admin/sync-reports", view);
    assertEquals(7, attrs.getFlashAttributes().get("deletedCount"));
    verify(client).delete("/api/v1/sync-reports?olderThanDays=30", SyncReportPurgeResultDto.class);
  }

  @Test
  void deleteOld_withSource_relaysSourceAndRedirectsToThatTab() {
    BackendApiClient client = mock(BackendApiClient.class);
    when(client.delete(
            eq("/api/v1/sync-reports?olderThanDays=14&source=UEX"),
            eq(SyncReportPurgeResultDto.class)))
        .thenReturn(new SyncReportPurgeResultDto(2));
    AdminSyncReportsPageController controller = new AdminSyncReportsPageController(client);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();

    String view = controller.deleteOld("UEX", 14, attrs);

    assertEquals("redirect:/admin/sync-reports/uex", view);
    assertEquals(2, attrs.getFlashAttributes().get("deletedCount"));
    verify(client)
        .delete("/api/v1/sync-reports?olderThanDays=14&source=UEX", SyncReportPurgeResultDto.class);
  }

  @Test
  void deleteOld_rejectsNonPositiveDaysWithoutBackendCall() {
    BackendApiClient client = mock(BackendApiClient.class);
    AdminSyncReportsPageController controller = new AdminSyncReportsPageController(client);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();

    String view = controller.deleteOld("SCWIKI", 0, attrs);

    assertEquals("redirect:/admin/sync-reports/scwiki", view);
    assertEquals("error.admin.syncReports.delete", attrs.getFlashAttributes().get("error"));
    verify(client, never())
        .delete(ArgumentMatchers.<String>any(), ArgumentMatchers.<Class<?>>any());
  }

  @Test
  void deleteOld_backendFailureSurfacesErrorFlash() {
    BackendApiClient client = mock(BackendApiClient.class);
    when(client.delete(any(String.class), eq(SyncReportPurgeResultDto.class)))
        .thenThrow(new BackendServiceException("boom", new RuntimeException(), 500));
    AdminSyncReportsPageController controller = new AdminSyncReportsPageController(client);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();

    String view = controller.deleteOld("", 30, attrs);

    assertEquals("redirect:/admin/sync-reports", view);
    assertEquals("error.admin.syncReports.delete", attrs.getFlashAttributes().get("error"));
  }
}

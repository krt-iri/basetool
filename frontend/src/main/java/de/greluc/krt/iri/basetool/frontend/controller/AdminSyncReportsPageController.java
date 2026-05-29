package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.SyncReportDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Spring MVC controller backing the {@code /admin/sync-reports} pages (SC_WIKI_SYNC_PLAN.md §8.8):
 * a combined view plus per-source views for SC Wiki and UEX. All three render the same {@code
 * admin/sync-reports} template, differing only in the {@code source} filter relayed to the backend
 * and the active-tab marker.
 *
 * <p>Admin-only — class-level {@code @PreAuthorize("hasRole('ADMIN')")} matches the backend gate.
 * Read-only: the page never mutates the audit log.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminSyncReportsPageController {

  private static final int PAGE_SIZE = 100;

  private final BackendApiClient backendApiClient;

  /**
   * Combined view across both catalogues.
   *
   * @param page zero-based page index
   * @param model Thymeleaf model
   * @return the {@code admin/sync-reports} view name
   */
  @GetMapping("/admin/sync-reports")
  public String combined(
      @RequestParam(required = false, defaultValue = "0") int page, Model model) {
    return render(null, "ALL", "/admin/sync-reports", page, model);
  }

  /**
   * SC Wiki-only view.
   *
   * @param page zero-based page index
   * @param model Thymeleaf model
   * @return the {@code admin/sync-reports} view name
   */
  @GetMapping("/admin/sync-reports/scwiki")
  public String scwiki(@RequestParam(required = false, defaultValue = "0") int page, Model model) {
    return render("SCWIKI", "SCWIKI", "/admin/sync-reports/scwiki", page, model);
  }

  /**
   * UEX-only view.
   *
   * @param page zero-based page index
   * @param model Thymeleaf model
   * @return the {@code admin/sync-reports} view name
   */
  @GetMapping("/admin/sync-reports/uex")
  public String uex(@RequestParam(required = false, defaultValue = "0") int page, Model model) {
    return render("UEX", "UEX", "/admin/sync-reports/uex", page, model);
  }

  /**
   * Shared render path: fetches one page of events from the backend (filtered to {@code source}
   * when non-null), populates the model, and returns the view name. A backend failure collapses to
   * an error banner with an empty list rather than a 500.
   *
   * @param source backend source filter ({@code "SCWIKI"} / {@code "UEX"}), or {@code null} for the
   *     combined view
   * @param activeTab marker for the active tab in the template ({@code "ALL"} / {@code "SCWIKI"} /
   *     {@code "UEX"})
   * @param basePath the page's own path, used to build pager links
   * @param page zero-based page index
   * @param model Thymeleaf model
   * @return the {@code admin/sync-reports} view name
   */
  private String render(String source, String activeTab, String basePath, int page, Model model) {
    int safePage = Math.max(page, 0);
    String uri = "/api/v1/sync-reports?page=" + safePage + "&size=" + PAGE_SIZE;
    if (source != null) {
      uri += "&source=" + source;
    }
    try {
      PageResponse<SyncReportDto> events =
          backendApiClient.get(
              uri, new ParameterizedTypeReference<PageResponse<SyncReportDto>>() {});
      if (events != null) {
        model.addAttribute("events", events.content() == null ? List.of() : events.content());
        model.addAttribute("currentPage", events.page());
        model.addAttribute("totalPages", events.totalPages());
        model.addAttribute("totalElements", events.totalElements());
      } else {
        populateEmpty(model);
      }
    } catch (Exception e) {
      log.error("Failed to load sync reports (source={})", source, e);
      model.addAttribute("error", "error.admin.syncReports.load");
      populateEmpty(model);
    }
    model.addAttribute("activeTab", activeTab);
    model.addAttribute("basePath", basePath);
    return "admin/sync-reports";
  }

  /**
   * Fills the paging model attributes with an empty result, used on a backend miss / failure so the
   * template never dereferences a missing attribute.
   *
   * @param model Thymeleaf model to fill
   */
  private void populateEmpty(Model model) {
    model.addAttribute("events", List.of());
    model.addAttribute("currentPage", 0);
    model.addAttribute("totalPages", 0);
    model.addAttribute("totalElements", 0L);
  }
}

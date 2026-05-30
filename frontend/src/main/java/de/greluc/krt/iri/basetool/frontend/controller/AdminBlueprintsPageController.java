package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.BlueprintDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Spring MVC controller backing the {@code /admin/blueprints} page: a paginated, filterable list of
 * the synced SC Wiki crafting blueprints with their ingredients and per-slot stat modifiers.
 *
 * <p>Admin-only — class-level {@code @PreAuthorize("hasRole('ADMIN')")} matches the backend gate.
 * Read-only: the SC Wiki sync is the only writer. Filtering and paging are server-side (relayed to
 * {@code GET /api/v1/blueprints}); a backend failure collapses to an error banner with an empty
 * list rather than a 500.
 */
@Controller
@RequestMapping("/admin/blueprints")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminBlueprintsPageController {

  /** Page size for the blueprint list — one detail-rich card per row, so kept modest. */
  private static final int PAGE_SIZE = 25;

  private final BackendApiClient backendApiClient;

  /**
   * Loads one page of blueprints, optionally filtered by an output-item-name / Wiki-key substring,
   * and populates the model for the {@code admin/blueprints} view.
   *
   * @param search optional case-insensitive output-name / key filter
   * @param page zero-based page index
   * @param model Thymeleaf model
   * @return the {@code admin/blueprints} view name
   */
  @GetMapping
  public String listBlueprints(
      @RequestParam(required = false) String search,
      @RequestParam(required = false, defaultValue = "0") int page,
      Model model) {
    int safePage = Math.max(page, 0);
    String trimmed = (search == null || search.isBlank()) ? null : search.trim();

    StringBuilder uri =
        new StringBuilder("/api/v1/blueprints?size=")
            .append(PAGE_SIZE)
            .append("&page=")
            .append(safePage)
            .append("&sort=outputName,asc");
    if (trimmed != null) {
      uri.append("&search=").append(URLEncoder.encode(trimmed, StandardCharsets.UTF_8));
    }

    try {
      PageResponse<BlueprintDto> response =
          backendApiClient.get(
              uri.toString(), new ParameterizedTypeReference<PageResponse<BlueprintDto>>() {});
      if (response != null) {
        model.addAttribute(
            "blueprints", response.content() == null ? List.of() : response.content());
        model.addAttribute("currentPage", response.page());
        model.addAttribute("totalPages", response.totalPages());
        model.addAttribute("totalElements", response.totalElements());
      } else {
        populateEmpty(model);
      }
    } catch (Exception e) {
      log.error("Error loading blueprints data (search={})", trimmed, e);
      model.addAttribute("error", "error.admin.blueprints.load");
      populateEmpty(model);
    }
    model.addAttribute("search", trimmed == null ? "" : trimmed);
    return "admin/blueprints";
  }

  /**
   * Fills the paging model attributes with an empty result so the template never dereferences a
   * missing attribute on a backend miss / failure.
   *
   * @param model Thymeleaf model to fill
   */
  private void populateEmpty(Model model) {
    model.addAttribute("blueprints", List.of());
    model.addAttribute("currentPage", 0);
    model.addAttribute("totalPages", 0);
    model.addAttribute("totalElements", 0L);
  }
}

package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.BlueprintProductDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.PersonalBlueprintBatchCreateRequest;
import de.greluc.krt.iri.basetool.frontend.model.dto.PersonalBlueprintBatchResultDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PersonalBlueprintDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PersonalBlueprintRecipeDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PersonalBlueprintUpdateRequest;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Page controller for the Blueprints sub-page of the personal inventory area (#327, Phase 5).
 * Renders the owned-blueprint list and proxies the type-ahead product search, the multi-select
 * batch add, and the per-row note edit / remove to the backend via {@link BackendApiClient}.
 *
 * <p>The owner is always the authenticated caller — derived from the bearer-relayed JWT on the
 * backend side, never accepted from the request — so a user can only ever see or mutate their own
 * blueprints. Edit / remove use a full redirect so the optimistic-lock {@code version} re-syncs on
 * every related row without per-node DOM patching.
 */
@Controller
@RequestMapping("/personal-inventory/blueprints")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Slf4j
public class PersonalInventoryBlueprintsPageController {

  /** Page size for the owned-blueprint list — modest, one row per product. */
  private static final int PAGE_SIZE = 200;

  private final BackendApiClient backendApiClient;

  /**
   * Renders the owned-blueprint list with the multi-select add bar and the edit / remove modals.
   *
   * @param q optional case-insensitive product-name filter, echoed into the search input
   * @param model Thymeleaf model populated with the blueprint list and the filter query
   * @return the {@code personal-inventory-blueprints} view name
   */
  @GetMapping
  public String view(@RequestParam(required = false) String q, Model model) {
    model.addAttribute("filterQuery", q == null ? "" : q);
    PageResponse<PersonalBlueprintDto> blueprints = fetchOwned(q);
    model.addAttribute(
        "blueprints", blueprints != null ? blueprints.content() : Collections.emptyList());
    return "personal-inventory-blueprints";
  }

  /**
   * Type-ahead proxy backing the multi-select add. Relays the query to the backend product search
   * and returns the hits (each carrying an owned-by-caller flag). Proxied through the frontend so
   * the same Spring Security session and CSRF policy apply; failures collapse to an empty list so
   * the type-ahead never shows a stack trace.
   *
   * @param q optional case-insensitive product-name substring
   * @param limit optional result cap; defaults to 25 and is clamped to {@code [1, 200]}
   * @return the matching products, or an empty list on any backend failure
   */
  @GetMapping("/search")
  @ResponseBody
  public List<BlueprintProductDto> search(
      @RequestParam(required = false) String q, @RequestParam(required = false) Integer limit) {
    try {
      String query = q == null ? "" : q;
      int effectiveLimit = limit == null ? 25 : Math.min(200, Math.max(1, limit));
      String uri =
          "/api/v1/blueprints/products/search?q="
              + URLEncoder.encode(query, StandardCharsets.UTF_8)
              + "&limit="
              + effectiveLimit;
      List<BlueprintProductDto> result =
          backendApiClient.get(uri, new ParameterizedTypeReference<>() {});
      return result == null ? Collections.emptyList() : result;
    } catch (Exception e) {
      log.warn("Blueprint product type-ahead failed for query='{}': {}", q, e.getMessage());
      return Collections.emptyList();
    }
  }

  /**
   * Recipe proxy backing the expandable "Zutaten &amp; Stats" detail of an owned blueprint. Relays
   * to the backend owned-blueprint recipe endpoint and returns the ingredients + per-quality stat
   * modifiers as JSON; failures collapse to an empty recipe so the detail panel renders a graceful
   * "no data" state rather than a stack trace.
   *
   * @param id owned-blueprint entry id
   * @return the recipe view, or an empty recipe on any backend failure
   */
  @GetMapping("/{id}/recipe")
  @ResponseBody
  public PersonalBlueprintRecipeDto recipe(@PathVariable @NotNull UUID id) {
    try {
      PersonalBlueprintRecipeDto result =
          backendApiClient.get(
              "/api/v1/personal-blueprints/" + id + "/recipe", PersonalBlueprintRecipeDto.class);
      return result == null ? emptyRecipe() : result;
    } catch (Exception e) {
      log.warn("Failed to fetch blueprint recipe {}: {}", id, e.getMessage());
      return emptyRecipe();
    }
  }

  /**
   * Builds an empty recipe view (no product name, no variants, no groups/ingredients) returned when
   * the backend recipe call fails or yields nothing, so the client always receives a well-formed
   * payload to render the "no data" state.
   *
   * @return an empty recipe DTO
   */
  private static PersonalBlueprintRecipeDto emptyRecipe() {
    return new PersonalBlueprintRecipeDto(null, 0, List.of(), List.of());
  }

  /**
   * Multi-select batch add. Relays the staged product keys to the backend batch endpoint and
   * returns the summary so the client can toast added / skipped counts and reload the list. AJAX
   * (CSRF-protected) rather than a form post so the user can keep searching and staging before
   * committing.
   *
   * @param productKeys the normalized product keys staged by the user
   * @return the batch result (added / skipped counts), or a zeroed result on backend failure
   */
  @PostMapping("/add-selected")
  @ResponseBody
  public PersonalBlueprintBatchResultDto addSelected(@RequestBody List<String> productKeys) {
    List<String> keys = productKeys == null ? List.of() : productKeys;
    if (keys.isEmpty()) {
      return new PersonalBlueprintBatchResultDto(0, 0, 0);
    }
    try {
      PersonalBlueprintBatchResultDto result =
          backendApiClient.post(
              "/api/v1/personal-blueprints/batch",
              new PersonalBlueprintBatchCreateRequest(keys),
              PersonalBlueprintBatchResultDto.class);
      return result == null ? new PersonalBlueprintBatchResultDto(0, 0, 0) : result;
    } catch (Exception e) {
      log.error("Batch blueprint add failed for {} key(s)", keys.size(), e);
      return new PersonalBlueprintBatchResultDto(0, 0, 0);
    }
  }

  /**
   * Updates the note of an owned blueprint, preserving the acquisition timestamp. A 409 surfaces as
   * the dedicated optimistic-lock toast.
   *
   * @param id blueprint entry id
   * @param note the new note text
   * @param acquiredAt the preserved acquisition instant (ISO-8601), or blank for none
   * @param version the last seen optimistic-lock version
   * @param redirectAttributes flash attributes carrier
   * @return redirect to the Blueprints page
   */
  @PostMapping("/{id}/update-note")
  public String updateNote(
      @PathVariable @NotNull UUID id,
      @RequestParam(required = false) String note,
      @RequestParam(required = false) String acquiredAt,
      @RequestParam Long version,
      RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.put(
          "/api/v1/personal-blueprints/" + id,
          new PersonalBlueprintUpdateRequest(
              parseInstantOrNull(acquiredAt), emptyToNull(note), version),
          PersonalBlueprintDto.class);
      redirectAttributes.addFlashAttribute(
          "successToast", "personalInventory.blueprints.toast.noteUpdated");
    } catch (Exception e) {
      log.error("Failed to update blueprint note {}", id, e);
      redirectAttributes.addFlashAttribute(
          "errorToast", classifyError(e, "personalInventory.blueprints.error.update"));
    }
    return "redirect:/personal-inventory/blueprints";
  }

  /**
   * Removes an owned blueprint from the caller's set.
   *
   * @param id blueprint entry id
   * @param redirectAttributes flash attributes carrier
   * @return redirect to the Blueprints page
   */
  @PostMapping("/{id}/delete")
  public String delete(@PathVariable @NotNull UUID id, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/personal-blueprints/" + id, Void.class);
      redirectAttributes.addFlashAttribute(
          "successToast", "personalInventory.blueprints.toast.removed");
    } catch (Exception e) {
      log.error("Failed to remove blueprint {}", id, e);
      redirectAttributes.addFlashAttribute(
          "errorToast", classifyError(e, "personalInventory.blueprints.error.remove"));
    }
    return "redirect:/personal-inventory/blueprints";
  }

  /**
   * Fetches the caller's owned blueprints, optionally filtered by a product-name substring, sorted
   * alphabetically by product name. A backend failure collapses to an empty page rather than a 500.
   *
   * @param q optional case-insensitive product-name filter
   * @return the owned-blueprint page, or an empty page on failure
   */
  private PageResponse<PersonalBlueprintDto> fetchOwned(String q) {
    try {
      StringBuilder uri =
          new StringBuilder("/api/v1/personal-blueprints?size=")
              .append(PAGE_SIZE)
              .append("&sort=productName,asc");
      if (q != null && !q.isBlank()) {
        uri.append("&q=").append(URLEncoder.encode(q, StandardCharsets.UTF_8));
      }
      return backendApiClient.get(uri.toString(), new ParameterizedTypeReference<>() {});
    } catch (Exception e) {
      log.error("Failed to fetch owned blueprints", e);
      return new PageResponse<>(new ArrayList<>(), 0, PAGE_SIZE, 0, 0, List.of());
    }
  }

  /**
   * Parses an ISO-8601 instant string, tolerating a blank value (returns {@code null}). Invalid
   * input is also treated as {@code null} so a malformed hidden field never blocks a note edit.
   *
   * @param iso the ISO-8601 instant string, or blank
   * @return the parsed instant, or {@code null}
   */
  private static Instant parseInstantOrNull(String iso) {
    if (iso == null || iso.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(iso.trim());
    } catch (Exception e) {
      log.debug("Ignoring unparseable acquiredAt '{}'", iso);
      return null;
    }
  }

  /**
   * Collapses a blank string to {@code null} so an empty note clears rather than stores whitespace.
   *
   * @param value the raw value
   * @return the trimmed value, or {@code null} if blank
   */
  private static String emptyToNull(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }

  /**
   * Maps backend exceptions to specific toast keys, distinguishing only the 409 optimistic-lock
   * conflict from the supplied generic key.
   *
   * @param e the caught exception
   * @param defaultKey the fallback toast key
   * @return the resolved toast key
   */
  private String classifyError(Exception e, String defaultKey) {
    if (e instanceof de.greluc.krt.iri.basetool.frontend.service.BackendServiceException bse
        && bse.getStatusCode() == 409) {
      return "personalInventory.blueprints.error.conflict";
    }
    return defaultKey;
  }
}

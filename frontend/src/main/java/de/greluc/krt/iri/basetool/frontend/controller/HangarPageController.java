/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.LocationDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.ManufacturerDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OrgUnitMembershipOptionDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.SetHomeLocationRequestDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.ShipDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.ShipRequestDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.ShipTypeDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.SquadronShipOverviewDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.frontend.model.form.ShipForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.ParallelPageLoader;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC controller for the personal hangar pages ({@code /hangar} and {@code
 * /hangar/squadron}).
 *
 * <p>The personal hangar lists the current user's ships with a multi-key sort: manufacturer name,
 * ship type, insurance tier (LTI &lt; numeric &lt; unset), insurance number desc, location and
 * finally fitted-status + name. The order is deliberate — fleet members compare insurance state
 * across ships of the same type, so insurance grouping has to win over location. The squadron
 * overview aggregates the entire org's hangar into a count-per-type table.
 */
@Controller
@RequestMapping("/hangar")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class HangarPageController {

  private final BackendApiClient backendApiClient;
  private final ParallelPageLoader parallelPageLoader;

  /**
   * Multi-key comparator that orders ships by manufacturer, ship type, insurance tier (LTI &lt;
   * numeric &lt; unset), insurance amount desc, location, fitted-status and finally name. Kept as a
   * shared instance because the comparator is stateless and the controller sorts the ship list on
   * every render.
   */
  private static final Comparator<ShipDto> SHIP_SORT =
      Comparator.comparing(
              (ShipDto s) ->
                  s.shipType() != null && s.shipType().manufacturer() != null
                      ? s.shipType().manufacturer().name()
                      : "",
              String.CASE_INSENSITIVE_ORDER)
          .thenComparing(
              s -> s.shipType() != null ? s.shipType().name() : "", String.CASE_INSENSITIVE_ORDER)
          .thenComparing(
              (ShipDto s) -> {
                String ins = s.insurance();
                if (ins == null || ins.equals("0")) {
                  return 3;
                }
                if (ins.equals("LTI")) {
                  return 1;
                }
                return 2;
              })
          .thenComparing(
              (ShipDto s) -> {
                String ins = s.insurance();
                if (ins == null || ins.equals("LTI") || ins.equals("0")) {
                  return 0;
                }
                try {
                  return Integer.parseInt(ins);
                } catch (NumberFormatException e) {
                  return 0;
                }
              },
              Comparator.reverseOrder())
          .thenComparing(
              s -> s.location() != null ? s.location().name() : "", String.CASE_INSENSITIVE_ORDER)
          .thenComparing(s -> (s.fitted() != null && s.fitted()) ? 0 : 1)
          .thenComparing(s -> s.name() != null ? s.name() : "", String.CASE_INSENSITIVE_ORDER);

  /**
   * Renders the personal hangar page. Fetches my ships and the three cached reference catalogs
   * (ship types, locations, manufacturers) in parallel via {@link ParallelPageLoader}; each catalog
   * call independently degrades to an empty list on backend failure so a single dead reference
   * catalog never blanks the whole page. The ship list is sorted client-side with the multi-key
   * comparator described in the class Javadoc.
   *
   * @param model Thymeleaf model populated with the ship form, ship list and reference catalogs
   * @return the {@code hangar} view name
   */
  @GetMapping
  public String viewHangar(Model model) {
    if (!model.containsAttribute("shipForm")) {
      model.addAttribute("shipForm", new ShipForm());
    }

    CompletableFuture<List<ShipDto>> shipsFuture =
        parallelPageLoader
            .<List<ShipDto>>loadAsync(
                () -> {
                  PageResponse<ShipDto> p =
                      backendApiClient.get(
                          "/api/v1/hangar/my-ships?size=1000",
                          new ParameterizedTypeReference<PageResponse<ShipDto>>() {});
                  return p != null && p.content() != null
                      ? new ArrayList<>(p.content())
                      : new ArrayList<>();
                })
            .exceptionally(
                e -> {
                  log.error("Failed to fetch my ships", e);
                  model.addAttribute("error", "error.hangar.ships.load");
                  return new ArrayList<>();
                });

    CompletableFuture<List<ShipTypeDto>> shipTypesFuture =
        parallelPageLoader
            .<List<ShipTypeDto>>loadAsync(
                () -> {
                  PageResponse<ShipTypeDto> p =
                      backendApiClient.getCached(
                          "/api/v1/ship-types?size=1000",
                          new ParameterizedTypeReference<PageResponse<ShipTypeDto>>() {});
                  return p != null && p.content() != null
                      ? new ArrayList<>(p.content())
                      : new ArrayList<>();
                })
            .exceptionally(
                e -> {
                  log.error("Failed to fetch ship types", e);
                  return new ArrayList<>();
                });

    CompletableFuture<List<LocationDto>> locationsFuture =
        parallelPageLoader
            .<List<LocationDto>>loadAsync(
                () -> {
                  PageResponse<LocationDto> p =
                      backendApiClient.getCached(
                          "/api/v1/locations?size=1000",
                          new ParameterizedTypeReference<PageResponse<LocationDto>>() {});
                  return p != null && p.content() != null
                      ? new ArrayList<>(p.content())
                      : new ArrayList<>();
                })
            .exceptionally(
                e -> {
                  log.error("Failed to fetch locations", e);
                  return new ArrayList<>();
                });

    CompletableFuture<List<ManufacturerDto>> manufacturersFuture =
        parallelPageLoader
            .<List<ManufacturerDto>>loadAsync(
                () -> {
                  PageResponse<ManufacturerDto> p =
                      backendApiClient.getCached(
                          "/api/v1/manufacturers?size=1000",
                          new ParameterizedTypeReference<PageResponse<ManufacturerDto>>() {});
                  return p != null && p.content() != null
                      ? new ArrayList<>(p.content())
                      : new ArrayList<>();
                })
            .exceptionally(
                e -> {
                  log.error("Failed to fetch manufacturers", e);
                  return new ArrayList<>();
                });

    CompletableFuture<List<LocationDto>> homeLocationsFuture =
        parallelPageLoader
            .<List<LocationDto>>loadAsync(
                () -> {
                  List<LocationDto> hl =
                      backendApiClient.getCached(
                          "/api/v1/locations/home-locations",
                          new ParameterizedTypeReference<List<LocationDto>>() {});
                  return hl != null ? new ArrayList<>(hl) : new ArrayList<>();
                })
            .exceptionally(
                e -> {
                  log.error("Failed to fetch home locations", e);
                  return new ArrayList<>();
                });

    // join() blocks until every parallel fetch finishes; each call ran on its own virtual thread
    // with the full request-scoped context (SecurityContext / RequestAttributes / squadron /
    // correlation id) restored, so OAuth2 bearer relay and squadron-header propagation behave
    // identically to the previous sequential implementation.
    CompletableFuture.allOf(
            shipsFuture, shipTypesFuture, locationsFuture, manufacturersFuture, homeLocationsFuture)
        .join();

    List<ShipDto> myShips = shipsFuture.join();
    myShips.sort(SHIP_SORT);

    List<ShipTypeDto> shipTypes = shipTypesFuture.join();
    shipTypes.sort(Comparator.comparing(ShipTypeDto::name, String.CASE_INSENSITIVE_ORDER));

    List<LocationDto> locations = locationsFuture.join();
    locations.sort(Comparator.comparing(LocationDto::name, String.CASE_INSENSITIVE_ORDER));

    List<ManufacturerDto> manufacturers = manufacturersFuture.join();
    manufacturers.sort(Comparator.comparing(ManufacturerDto::name, String.CASE_INSENSITIVE_ORDER));

    // Curated home locations for the bulk "set home location" picker. The backend already returns
    // them alphabetically descending (Z->A); preserve that order (do not re-sort).
    List<LocationDto> homeLocations = homeLocationsFuture.join();

    model.addAttribute("myShips", myShips);
    model.addAttribute("shipTypes", shipTypes);
    model.addAttribute("locations", locations);
    model.addAttribute("manufacturers", manufacturers);
    model.addAttribute("homeLocations", homeLocations);
    model.addAttribute("ownerOptions", fetchCallerMembershipOptions());

    return "hangar";
  }

  /**
   * Fetches the caller's OrgUnit memberships for the R5.d.f owner-picker on the add-ship modal.
   * Ships are always added for the calling user (no admin-cross-user override on this page), so the
   * picker reflects the caller's own memberships. Falls back to an empty list on backend hiccup;
   * the fragment collapses to its hidden state in that case.
   *
   * @return picker options or empty list; never {@code null}.
   */
  private List<OrgUnitMembershipOptionDto> fetchCallerMembershipOptions() {
    try {
      UserDto me = backendApiClient.get("/api/v1/users/me", UserDto.class);
      if (me == null || me.id() == null) {
        return List.of();
      }
      List<OrgUnitMembershipOptionDto> options =
          backendApiClient.get(
              "/api/v1/users/" + me.id() + "/memberships", new ParameterizedTypeReference<>() {});
      return options != null ? options : List.of();
    } catch (Exception e) {
      log.warn("Failed to fetch memberships for hangar add-ship owner-picker", e);
      return List.of();
    }
  }

  /**
   * Page sizes the squadron overview offers in its picker (REQ-HANGAR-001, same trio as the
   * blueprint availability overview). Any other client-supplied {@code size} is snapped back to
   * {@link #SQUADRON_DEFAULT_PAGE_SIZE} so a crafted URL cannot request an unbounded page from the
   * backend.
   */
  private static final List<Integer> SQUADRON_PAGE_SIZES = List.of(10, 50, 100);

  /** Page size applied when the request carries none (or a non-whitelisted one). */
  private static final int SQUADRON_DEFAULT_PAGE_SIZE = 50;

  /**
   * Renders the squadron-wide hangar overview ({@code /hangar/squadron}), server-side paginated
   * across every ship type in the caller's scope (REQ-HANGAR-001). The backend aggregates counts
   * per ship type, sorted by ship-type name; page metadata, the page-size choice (10/50/100 — any
   * other value snaps to the default) and the optional search term travel as query parameters and
   * are echoed into the model so the pagination links and the filter form can reproduce the state.
   *
   * @param page zero-based page index, defaults to the first page; negatives are clamped to 0
   * @param size page size, validated against {@link #SQUADRON_PAGE_SIZES}
   * @param search optional ship-type/manufacturer filter, applied server-side by the backend
   * @param fragment when {@code "results"}, only the results+pagination fragment is rendered for an
   *     in-place AJAX swap (epic #571 / REQ-FE-005); otherwise the full page
   * @param model Thymeleaf model populated with the overview page, the picker options and the
   *     pagination base URL (search-preserving)
   * @return the {@code hangar-squadron} view name, or its {@code squadronResults} fragment selector
   */
  @GetMapping("/squadron")
  public String viewSquadron(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String fragment,
      Model model) {
    int effectiveSize =
        size != null && SQUADRON_PAGE_SIZES.contains(size) ? size : SQUADRON_DEFAULT_PAGE_SIZE;
    int effectivePage = page == null || page < 0 ? 0 : page;
    String effectiveSearch = search == null || search.isBlank() ? null : search.trim();

    List<SquadronShipOverviewDto> overview = new ArrayList<>();
    PageResponse<SquadronShipOverviewDto> res = null;
    try {
      org.springframework.web.util.UriComponentsBuilder uriBuilder =
          org.springframework.web.util.UriComponentsBuilder.fromPath(
                  "/api/v1/hangar/squadron-overview")
              .queryParam("page", effectivePage)
              .queryParam("size", effectiveSize);
      if (effectiveSearch != null) {
        uriBuilder.queryParam("search", effectiveSearch);
      }
      res = backendApiClient.get(uriBuilder.toUriString(), new ParameterizedTypeReference<>() {});
      if (res != null && res.content() != null) {
        overview = new ArrayList<>(res.content());
      }
    } catch (Exception e) {
      log.error("Failed to fetch squadron overview", e);
      model.addAttribute("error", "error.hangar.squadron.load");
    }

    // Page links must keep the active filter, so the fragment's base URL carries the search term
    // percent-encoded (toUriString() encodes — a raw term could otherwise smuggle extra query
    // params into every pagination link); page/size are appended by the fragment itself.
    String paginationBaseUrl =
        effectiveSearch == null
            ? "/hangar/squadron"
            : org.springframework.web.util.UriComponentsBuilder.fromPath("/hangar/squadron")
                .queryParam("search", effectiveSearch)
                .toUriString();

    model.addAttribute("overview", overview);
    model.addAttribute("overviewPage", res);
    model.addAttribute("search", effectiveSearch);
    model.addAttribute("pageSizes", SQUADRON_PAGE_SIZES);
    model.addAttribute("pageSize", effectiveSize);
    model.addAttribute("paginationBaseUrl", paginationBaseUrl);
    if (fragment != null && "results".equalsIgnoreCase(fragment)) {
      return "hangar-squadron :: squadronResults";
    }
    return "hangar-squadron";
  }

  /**
   * Adds a new ship to the current user's hangar. Validation errors render the hangar view inline
   * (no redirect) so the BindingResult stays request-scoped — pushing it through the Redis-backed
   * FlashMap would crash on the self-referencing cycle.
   *
   * @param form ship form
   * @param bindingResult validation errors carrier
   * @param model Thymeleaf model used for inline re-rendering
   * @param redirectAttributes flash attributes carrier
   * @return inline {@code hangar} view on validation failure, otherwise redirect
   */
  @PostMapping("/add")
  public String addShip(
      @Valid @ModelAttribute("shipForm") ShipForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      // Render directly; the BindingResult stays request-scoped so it never goes
      // through a Redis-serialised FlashMap (see RedisSessionConfig).
      model.addAttribute("showShipModal", true);
      model.addAttribute("modalAction", "/hangar/add");
      return viewHangar(model);
    }

    try {
      ShipRequestDto request =
          new ShipRequestDto(
              form.getName(),
              form.getShipTypeId(),
              form.getInsurance(),
              form.getLocationId(),
              form.isFitted(),
              null,
              form.getOwningOrgUnitId());
      backendApiClient.post("/api/v1/hangar/ships", request, ShipDto.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.ship_add");
    } catch (Exception e) {
      log.error("Failed to add ship", e);
      log.error("Error adding ship", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.hangar.ship.add");
      redirectAttributes.addFlashAttribute("shipForm", form);
    }
    return "redirect:/hangar";
  }

  /**
   * Updates an existing ship. Optimistic-locking version travels through the form so the backend
   * can reject concurrent edits.
   *
   * @param id ship id
   * @param form ship form (carries the version field)
   * @param bindingResult validation errors carrier
   * @param model Thymeleaf model used for inline re-rendering
   * @param redirectAttributes flash attributes carrier
   * @return inline {@code hangar} view on validation failure, otherwise redirect
   */
  @PostMapping("/{id}/update")
  public String updateShip(
      @PathVariable @NotNull UUID id,
      @Valid @ModelAttribute("shipForm") ShipForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      log.warn("Validation failed for ship update {}", id);
      model.addAttribute("errorToast", "error.validation.failed");
      model.addAttribute("showShipModal", true);
      model.addAttribute("modalAction", "/hangar/" + id + "/update");
      return viewHangar(model);
    }

    try {
      ShipRequestDto request =
          new ShipRequestDto(
              form.getName(),
              form.getShipTypeId(),
              form.getInsurance(),
              form.getLocationId(),
              form.isFitted(),
              form.getVersion(),
              // Update path: owningOrgUnitId is not editable, the existing stamp survives.
              null);
      backendApiClient.put("/api/v1/hangar/ships/" + id, request, ShipDto.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.ship_update");
    } catch (Exception e) {
      log.error("Failed to update ship", e);
      log.error("Error updating ship", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.hangar.ship.update");
    }
    return "redirect:/hangar";
  }

  /**
   * Deletes a ship from the user's hangar.
   *
   * @param id ship id
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /hangar}
   */
  @PostMapping("/{id}/delete")
  public String deleteShip(@PathVariable @NotNull UUID id, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/hangar/ships/" + id, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.ship_delete");
    } catch (Exception e) {
      log.error("Failed to delete ship", e);
      log.error("Error deleting ship", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.hangar.ship.delete");
    }
    return "redirect:/hangar";
  }

  /**
   * Bulk-sets the chosen curated home location on every ship the calling user owns, then redirects
   * back to the hangar — a full reload that resyncs each row's displayed location and version. The
   * location id comes from the home-location modal's select; the backend derives the owner from the
   * JWT and validates that the id is a selectable home location.
   *
   * @param locationId the curated home location chosen in the modal
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /hangar}
   */
  @PostMapping("/home-location")
  public String setHomeLocation(
      @RequestParam("locationId") @NotNull UUID locationId, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.post(
          "/api/v1/hangar/ships/home-location",
          new SetHomeLocationRequestDto(locationId),
          Void.class);
      redirectAttributes.addFlashAttribute(
          "successToast", "notification.success.home_location_set");
    } catch (Exception e) {
      log.error("Failed to set home location for ships", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.hangar.home_location.set");
    }
    return "redirect:/hangar";
  }

  private Long parseLong(Object o) {
    if (o == null) {
      return null;
    }
    try {
      if (o instanceof Number) {
        return ((Number) o).longValue();
      }
      return Long.parseLong(o.toString());
    } catch (Exception e) {
      return null;
    }
  }

  private String parseString(Object o) {
    return o == null ? null : o.toString();
  }

  private UUID parseUuid(Object o) {
    if (o == null) {
      return null;
    }
    try {
      return UUID.fromString(o.toString());
    } catch (Exception e) {
      return null;
    }
  }
}

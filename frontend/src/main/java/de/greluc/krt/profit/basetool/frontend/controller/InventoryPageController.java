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

package de.greluc.krt.profit.basetool.frontend.controller;

import de.greluc.krt.profit.basetool.frontend.model.dto.AggregatedInventoryDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.BulkCheckoutRequest;
import de.greluc.krt.profit.basetool.frontend.model.dto.GroupedInventoryDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.InventoryItemBookOutDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.InventoryItemCreateDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.InventoryItemDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.InventoryItemNoteUpdateRequest;
import de.greluc.krt.profit.basetool.frontend.model.dto.InventoryItemUpdateDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.InventoryStackDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.OrgUnitMembershipOptionDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.profit.basetool.frontend.model.form.InventoryForm;
import de.greluc.krt.profit.basetool.frontend.service.BackendApiClient;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC controller for the inventory pages ({@code /inventory}, {@code /inventory/my}, {@code
 * /inventory/all}, {@code /inventory/input}, plus AJAX book-out / transfer / update-associations
 * endpoints).
 *
 * <p>Four read views: aggregated (sum per material across the squadron), per-material drilldown,
 * personal ({@code /my}), and admin-all ({@code /all}). All four list endpoints accept the same
 * filter dimensions (material ids, min quality, job order, mission) and support a {@code
 * fragment=true} flag that returns just the table fragment so AJAX filter changes do not reload the
 * page. Write paths cover create (input form), book-out (consume / transfer / sell), inline
 * transfer (AJAX, used by the material-collection page), and association update (re-bind to a
 * different mission/job-order).
 */
@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class InventoryPageController {

  private final BackendApiClient backendApiClient;

  /**
   * Renders the squadron-wide aggregated inventory view ({@code /inventory}). Sort is fixed to
   * material name asc, quality desc, amount desc — operators look for the highest-quality stock
   * first.
   *
   * @param page zero-based page index
   * @param size page size
   * @param fragment when {@code "results"}, only the results+pagination fragment is rendered for an
   *     in-place AJAX swap (epic #571 / REQ-FE-005); otherwise the full page
   * @param model Thymeleaf model populated with the page, aggregated items and material catalog
   * @return the {@code inventory-index} view name, or its {@code inventoryResults} fragment
   *     selector
   */
  @GetMapping
  public String viewAggregatedInventory(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String fragment,
      Model model) {
    List<AggregatedInventoryDto> aggregated = new ArrayList<>();
    try {
      StringBuilder uri = new StringBuilder("/api/v1/inventory/aggregated?");
      if (page != null) {
        uri.append("page=").append(page).append("&");
      }
      if (size != null) {
        uri.append("size=").append(size).append("&");
      }
      uri.append("sort=material.name,asc;quality,desc;amount,desc");

      PageResponse<AggregatedInventoryDto> p =
          backendApiClient.get(uri.toString(), new ParameterizedTypeReference<>() {});
      if (p != null) {
        if (p.content() != null) {
          aggregated = new ArrayList<>(p.content());
        }
        model.addAttribute("inventoryPage", p);
      }
    } catch (Exception e) {
      log.error("Failed to fetch aggregated inventory", e);
      model.addAttribute("error", "error.inventory.aggregate.load");
    }

    List<de.greluc.krt.profit.basetool.frontend.model.dto.MaterialReferenceDto> materials =
        fetchMaterials();

    model.addAttribute("aggregated", aggregated);
    model.addAttribute("materials", materials);
    if (fragment != null && "results".equalsIgnoreCase(fragment)) {
      return "inventory-index :: inventoryResults";
    }
    return "inventory-index";
  }

  /**
   * Renders the per-material drilldown ({@code /inventory/material/{materialId}}) showing every
   * individual inventory row for the given material (up to 1000 in one page). Loads the
   * active-job-order list because the page offers inline re-assignment of items to job orders.
   *
   * @param materialId material id to drill into
   * @param model Thymeleaf model populated with items, the material catalog and active job orders
   * @return the {@code inventory-material} view name
   */
  @GetMapping("/material/{materialId}")
  public String viewMaterialInventory(@PathVariable @NotNull UUID materialId, Model model) {
    List<InventoryItemDto> items = new ArrayList<>();
    try {
      PageResponse<InventoryItemDto> p =
          backendApiClient.get(
              "/api/v1/inventory/material/" + materialId + "?size=1000",
              new ParameterizedTypeReference<>() {});
      if (p != null && p.content() != null) {
        items = new ArrayList<>(p.content());
      }
    } catch (Exception e) {
      log.error("Failed to fetch material inventory", e);
      model.addAttribute("error", "error.inventory.material.load");
    }

    model.addAttribute("items", items);
    model.addAttribute("materials", fetchMaterials());
    model.addAttribute("selectedMaterialId", materialId);
    model.addAttribute("jobOrders", fetchActiveJobOrders());
    return "inventory-material";
  }

  /**
   * Per-material grouping wrapper for the {@code /my} and {@code /all} list views.
   *
   * <p>The backend's {@code /grouped} endpoint returns this shape directly so the page renders an
   * outer "material" row with summary stats (total amount, average + max quality) and an inner list
   * of {@link InventoryStackDto} stacks, each of which expands to the individual append-only
   * entries (Material → Stack → Entries).
   *
   * @param material the grouping material
   * @param totalAmount sum across all stacks of this material
   * @param averageQuality weighted average quality across all stacks
   * @param maxQuality the highest quality value seen in the group
   * @param stacks the per-stock-identity stacks this material breaks down into
   */
  public record GroupedInventoryDto(
      de.greluc.krt.profit.basetool.frontend.model.dto.MaterialReferenceDto material,
      Double totalAmount,
      Double averageQuality,
      Integer maxQuality,
      List<InventoryStackDto> stacks) {

    /**
     * Counts the distinct owning users across this material's stacks, for the grouped Lager row's
     * context line ("{n} Nutzer / {m} Stacks").
     *
     * @return the number of distinct users owning at least one stack of this material
     */
    public int userCount() {
      return (int)
          stacks.stream()
              .map(InventoryStackDto::user)
              .filter(java.util.Objects::nonNull)
              .map(u -> u.id())
              .distinct()
              .count();
    }
  }

  /**
   * Renders the personal inventory list ({@code /inventory/my}). Filters are URL-driven so a user
   * can share a filtered link. {@code fragment=true} returns just the table fragment for AJAX
   * filter changes.
   *
   * @param materialIds optional material id filter (multi)
   * @param minQuality optional minimum-quality filter
   * @param jobOrderIds optional job-order id filter (multi)
   * @param missionIds optional mission id filter (multi)
   * @param personalOnly when true, show only the caller's personal entries ({@code personal =
   *     true})
   * @param fragment when true, return the {@code inventoryTableFragment} fragment
   * @param model Thymeleaf model populated with grouped items, filter source catalogs and the
   *     auth-derived UX flags
   * @return either the full {@code inventory-my} view or its table fragment
   */
  @GetMapping("/my")
  public String viewMyInventory(
      @RequestParam(required = false) List<UUID> materialIds,
      @RequestParam(required = false) Integer minQuality,
      @RequestParam(required = false) List<UUID> jobOrderIds,
      @RequestParam(required = false) List<UUID> missionIds,
      @RequestParam(required = false, defaultValue = "false") boolean personalOnly,
      @RequestParam(required = false, defaultValue = "false") boolean fragment,
      Model model) {
    if (!model.containsAttribute("inventoryForm")) {
      model.addAttribute("inventoryForm", new InventoryForm());
    }
    if (!model.containsAttribute("inventoryBookOutForm")) {
      model.addAttribute(
          "inventoryBookOutForm",
          new de.greluc.krt.profit.basetool.frontend.model.form.InventoryBookOutForm());
    }

    List<GroupedInventoryDto> groupedItems = new ArrayList<>();
    try {
      org.springframework.web.util.UriComponentsBuilder uriBuilder =
          org.springframework.web.util.UriComponentsBuilder.fromPath(
              "/api/v1/inventory/my-inventory/grouped");
      if (materialIds != null && !materialIds.isEmpty()) {
        for (UUID id : materialIds) {
          uriBuilder.queryParam("materialIds", id.toString());
        }
      }
      if (minQuality != null) {
        uriBuilder.queryParam("minQuality", minQuality);
      }
      if (jobOrderIds != null && !jobOrderIds.isEmpty()) {
        for (UUID id : jobOrderIds) {
          uriBuilder.queryParam("jobOrderIds", id.toString());
        }
      }
      if (missionIds != null && !missionIds.isEmpty()) {
        for (UUID id : missionIds) {
          uriBuilder.queryParam("missionIds", id.toString());
        }
      }
      if (personalOnly) {
        uriBuilder.queryParam("personalOnly", true);
      }
      String url = uriBuilder.build().toUriString();
      List<GroupedInventoryDto> res =
          backendApiClient.get(url, new ParameterizedTypeReference<>() {});
      if (res != null) {
        groupedItems = res;
      }
    } catch (Exception e) {
      log.error("Failed to fetch my grouped inventory", e);
      model.addAttribute("error", "error.inventory.personal.load");
    }

    model.addAttribute("groupedItems", groupedItems);
    // keeping empty items list to not break any existing template iteration if any
    model.addAttribute("items", new ArrayList<>());
    model.addAttribute("materials", fetchMaterials());
    model.addAttribute("locations", fetchLocations());
    model.addAttribute("jobOrders", fetchActiveJobOrders());
    model.addAttribute("missions", fetchMissions());
    model.addAttribute("users", fetchUsers());
    model.addAttribute("selectedMaterialIds", materialIds);
    model.addAttribute("selectedMinQuality", minQuality);
    model.addAttribute("selectedJobOrderIds", jobOrderIds);
    model.addAttribute("selectedMissionIds", missionIds);
    model.addAttribute("selectedPersonalOnly", personalOnly);
    model.addAttribute("authUserId", currentAuthName());
    model.addAttribute("canEditForeignNotes", hasLogisticianOrAbove());

    if (fragment) {
      return "inventory-my :: inventoryTableFragment";
    }
    return "inventory-my";
  }

  /**
   * Renders the squadron-wide inventory list ({@code /inventory/all}). Same shape as {@link
   * #viewMyInventory} but the backend endpoint scopes to all users (gated by role at the backend).
   *
   * @param materialIds optional material id filter (multi)
   * @param minQuality optional minimum-quality filter
   * @param jobOrderIds optional job-order id filter (multi)
   * @param missionIds optional mission id filter (multi)
   * @param fragment when true, return the table fragment
   * @return either the full {@code inventory-admin} view or its fragment
   */
  @GetMapping("/all")
  public String viewAllInventory(
      @RequestParam(required = false) List<UUID> materialIds,
      @RequestParam(required = false) Integer minQuality,
      @RequestParam(required = false) List<UUID> jobOrderIds,
      @RequestParam(required = false) List<UUID> missionIds,
      @RequestParam(required = false, defaultValue = "false") boolean fragment,
      Model model) {
    if (!model.containsAttribute("inventoryForm")) {
      model.addAttribute("inventoryForm", new InventoryForm());
    }
    if (!model.containsAttribute("inventoryBookOutForm")) {
      model.addAttribute(
          "inventoryBookOutForm",
          new de.greluc.krt.profit.basetool.frontend.model.form.InventoryBookOutForm());
    }

    List<GroupedInventoryDto> groupedItems = new ArrayList<>();
    try {
      org.springframework.web.util.UriComponentsBuilder uriBuilder =
          org.springframework.web.util.UriComponentsBuilder.fromPath(
              "/api/v1/inventory/all/grouped");
      if (materialIds != null && !materialIds.isEmpty()) {
        for (UUID id : materialIds) {
          uriBuilder.queryParam("materialIds", id.toString());
        }
      }
      if (minQuality != null) {
        uriBuilder.queryParam("minQuality", minQuality);
      }
      if (jobOrderIds != null && !jobOrderIds.isEmpty()) {
        for (UUID id : jobOrderIds) {
          uriBuilder.queryParam("jobOrderIds", id.toString());
        }
      }
      if (missionIds != null && !missionIds.isEmpty()) {
        for (UUID id : missionIds) {
          uriBuilder.queryParam("missionIds", id.toString());
        }
      }
      String url = uriBuilder.build().toUriString();
      List<GroupedInventoryDto> res =
          backendApiClient.get(url, new ParameterizedTypeReference<>() {});
      if (res != null) {
        groupedItems = res;
      }
    } catch (Exception e) {
      log.error("Failed to fetch all grouped inventory", e);
      model.addAttribute("error", "error.inventory.global.load");
    }

    model.addAttribute("groupedItems", groupedItems);
    model.addAttribute("items", new ArrayList<>());
    model.addAttribute("materials", fetchMaterials());
    model.addAttribute("selectedMaterialIds", materialIds);
    model.addAttribute("selectedMinQuality", minQuality);
    model.addAttribute("selectedJobOrderIds", jobOrderIds);
    model.addAttribute("selectedMissionIds", missionIds);
    model.addAttribute("locations", fetchLocations());
    model.addAttribute("jobOrders", fetchActiveJobOrders());
    model.addAttribute("missions", fetchMissions());
    model.addAttribute("users", fetchUsers());
    model.addAttribute("authUserId", currentAuthName());
    model.addAttribute("canEditForeignNotes", hasLogisticianOrAbove());

    if (fragment) {
      return "inventory-admin :: inventoryTableFragment";
    }
    return "inventory-admin";
  }

  /**
   * Lazily renders one page of a personal-Lager stack's individual entries — the AJAX drill-down
   * behind a collapsed stack on {@code /inventory/my}. The append-only Lager keeps every
   * contribution as its own row, so the grouped view never inlines them; the browser expands a
   * stack and this endpoint fetches that stack's entries oldest-first, paginated, from the
   * backend's {@code /api/v1/inventory/my-inventory/stack/entries}. The stack is addressed by the
   * stock-identity query params the grouped {@link InventoryStackDto} already exposes (a {@code
   * null} job-order / mission / owning-org-unit selects the rows where that association is itself
   * absent). Returns the {@code stackEntries} HTML fragment that replaces the stack's entries
   * container.
   *
   * @param materialId the stack's material (from the enclosing group)
   * @param locationId the stack's storage location
   * @param quality the stack's quality grade, or {@code null}
   * @param jobOrderId the stack's linked job-order id, or {@code null} for the unassigned slice
   * @param missionId the stack's linked mission id, or {@code null} for the unassigned slice
   * @param personal whether the stack holds the caller's private stock
   * @param owningOrgUnitId the stack's owning org-unit pool, or {@code null}
   * @param page zero-based page index, or {@code null} for the first page
   * @param size page size, or {@code null} for the backend default
   * @param model Thymeleaf model populated with the entries page and association catalogs
   * @return the {@code inventory-my :: stackEntries} fragment view name
   */
  @GetMapping("/my/stack/entries")
  public String viewMyStackEntries(
      @RequestParam @NotNull UUID materialId,
      @RequestParam @NotNull UUID locationId,
      @RequestParam(required = false) Integer quality,
      @RequestParam(required = false) UUID jobOrderId,
      @RequestParam(required = false) UUID missionId,
      @RequestParam(required = false, defaultValue = "false") boolean personal,
      @RequestParam(required = false) UUID owningOrgUnitId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      Model model) {
    org.springframework.web.util.UriComponentsBuilder uriBuilder =
        org.springframework.web.util.UriComponentsBuilder.fromPath(
                "/api/v1/inventory/my-inventory/stack/entries")
            .queryParam("materialId", materialId)
            .queryParam("locationId", locationId)
            .queryParam("personal", personal);
    if (quality != null) {
      uriBuilder.queryParam("quality", quality);
    }
    if (jobOrderId != null) {
      uriBuilder.queryParam("jobOrderId", jobOrderId);
    }
    if (missionId != null) {
      uriBuilder.queryParam("missionId", missionId);
    }
    if (owningOrgUnitId != null) {
      uriBuilder.queryParam("owningOrgUnitId", owningOrgUnitId);
    }
    if (page != null) {
      uriBuilder.queryParam("page", page);
    }
    if (size != null) {
      uriBuilder.queryParam("size", size);
    }
    fetchStackEntriesIntoModel(uriBuilder.build().toUriString(), model);
    return "fragments/inventory-stack-entries :: stackEntriesMy";
  }

  /**
   * Squadron-wide variant of {@link #viewMyStackEntries} — the AJAX drill-down behind a collapsed
   * stack on {@code /inventory/all}. A global stack is per-owner, so the stack key carries the
   * owning {@code userId} in addition to the other stock-identity dimensions; the backend
   * re-applies the same org-unit scope predicate as the grouped view, so the drill-down can never
   * widen visibility beyond the caller's slice. The global Lager is non-personal by definition, so
   * there is no {@code personal} param. Returns the {@code stackEntries} HTML fragment for the
   * admin page.
   *
   * @param materialId the stack's material (from the enclosing group)
   * @param userId the stack's owning user
   * @param locationId the stack's storage location
   * @param quality the stack's quality grade, or {@code null}
   * @param jobOrderId the stack's linked job-order id, or {@code null} for the unassigned slice
   * @param missionId the stack's linked mission id, or {@code null} for the unassigned slice
   * @param owningOrgUnitId the stack's owning org-unit pool, or {@code null}
   * @param page zero-based page index, or {@code null} for the first page
   * @param size page size, or {@code null} for the backend default
   * @param model Thymeleaf model populated with the entries page and association catalogs
   * @return the {@code inventory-admin :: stackEntries} fragment view name
   */
  @GetMapping("/all/stack/entries")
  public String viewAllStackEntries(
      @RequestParam @NotNull UUID materialId,
      @RequestParam @NotNull UUID userId,
      @RequestParam @NotNull UUID locationId,
      @RequestParam(required = false) Integer quality,
      @RequestParam(required = false) UUID jobOrderId,
      @RequestParam(required = false) UUID missionId,
      @RequestParam(required = false) UUID owningOrgUnitId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      Model model) {
    org.springframework.web.util.UriComponentsBuilder uriBuilder =
        org.springframework.web.util.UriComponentsBuilder.fromPath(
                "/api/v1/inventory/all/stack/entries")
            .queryParam("materialId", materialId)
            .queryParam("userId", userId)
            .queryParam("locationId", locationId);
    if (quality != null) {
      uriBuilder.queryParam("quality", quality);
    }
    if (jobOrderId != null) {
      uriBuilder.queryParam("jobOrderId", jobOrderId);
    }
    if (missionId != null) {
      uriBuilder.queryParam("missionId", missionId);
    }
    if (owningOrgUnitId != null) {
      uriBuilder.queryParam("owningOrgUnitId", owningOrgUnitId);
    }
    if (page != null) {
      uriBuilder.queryParam("page", page);
    }
    if (size != null) {
      uriBuilder.queryParam("size", size);
    }
    fetchStackEntriesIntoModel(uriBuilder.build().toUriString(), model);
    return "fragments/inventory-stack-entries :: stackEntriesAdmin";
  }

  /**
   * Shared backend call + model population for the two stack-entries drill-down endpoints. Fetches
   * the paginated entries page from the given backend URI and the job-order / mission catalogs the
   * per-entry association dropdowns render. A backend failure degrades to an empty entries list
   * plus an {@code error} flag so the fragment still renders a (empty) container instead of
   * throwing.
   *
   * @param uri the fully-built backend stack-entries URI (path + query)
   * @param model the Thymeleaf model to populate with {@code entries}, {@code entriesPage}, {@code
   *     jobOrders} and {@code missions}
   */
  private void fetchStackEntriesIntoModel(@NotNull String uri, Model model) {
    PageResponse<InventoryItemDto> p = null;
    try {
      p = backendApiClient.get(uri, new ParameterizedTypeReference<>() {});
    } catch (Exception e) {
      log.error("Failed to fetch stack entries", e);
      model.addAttribute("error", "inventory.stack.entries.error");
    }
    List<InventoryItemDto> entries =
        (p != null && p.content() != null) ? new ArrayList<>(p.content()) : new ArrayList<>();
    model.addAttribute("entries", entries);
    model.addAttribute("entriesPage", p);
    model.addAttribute("jobOrders", fetchActiveJobOrders());
    model.addAttribute("missions", fetchMissions());
  }

  /**
   * Renders the inventory create form ({@code /inventory/input}). The {@code source=admin} mode
   * seeds {@code isGlobal=true} so the admin can pick a target user from the user dropdown;
   * otherwise the form creates a personal entry owned by the caller.
   *
   * @param source optional origin marker ({@code admin}, {@code my}, {@code aggregated}) used to
   *     pick the post-save redirect target
   * @param model Thymeleaf model populated with the form and dropdown catalogs
   * @return the {@code inventory-input} view name
   */
  @GetMapping("/input")
  public String viewInputPage(@RequestParam(required = false) String source, Model model) {
    InventoryForm form;
    if (!model.containsAttribute("inventoryForm")) {
      form = new InventoryForm();
      if ("admin".equals(source)) {
        form.setIsGlobal(true);
      } else {
        form.setIsGlobal(false);
      }
      form.setSource(source);
      model.addAttribute("inventoryForm", form);
    } else {
      form = (InventoryForm) model.getAttribute("inventoryForm");
    }

    if (form != null && Boolean.TRUE.equals(form.getIsGlobal())) {
      model.addAttribute("users", fetchUsers());
    }

    model.addAttribute("materials", fetchMaterials());
    model.addAttribute("locations", fetchLocations());
    model.addAttribute("missions", fetchMissions());
    model.addAttribute("jobOrders", fetchActiveJobOrders());
    model.addAttribute("ownerOptions", fetchOwnerPickerOptions(form));
    return "inventory-input";
  }

  /**
   * Resolves the {@link OrgUnitMembershipOptionDto} list that drives the R5.d owner-picker fragment
   * on the inventory-input form. The target user is:
   *
   * <ul>
   *   <li>The form's {@code userId} when the admin is creating a global entry for another user (the
   *       picker reflects the chosen user's memberships).
   *   <li>The calling user otherwise (a self-entry — picker reflects the caller's own memberships).
   * </ul>
   *
   * <p>Falling back to an empty list when the lookup fails keeps the page renderable: the fragment
   * collapses to a hidden state when its option list is empty, so a transient backend hiccup does
   * not break the rest of the form.
   *
   * @param form the inbound inventory form (may be {@code null} on first GET before binding).
   * @return picker options or empty list; never {@code null}.
   */
  private List<OrgUnitMembershipOptionDto> fetchOwnerPickerOptions(InventoryForm form) {
    if (form != null && Boolean.TRUE.equals(form.getIsGlobal()) && form.getUserId() != null) {
      // Admin creating a global entry for ANOTHER user → that user's DIRECT memberships (their own
      // stock). The create-on-behalf cascade is the caller's reach, not a third-party owner's.
      try {
        List<OrgUnitMembershipOptionDto> options =
            backendApiClient.get(
                "/api/v1/users/" + form.getUserId() + "/memberships",
                new ParameterizedTypeReference<>() {});
        return options != null ? options : List.of();
      } catch (Exception e) {
        log.warn("Failed to fetch memberships for owner-picker", e);
        return List.of();
      }
    }
    // Self-entry → the caller's pickable org units: direct memberships plus their cascading
    // leadership reach (own Bereich/OL + overseen subordinate Staffeln/SKs), epic #692 Phase 5.
    // Unchanged for an ordinary member. Resolved server-side for the caller.
    try {
      List<OrgUnitMembershipOptionDto> options =
          backendApiClient.get(
              "/api/v1/users/me/pickable-org-units", new ParameterizedTypeReference<>() {});
      return options != null ? options : List.of();
    } catch (Exception e) {
      log.warn("Failed to fetch pickable org units for owner-picker", e);
      return List.of();
    }
  }

  /**
   * Persists a new inventory item.
   *
   * <p>Enforces a cross-field invariant before the backend call: a personal entry cannot be
   * assigned to a job order or mission. Validation failure re-renders the input page inline so the
   * BindingResult stays request-scoped. On success, redirects to the page the user came from
   * (encoded in {@code source}).
   *
   * @param form inventory form
   * @param bindingResult validation errors carrier
   * @param model Thymeleaf model used for inline re-rendering
   * @param redirectAttributes flash attributes carrier
   * @return inline {@code inventory-input} view on failure, otherwise redirect to the source page
   */
  @PostMapping("/input")
  public String addInventoryItem(
      @Valid @ModelAttribute("inventoryForm") InventoryForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes) {
    if (Boolean.TRUE.equals(form.getPersonal())
        && (form.getJobOrderId() != null || form.getMissionId() != null)) {
      bindingResult.rejectValue(
          "personal",
          "error.inventory.personal.assignment",
          "Ein persönlicher Eintrag darf keinem Auftrag oder Einsatz zugeordnet sein.");
    }

    if (bindingResult.hasErrors()) {
      // Render directly; BindingResult stays request-scoped (see RedisSessionConfig).
      return viewInputPage(form.getSource(), model);
    }

    try {
      InventoryItemCreateDto request =
          new InventoryItemCreateDto(
              Boolean.TRUE.equals(form.getIsGlobal()) ? form.getUserId() : null,
              form.getMaterialId(),
              form.getLocationId(),
              form.getQuality(),
              form.getAmount(),
              form.getPersonal(),
              form.getMissionId(),
              form.getJobOrderId(),
              form.getOwningOrgUnitId());
      backendApiClient.post("/api/v1/inventory", request, InventoryItemDto.class);
      redirectAttributes.addFlashAttribute("successToast", "success.inventory.add");
    } catch (Exception e) {
      log.error("Failed to add inventory item", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.inventory.add.failed");
      redirectAttributes.addFlashAttribute("inventoryForm", form);
      return "redirect:/inventory/input";
    }

    if ("my".equals(form.getSource())) {
      return "redirect:/inventory/my";
    } else if ("admin".equals(form.getSource())) {
      return "redirect:/inventory/all";
    } else if ("aggregated".equals(form.getSource())) {
      return "redirect:/inventory";
    }

    return "redirect:/inventory";
  }

  /**
   * AJAX twin of {@link #addInventoryItem} (#577): books an item into the inventory in place.
   * Routed by the {@code X-Requested-With} header (the classic {@code POST /inventory/input}
   * redirect stays the no-JavaScript fallback). The form is multipart/form-encoded (the create page
   * has the SCU-decimal amount + the owner-picker), so it binds the same {@code @ModelAttribute}
   * the classic handler does. On success it returns the source listing URL the client navigates to
   * (navigate-after-AJAX, REQ-FE-006 — book-in is a standalone page with no local list to patch); a
   * cross-field or bind error returns {@code 422} {@code problem+json} with a {@code code} the
   * client maps to an inline toast, and a backend failure is propagated as {@code problem+json}.
   *
   * @param form the bound inventory form
   * @param bindingResult the binding/validation result
   * @return {@code 200} with {@code {targetUrl}}, {@code 422} on validation, or the propagated
   *     error
   */
  @PostMapping(value = "/input", headers = "X-Requested-With=XMLHttpRequest")
  @ResponseBody
  public org.springframework.http.ResponseEntity<Object> addInventoryItemAjax(
      @Valid @ModelAttribute("inventoryForm") InventoryForm form, BindingResult bindingResult) {
    if (Boolean.TRUE.equals(form.getPersonal())
        && (form.getJobOrderId() != null || form.getMissionId() != null)) {
      return inventoryValidationError("INVENTORY_PERSONAL_ASSIGNMENT");
    }
    if (bindingResult.hasErrors()) {
      return inventoryValidationError("VALIDATION");
    }
    try {
      InventoryItemCreateDto request =
          new InventoryItemCreateDto(
              Boolean.TRUE.equals(form.getIsGlobal()) ? form.getUserId() : null,
              form.getMaterialId(),
              form.getLocationId(),
              form.getQuality(),
              form.getAmount(),
              form.getPersonal(),
              form.getMissionId(),
              form.getJobOrderId(),
              form.getOwningOrgUnitId());
      backendApiClient.post("/api/v1/inventory", request, InventoryItemDto.class);
      return org.springframework.http.ResponseEntity.ok(
          java.util.Map.of("targetUrl", inventorySourceTarget(form.getSource())));
    } catch (de.greluc.krt.profit.basetool.frontend.service.BackendServiceException e) {
      log.error("Failed to add inventory item (ajax): {}", e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.error("Failed to add inventory item (ajax)", e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Maps the create form's {@code source} marker to the listing the client navigates to after an
   * in-place book-in, mirroring the redirect targets of the classic {@link #addInventoryItem}.
   *
   * @param source the form's {@code source} field ({@code my} / {@code admin} / {@code aggregated})
   * @return the same-origin listing path
   */
  private static String inventorySourceTarget(String source) {
    if ("my".equals(source)) {
      return "/inventory/my";
    }
    if ("admin".equals(source)) {
      return "/inventory/all";
    }
    return "/inventory";
  }

  /**
   * Builds a {@code 422} {@code problem+json} response carrying a stable {@code code} the inventory
   * pages map to a localized inline toast — used by {@link #addInventoryItemAjax} for the
   * server-side cross-field rule (a personal entry cannot carry an order/mission) and for a bind
   * failure, so the create page surfaces the error without a navigation.
   *
   * @param code the validation code ({@code INVENTORY_PERSONAL_ASSIGNMENT} / {@code VALIDATION})
   * @return a {@code 422} {@code problem+json} response
   */
  private static org.springframework.http.ResponseEntity<Object> inventoryValidationError(
      String code) {
    java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("status", 422);
    body.put("code", code);
    return org.springframework.http.ResponseEntity.unprocessableContent()
        .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
        .body(body);
  }

  /**
   * Translates a {@link de.greluc.krt.profit.basetool.frontend.service.BackendServiceException}
   * into an RFC 7807 {@code problem+json} {@link org.springframework.http.ResponseEntity},
   * preserving the backend status, {@code code} (e.g. {@code OPTIMISTIC_LOCK}), {@code detail} and
   * correlation id so the client's {@code krtFetch.handleProblem} can drive the conflict
   * reload-confirm or an error toast. Mirrors the helper in the mission / job-order / operation
   * controllers.
   *
   * @param e the backend failure to relay
   * @return a {@code problem+json} response carrying the backend status and code
   */
  private static org.springframework.http.ResponseEntity<Object> propagateBackendError(
      de.greluc.krt.profit.basetool.frontend.service.BackendServiceException e) {
    java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("status", e.getStatusCode());
    body.put("code", e.getProblemCode());
    if (e.getProblemDetail() != null && !e.getProblemDetail().isBlank()) {
      body.put("detail", e.getProblemDetail());
    }
    if (e.getCorrelationId() != null && !e.getCorrelationId().isBlank()) {
      body.put("correlationId", e.getCorrelationId());
    }
    return org.springframework.http.ResponseEntity.status(e.getStatusCode())
        .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
        .body(body);
  }

  /**
   * Books out an inventory item (consume / transfer / sell). The {@code type} field on the form
   * selects the operation; the backend computes the resulting state changes (decrement, transfer to
   * another user/location, or sell with terminal + price).
   *
   * <p>The redirect target preserves filter query parameters from the {@code Referer} header (see
   * {@link #buildInventoryRedirectFromReferer}) so the user does not lose their active filters
   * after a successful book-out. Validation failures drop the filter state and re-render the
   * originating listing inline — acceptable for a rare validation path.
   *
   * @param id inventory item id
   * @param form book-out form
   * @param bindingResult validation errors carrier
   * @param model Thymeleaf model used for inline re-rendering on validation failure
   * @param redirectAttributes flash attributes carrier
   * @param referer browser-supplied origin URL, used to derive the redirect target and the
   *     admin-vs-my detection
   * @return inline rerendered listing on failure, otherwise redirect preserving filters
   */
  @PostMapping("/{id}/book-out")
  public String bookOutInventoryItem(
      @PathVariable @NotNull UUID id,
      @Valid @ModelAttribute("inventoryBookOutForm")
          de.greluc.krt.profit.basetool.frontend.model.form.InventoryBookOutForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes,
      @RequestHeader(value = "Referer", required = false) String referer) {
    boolean fromAdminListing = referer != null && referer.contains("/inventory/all");
    String basePath = fromAdminListing ? "/inventory/all" : "/inventory/my";
    String redirectPath = buildInventoryRedirectFromReferer(basePath, referer);

    if (bindingResult.hasErrors()) {
      // Render the originating listing directly so the BindingResult stays
      // request-scoped (see RedisSessionConfig). The user-side trade-off is that
      // the filter state from the referer URL is dropped — acceptable for a rare
      // validation error path; the modal re-opens with the input + field errors.
      model.addAttribute("errorToast", "error.validation.failed");
      model.addAttribute("showBookOutModal", id);
      if (fromAdminListing) {
        return viewAllInventory(null, null, null, null, false, model);
      }
      return viewMyInventory(null, null, null, null, false, false, model);
    }

    try {
      InventoryItemBookOutDto request =
          new InventoryItemBookOutDto(
              form.getAmount(),
              form.getTargetUserId(),
              form.getTargetLocationId(),
              form.getType(),
              form.getTerminal(),
              form.getSellAmount(),
              form.getVersion(),
              form.getTargetOwningOrgUnitId());
      backendApiClient.post("/api/v1/inventory/" + id + "/book-out", request, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "success.inventory.bookout");
    } catch (Exception e) {
      log.error("Failed to book out inventory item", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.inventory.bookout.failed");
    }
    return "redirect:" + redirectPath;
  }

  /**
   * Builds a redirect target for inventory list views that preserves the filter query parameters
   * (e.g. {@code materialIds}, {@code minQuality}, {@code jobOrderIds}, {@code missionIds}, {@code
   * page}, {@code size}, {@code sort}) taken from the given Referer URL. This is the single source
   * of truth for filter state (URL-based) and guarantees that users keep their active filters after
   * write actions such as book-out / transfer / sell.
   *
   * <p>If the referer is empty, not parseable, or contains no query string, the plain base path is
   * returned. The {@code fragment} parameter is intentionally stripped since the redirect always
   * targets the full page view.
   */
  @org.jetbrains.annotations.NotNull
  static String buildInventoryRedirectFromReferer(
      @org.jetbrains.annotations.NotNull String basePath,
      @org.jetbrains.annotations.Nullable String referer) {
    if (referer == null || referer.isBlank()) {
      return basePath;
    }
    String query;
    try {
      java.net.URI uri = java.net.URI.create(referer);
      query = uri.getRawQuery();
    } catch (IllegalArgumentException ex) {
      return basePath;
    }
    if (query == null || query.isBlank()) {
      return basePath;
    }
    StringBuilder rebuilt = new StringBuilder();
    for (String raw : query.split("&")) {
      if (raw.isEmpty()) {
        continue;
      }
      int eq = raw.indexOf('=');
      String name = eq < 0 ? raw : raw.substring(0, eq);
      if (name.isEmpty() || "fragment".equals(name)) {
        continue;
      }
      if (!rebuilt.isEmpty()) {
        rebuilt.append('&');
      }
      rebuilt.append(raw);
    }
    if (rebuilt.isEmpty()) {
      return basePath;
    }
    return basePath + "?" + rebuilt;
  }

  /**
   * AJAX endpoint that proxies a transfer (owner or location change) for an inventory item to the
   * backend book-out endpoint. Used by the material collection page for inline reassignment.
   */
  @PostMapping("/{id}/transfer")
  @ResponseBody
  public org.springframework.http.ResponseEntity<Object> transferInventoryItem(
      @PathVariable @NotNull UUID id, @RequestBody @Valid InventoryItemBookOutDto dto) {
    try {
      InventoryItemDto result =
          backendApiClient.post(
              "/api/v1/inventory/" + id + "/book-out", dto, InventoryItemDto.class);
      if (result == null) {
        // Item was fully consumed (deleted) – return 204 so the frontend can reload
        return org.springframework.http.ResponseEntity.noContent().build();
      }
      return org.springframework.http.ResponseEntity.ok(result);
    } catch (de.greluc.krt.profit.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "Failed to transfer inventory item: status={}, {}", e.getStatusCode(), e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.error("Failed to transfer inventory item", e);
      return org.springframework.http.ResponseEntity.status(500).build();
    }
  }

  /**
   * AJAX proxy for the bulk book-out of several fully-consumed inventory items in one call (#577,
   * part 2). The personal-inventory page collects the checked item ids and posts them here; this
   * forwards to the backend {@code POST /api/v1/inventory/bulk-checkout} (which discards each item
   * in full and enforces per-item ownership). It replaces the page's former direct browser call to
   * the backend {@code /api/v1/...} path, which had no matching frontend route — so the bulk action
   * never reached the backend. On a backend failure the {@code problem+json} is propagated (so the
   * client's {@code krtFetch.handleProblem} can drive the optimistic-lock reload-confirm or an
   * error toast); on success {@code 204} lets the page re-swap the grouped table in place rather
   * than reload.
   *
   * @param request the ids of the owned items to book out
   * @return {@code 204} on success, otherwise the propagated backend error
   */
  @PostMapping("/bulk-checkout")
  @ResponseBody
  public org.springframework.http.ResponseEntity<Object> bulkCheckout(
      @RequestBody BulkCheckoutRequest request) {
    // Guard the empty/missing id list here (rather than via @Valid, which the frontend
    // GlobalExceptionHandler would surface as a 500) so the page gets a clean 422 problem+json —
    // mirroring addInventoryItemAjax's manual validation. The page already blocks an empty
    // selection client-side; the backend re-validates per item.
    if (request == null || request.itemIds() == null || request.itemIds().isEmpty()) {
      return inventoryValidationError("VALIDATION");
    }
    try {
      backendApiClient.post("/api/v1/inventory/bulk-checkout", request, Void.class);
      return org.springframework.http.ResponseEntity.noContent().build();
    } catch (de.greluc.krt.profit.basetool.frontend.service.BackendServiceException e) {
      log.error("Failed to bulk-checkout inventory items (ajax): {}", e.getMessage());
      return propagateBackendError(e);
    } catch (Exception e) {
      log.error("Failed to bulk-checkout inventory items (ajax)", e);
      return org.springframework.http.ResponseEntity.internalServerError().build();
    }
  }

  /**
   * AJAX endpoint that updates the soft associations of an inventory item (mission, job order,
   * note). Distinct from {@link #transferInventoryItem} — this is a metadata-only update, no
   * quantity changes. Propagates the backend's status code verbatim so the AJAX layer can map a 409
   * to a dedicated optimistic-lock toast.
   *
   * @param id inventory item id
   * @param dto update payload
   * @return the updated item on success, propagated backend status on failure
   */
  @PutMapping("/{id}/update-associations")
  @ResponseBody
  public org.springframework.http.ResponseEntity<InventoryItemDto> updateAssociations(
      @PathVariable @NotNull UUID id, @RequestBody @Valid InventoryItemUpdateDto dto) {
    try {
      InventoryItemDto updated =
          backendApiClient.put("/api/v1/inventory/" + id, dto, InventoryItemDto.class);
      return org.springframework.http.ResponseEntity.ok(updated);
    } catch (de.greluc.krt.profit.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "Failed to update inventory item associations: status={}, {}",
          e.getStatusCode(),
          e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
      log.error("Failed to update inventory item associations: {}", e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error("Failed to update inventory item associations", e);
      return org.springframework.http.ResponseEntity.status(500).build();
    }
  }

  /**
   * AJAX endpoint that proxies a note update (add/edit/remove) for an inventory item to the
   * backend. Authorisation is enforced by the backend (owner or {@code LOGISTICIAN}/{@code
   * OFFICER}/ {@code ADMIN} via role hierarchy). A blank or empty {@code note} removes the note. On
   * success, returns the updated {@link InventoryItemDto} (including the incremented version) so
   * the frontend can synchronize {@code data-version} DOM attributes.
   */
  @PutMapping("/{id}/note")
  @ResponseBody
  public org.springframework.http.ResponseEntity<InventoryItemDto> updateInventoryItemNote(
      @PathVariable @NotNull UUID id, @RequestBody @Valid InventoryItemNoteUpdateRequest request) {
    try {
      InventoryItemDto updated =
          backendApiClient.put(
              "/api/v1/inventory/" + id + "/note", request, InventoryItemDto.class);
      return org.springframework.http.ResponseEntity.ok(updated);
    } catch (de.greluc.krt.profit.basetool.frontend.service.BackendServiceException e) {
      // Propagate backend status (e.g. 409 Conflict from Optimistic Locking, 400 Validation,
      // 403 Forbidden) to the browser instead of masking it as 500, so the JS note modal
      // can react appropriately (toast + reload on 409).
      log.error(
          "Failed to update inventory item note: status={}, {}", e.getStatusCode(), e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
      log.error("Failed to update inventory item note: {}", e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error("Failed to update inventory item note", e);
      return org.springframework.http.ResponseEntity.status(500).build();
    }
  }

  /**
   * AJAX endpoint that proxies a delivered-status update for an inventory item to the backend. On
   * success, returns the updated {@link InventoryItemDto} (including the incremented version) so
   * the frontend can synchronize {@code data-version} DOM attributes.
   */
  @PatchMapping("/{id}/delivered")
  @ResponseBody
  public org.springframework.http.ResponseEntity<Object> updateDelivered(
      @PathVariable @NotNull UUID id,
      @RequestBody @Valid
          de.greluc.krt.profit.basetool.frontend.model.dto.UpdateDeliveredRequest request) {
    try {
      InventoryItemDto updated =
          backendApiClient.patch(
              "/api/v1/inventory/" + id + "/delivered", request, InventoryItemDto.class);
      return org.springframework.http.ResponseEntity.ok(updated);
    } catch (de.greluc.krt.profit.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "Failed to update delivered status: status={}, {}", e.getStatusCode(), e.getMessage());
      return propagateBackendError(e);
    } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
      log.error("Failed to update delivered status: {}", e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error("Failed to update delivered status", e);
      return org.springframework.http.ResponseEntity.status(500).build();
    }
  }

  private List<de.greluc.krt.profit.basetool.frontend.model.dto.UserReferenceDto> fetchUsers() {
    try {
      List<de.greluc.krt.profit.basetool.frontend.model.dto.UserReferenceDto> content =
          backendApiClient.get("/api/v1/users/lookup", new ParameterizedTypeReference<>() {});
      if (content != null) {
        return content;
      }
    } catch (Exception e) {
      log.warn("Failed to fetch users (might not be an admin/officer)");
    }
    return new ArrayList<>();
  }

  private List<de.greluc.krt.profit.basetool.frontend.model.dto.MaterialReferenceDto>
      fetchMaterials() {
    List<de.greluc.krt.profit.basetool.frontend.model.dto.MaterialReferenceDto> materials =
        new ArrayList<>();
    try {
      List<de.greluc.krt.profit.basetool.frontend.model.dto.MaterialReferenceDto> content =
          backendApiClient.getCached(
              "/api/v1/materials/lookup", new ParameterizedTypeReference<>() {});
      if (content != null) {
        materials.addAll(content);
      }
    } catch (Exception e) {
      log.error("Failed to fetch materials", e);
    }
    return materials;
  }

  private List<de.greluc.krt.profit.basetool.frontend.model.dto.LocationReferenceDto>
      fetchLocations() {
    List<de.greluc.krt.profit.basetool.frontend.model.dto.LocationReferenceDto> locations =
        new ArrayList<>();
    try {
      List<de.greluc.krt.profit.basetool.frontend.model.dto.LocationReferenceDto> content =
          backendApiClient.getCached(
              "/api/v1/locations/lookup", new ParameterizedTypeReference<>() {});
      if (content != null) {
        locations.addAll(content);
      }
    } catch (Exception e) {
      log.error("Failed to fetch locations", e);
    }
    return locations;
  }

  private List<de.greluc.krt.profit.basetool.frontend.model.dto.JobOrderReferenceDto>
      fetchActiveJobOrders() {
    List<de.greluc.krt.profit.basetool.frontend.model.dto.JobOrderReferenceDto> orders =
        new ArrayList<>();
    try {
      List<de.greluc.krt.profit.basetool.frontend.model.dto.JobOrderReferenceDto> content =
          backendApiClient.get("/api/v1/orders/lookup", new ParameterizedTypeReference<>() {});
      if (content != null) {
        orders.addAll(content);
      }
    } catch (Exception e) {
      log.error("Failed to fetch active job orders", e);
    }
    return orders;
  }

  private List<de.greluc.krt.profit.basetool.frontend.model.dto.MissionReferenceDto>
      fetchMissions() {
    try {
      List<de.greluc.krt.profit.basetool.frontend.model.dto.MissionReferenceDto> content =
          backendApiClient.get("/api/v1/missions/lookup", new ParameterizedTypeReference<>() {});
      if (content != null) {
        return content;
      }
    } catch (Exception e) {
      log.error("Failed to fetch missions", e);
    }
    return new ArrayList<>();
  }

  private static String currentAuthName() {
    org.springframework.security.core.Authentication auth =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    return auth != null ? auth.getName() : null;
  }

  private static boolean hasLogisticianOrAbove() {
    org.springframework.security.core.Authentication auth =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    if (auth == null || auth.getAuthorities() == null) {
      return false;
    }
    for (org.springframework.security.core.GrantedAuthority a : auth.getAuthorities()) {
      String r = a.getAuthority();
      if ("ROLE_LOGISTICIAN".equals(r) || "ROLE_OFFICER".equals(r) || "ROLE_ADMIN".equals(r)) {
        return true;
      }
    }
    return false;
  }
}

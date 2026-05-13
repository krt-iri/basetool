package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.AggregatedInventoryDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.GroupedInventoryDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.InventoryItemBookOutDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.InventoryItemCreateDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.InventoryItemDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.InventoryItemNoteUpdateRequest;
import de.greluc.krt.iri.basetool.frontend.model.dto.InventoryItemUpdateDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.form.InventoryForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
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
   * @param model Thymeleaf model populated with the page, aggregated items and material catalog
   * @return the {@code inventory-index} view name
   */
  @GetMapping
  public String viewAggregatedInventory(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
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

    List<de.greluc.krt.iri.basetool.frontend.model.dto.MaterialReferenceDto> materials =
        fetchMaterials();

    model.addAttribute("aggregated", aggregated);
    model.addAttribute("materials", materials);
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
   * of the underlying inventory items for expansion.
   *
   * @param material the grouping material
   * @param totalAmount sum across all items
   * @param averageQuality weighted average quality across all items
   * @param maxQuality the highest quality value seen in the group
   * @param items the underlying individual inventory rows
   */
  public record GroupedInventoryDto(
      de.greluc.krt.iri.basetool.frontend.model.dto.MaterialReferenceDto material,
      Double totalAmount,
      Double averageQuality,
      Integer maxQuality,
      List<InventoryItemDto> items) {}

  /**
   * Renders the personal inventory list ({@code /inventory/my}). Filters are URL-driven so a user
   * can share a filtered link. {@code fragment=true} returns just the table fragment for AJAX
   * filter changes.
   *
   * @param materialIds optional material id filter (multi)
   * @param minQuality optional minimum-quality filter
   * @param jobOrderIds optional job-order id filter (multi)
   * @param missionIds optional mission id filter (multi)
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
      @RequestParam(required = false, defaultValue = "false") boolean fragment,
      Model model) {
    if (!model.containsAttribute("inventoryForm")) {
      model.addAttribute("inventoryForm", new InventoryForm());
    }
    if (!model.containsAttribute("inventoryBookOutForm")) {
      model.addAttribute(
          "inventoryBookOutForm",
          new de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm());
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
          new de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm());
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
    return "inventory-input";
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
              form.getJobOrderId());
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
          de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm form,
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
      return viewMyInventory(null, null, null, null, false, model);
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
              form.getVersion());
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
  public org.springframework.http.ResponseEntity<InventoryItemDto> transferInventoryItem(
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
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "Failed to transfer inventory item: status={}, {}", e.getStatusCode(), e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error("Failed to transfer inventory item", e);
      return org.springframework.http.ResponseEntity.status(500).build();
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
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
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
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
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
  public org.springframework.http.ResponseEntity<InventoryItemDto> updateDelivered(
      @PathVariable @NotNull UUID id,
      @RequestBody @Valid
          de.greluc.krt.iri.basetool.frontend.model.dto.UpdateDeliveredRequest request) {
    try {
      InventoryItemDto updated =
          backendApiClient.patch(
              "/api/v1/inventory/" + id + "/delivered", request, InventoryItemDto.class);
      return org.springframework.http.ResponseEntity.ok(updated);
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error(
          "Failed to update delivered status: status={}, {}", e.getStatusCode(), e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
      log.error("Failed to update delivered status: {}", e.getMessage());
      return org.springframework.http.ResponseEntity.status(e.getStatusCode()).build();
    } catch (Exception e) {
      log.error("Failed to update delivered status", e);
      return org.springframework.http.ResponseEntity.status(500).build();
    }
  }

  private List<de.greluc.krt.iri.basetool.frontend.model.dto.UserReferenceDto> fetchUsers() {
    try {
      List<de.greluc.krt.iri.basetool.frontend.model.dto.UserReferenceDto> content =
          backendApiClient.get("/api/v1/users/lookup", new ParameterizedTypeReference<>() {});
      if (content != null) {
        return content;
      }
    } catch (Exception e) {
      log.warn("Failed to fetch users (might not be an admin/officer)");
    }
    return new ArrayList<>();
  }

  private List<de.greluc.krt.iri.basetool.frontend.model.dto.MaterialReferenceDto>
      fetchMaterials() {
    List<de.greluc.krt.iri.basetool.frontend.model.dto.MaterialReferenceDto> materials =
        new ArrayList<>();
    try {
      List<de.greluc.krt.iri.basetool.frontend.model.dto.MaterialReferenceDto> content =
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

  private List<de.greluc.krt.iri.basetool.frontend.model.dto.LocationReferenceDto>
      fetchLocations() {
    List<de.greluc.krt.iri.basetool.frontend.model.dto.LocationReferenceDto> locations =
        new ArrayList<>();
    try {
      List<de.greluc.krt.iri.basetool.frontend.model.dto.LocationReferenceDto> content =
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

  private List<de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderReferenceDto>
      fetchActiveJobOrders() {
    List<de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderReferenceDto> orders =
        new ArrayList<>();
    try {
      List<de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderReferenceDto> content =
          backendApiClient.get("/api/v1/orders/lookup", new ParameterizedTypeReference<>() {});
      if (content != null) {
        orders.addAll(content);
      }
    } catch (Exception e) {
      log.error("Failed to fetch active job orders", e);
    }
    return orders;
  }

  private List<de.greluc.krt.iri.basetool.frontend.model.dto.MissionReferenceDto> fetchMissions() {
    try {
      List<de.greluc.krt.iri.basetool.frontend.model.dto.MissionReferenceDto> content =
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

  private String parseString(Object o) {
    return o == null ? null : o.toString();
  }

  private Integer parseInteger(Object o) {
    if (o == null) {
      return null;
    }
    if (o instanceof Integer i) {
      return i;
    }
    if (o instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(o.toString());
    } catch (Exception e) {
      return null;
    }
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

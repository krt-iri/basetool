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

import de.greluc.krt.iri.basetool.frontend.model.dto.BlueprintReferenceDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.ClaimDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.CreateClaimDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.CreateJobOrderDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.CreateJobOrderItemLineDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.CreateJobOrderItemMaterialDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.CreateJobOrderItemRequestDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.CreateJobOrderMaterialDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.GameItemReferenceDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.InventoryItemDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.ItemDerivationDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderHandoverCreateDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderHandoverDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderHandoverItemCreateDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderItemBlueprintOwnersDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderItemDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderItemHandoverCreateDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderItemHandoverDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderItemHandoverEntryCreateDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OrgUnitMembershipOptionDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.SquadronDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.SystemSettingDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.UpdateJobOrderStatusDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.frontend.model.form.JobOrderForm;
import de.greluc.krt.iri.basetool.frontend.model.form.JobOrderHandoverForm;
import de.greluc.krt.iri.basetool.frontend.model.form.JobOrderItemForm;
import de.greluc.krt.iri.basetool.frontend.model.form.JobOrderItemHandoverForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC controller for the job-order pages ({@code /orders}, {@code /orders/{id}}, {@code
 * /orders/create} plus a number of POST-only mutators).
 *
 * <p>Job orders are the central work unit of the logistics workflow: created by squadron members,
 * assigned to logisticians, materialized via inventory handovers, and finally completed. The
 * controller covers the entire lifecycle plus the priority/status mutations, the assignee
 * management, the handover creation flow, and the unlink endpoints for materials and inventory
 * items. The status filter on the list view is persisted in a 30-day cookie so a user keeps their
 * preferred filter across sessions.
 */
@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
public class JobOrderPageController {

  private final BackendApiClient backendApiClient;
  private final RoleHierarchy roleHierarchy;

  private static final List<String> VALID_STATUSES =
      List.of("OPEN", "IN_PROGRESS", "REJECTED", "COMPLETED");

  /**
   * Accepted values for the orders-index squadron-scope toggle (MULTI_SQUADRON_PLAN.md section
   * 5.3). {@code mine} restricts to orders whose creating OR requesting squadron equals the
   * caller's active squadron; {@code all} returns the cross-staffel union (the backend's natural
   * behaviour for Job Orders). Persisted in the {@code orders_filter_scope} cookie alongside the
   * status filter.
   */
  private static final List<String> VALID_SQUADRON_SCOPES = List.of("mine", "all");

  /**
   * Renders the job-order list ({@code /orders}). Two persisted filters drive the view:
   *
   * <ul>
   *   <li>Status filter — three-stage precedence: explicit query parameter wins, otherwise the
   *       {@code orders_filter_status} cookie (validated against {@link #VALID_STATUSES}),
   *       otherwise the default of {@code OPEN} + {@code IN_PROGRESS}.
   *   <li>Squadron scope toggle — explicit {@code scope} query parameter wins ({@code mine} = only
   *       orders whose creating OR requesting squadron equals the caller's active squadron, {@code
   *       all} = cross-staffel union), otherwise the {@code orders_filter_scope} cookie, otherwise
   *       the plan-mandated default of {@code mine} for users with an active squadron context
   *       (MULTI_SQUADRON_PLAN.md section 5.3). Callers with no active context (admins in "all
   *       squadrons" mode, anonymous) collapse silently to {@code all} regardless of the requested
   *       scope.
   * </ul>
   *
   * <p>Cookie updates: both filters write a 30-day cookie scoped to {@code /orders} whenever the
   * user changes them explicitly. Re-rendering the page without an explicit value preserves the
   * cookie untouched so back/forward navigation keeps the persisted state.
   *
   * @param status optional explicit status filter
   * @param scope optional explicit squadron-scope filter ({@code mine}/{@code all})
   * @param cookieStatus previous persisted status filter from the cookie
   * @param cookieScope previous persisted squadron-scope filter from the cookie
   * @param activeSquadronId active squadron context surfaced by {@code SquadronContextAdvice}; used
   *     to translate {@code scope=mine} into a backend {@code squadronId} param.
   * @param response servlet response, used to update the persistence cookies
   * @param fragment when {@code "results"}, only the results-table fragment is rendered for an
   *     in-place AJAX swap (epic #571 / REQ-FE-005); otherwise the full page
   * @param model Thymeleaf model populated with orders, selected filters and the aging thresholds
   *     for the row-color rendering
   * @return the {@code orders-index} view name, or its {@code ordersResults} fragment selector
   */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public String viewOrders(
      @RequestParam(required = false) List<String> status,
      @RequestParam(required = false) String scope,
      @CookieValue(name = "orders_filter_status", required = false) String cookieStatus,
      @CookieValue(name = "orders_filter_scope", required = false) String cookieScope,
      @ModelAttribute("activeSquadronId") UUID activeSquadronId,
      @ModelAttribute("canViewJobOrders") boolean canViewJobOrders,
      HttpServletResponse response,
      @RequestParam(required = false) String fragment,
      Model model) {
    if (!canViewJobOrders) {
      // Non-profit members (anyone without a profit-eligible org unit, and not an admin) are not
      // part of the order workflow: they may place orders but not browse the queue. Route them to
      // the create form — the only order surface open to them — mirroring the anonymous "submit but
      // don't track" flow. The backend list returns empty for them regardless; this is the UX.
      return "redirect:/orders/create";
    }
    if (status == null || status.isEmpty()) {
      if (cookieStatus != null && !cookieStatus.isBlank()) {
        List<String> parsed = Arrays.asList(cookieStatus.split("-"));
        boolean allValid = parsed.stream().allMatch(VALID_STATUSES::contains);
        if (allValid) {
          status = parsed;
        } else {
          status = List.of("OPEN", "IN_PROGRESS");
        }
      } else {
        status = List.of("OPEN", "IN_PROGRESS");
      }
    } else {
      Cookie cookie = new Cookie("orders_filter_status", String.join("-", status));
      cookie.setPath("/orders");
      cookie.setMaxAge(30 * 24 * 60 * 60); // 30 days
      cookie.setHttpOnly(true);
      cookie.setSecure(true);
      response.addCookie(cookie);
    }

    // Resolve the squadron-scope filter with the same three-stage precedence (param > cookie >
    // default). "mine" is the plan-mandated default per section 5.3; only callers with a resolvable
    // active squadron can act on it, so admins in "all squadrons" mode silently collapse to "all"
    // regardless of cookie / param value (no squadron context to filter on).
    String resolvedScope;
    if (scope != null && !scope.isBlank() && VALID_SQUADRON_SCOPES.contains(scope)) {
      resolvedScope = scope;
      Cookie scopeCookie = new Cookie("orders_filter_scope", resolvedScope);
      scopeCookie.setPath("/orders");
      scopeCookie.setMaxAge(30 * 24 * 60 * 60); // 30 days
      scopeCookie.setHttpOnly(true);
      scopeCookie.setSecure(true);
      response.addCookie(scopeCookie);
    } else if (cookieScope != null
        && !cookieScope.isBlank()
        && VALID_SQUADRON_SCOPES.contains(cookieScope)) {
      resolvedScope = cookieScope;
    } else {
      resolvedScope = "mine";
    }
    boolean filterToOwnSquadron = "mine".equals(resolvedScope) && activeSquadronId != null;

    List<JobOrderDto> orders = new ArrayList<>();
    int yellowDays = 30;
    int redDays = 90;
    try {
      String statusParam = String.join(",", status);
      String squadronParam = filterToOwnSquadron ? "&squadronId=" + activeSquadronId : "";
      PageResponse<JobOrderDto> p =
          backendApiClient.get(
              "/api/v1/orders?size=1000&sort=priority,asc&status=" + statusParam + squadronParam,
              new ParameterizedTypeReference<>() {});
      if (p != null && p.content() != null) {
        orders = new ArrayList<>(p.content());
        for (JobOrderDto order : orders) {
          for (de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderMaterialDto mat :
              order.materials()) {
            log.debug(
                "Received stock for job order #{} ({}): {}/{} (material: {})",
                order.displayId(),
                order.id(),
                mat.currentStock(),
                mat.amount(),
                mat.material().name());
          }
        }
      }

      try {
        SystemSettingDto yellowSetting =
            backendApiClient.get(
                "/api/v1/settings/job_order.age_yellow_days", SystemSettingDto.class);
        yellowDays = Integer.parseInt(yellowSetting.value());
      } catch (Exception e) {
        log.warn("Could not fetch yellow days setting, using default");
      }
      try {
        SystemSettingDto redSetting =
            backendApiClient.get("/api/v1/settings/job_order.age_red_days", SystemSettingDto.class);
        redDays = Integer.parseInt(redSetting.value());
      } catch (Exception e) {
        log.warn("Could not fetch red days setting, using default");
      }
    } catch (Exception e) {
      log.error("Failed to fetch orders", e);
      log.error("Failed to load job orders", e);
      model.addAttribute("error", "error.joborder.load");
    }

    model.addAttribute("orders", orders);
    model.addAttribute("selectedStatuses", status);
    model.addAttribute("selectedScope", resolvedScope);
    // Effective state, after collapsing "mine" to "all" when there is no active squadron context
    // (admin all-squadrons mode). The template uses this to render the toggle as informational-
    // only rather than active when the user cannot meaningfully act on "mine".
    model.addAttribute("scopeFilterApplied", filterToOwnSquadron);
    model.addAttribute("ageYellowDays", yellowDays);
    model.addAttribute("ageRedDays", redDays);
    if (fragment != null && "results".equalsIgnoreCase(fragment)) {
      return "orders-index :: ordersResults";
    }
    return "orders-index";
  }

  /**
   * Renders the order detail page ({@code /orders/{id}}). Loads the order plus the auxiliary
   * material-status and assignee lists and exposes the role-derived edit flags so the template
   * stays free of inline expression checks.
   *
   * @return the {@code order-detail} view name
   */
  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public String viewOrderDetail(
      @PathVariable UUID id,
      @ModelAttribute("canViewJobOrders") boolean canViewJobOrders,
      Model model,
      @AuthenticationPrincipal OidcUser principal,
      @RequestParam(required = false) String fragment) {
    if (!canViewJobOrders) {
      // Non-profit members may not open order details — the backend returns 403 for them anyway.
      // Route to the create form (their only order surface) so a stray bookmark/link is graceful;
      // a section-swap caller gets a section-sized error fragment instead of a redirect it would
      // otherwise follow into a small results container (#571/#575, mirrors the #574 fix).
      return fragment != null ? "orders-detail :: fragmentError" : "redirect:/orders/create";
    }
    try {
      JobOrderDto order = backendApiClient.get("/api/v1/orders/" + id, JobOrderDto.class);
      model.addAttribute("order", order);
      model.addAttribute("currentUserId", getCurrentUserId(principal));
      model.addAttribute("itemsWithoutMaterials", itemsWithoutDerivedMaterials(order));

      boolean canAssign = isLogistician(principal);
      model.addAttribute("isLogistician", canAssign);

      if (canAssign) {
        model.addAttribute("users", fetchUsers());
        model.addAttribute("materials", fetchMaterials());
        model.addAttribute("squadrons", fetchSquadrons());
        addOwnerPickerOptions(model);

        if (!model.containsAttribute("jobOrderForm")) {
          JobOrderForm form = new JobOrderForm();
          form.setRequestingOrgUnitId(
              order.requestingOrgUnit() != null ? order.requestingOrgUnit().id() : null);
          form.setHandle(order.handle());
          form.setComment(order.comment());
          form.setVersion(order.version());
          form.getMaterials().clear();
          if (order.materials() != null) {
            for (de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderMaterialDto mat :
                order.materials()) {
              JobOrderForm.JobOrderMaterialForm mf = new JobOrderForm.JobOrderMaterialForm();
              mf.setMaterialId(mat.material().id());
              mf.setMinQuality(mat.minQuality());
              mf.setAmount(mat.amount());
              form.getMaterials().add(mf);
            }
          }
          if (form.getMaterials().isEmpty()) {
            form.getMaterials().add(new JobOrderForm.JobOrderMaterialForm());
          }
          model.addAttribute("jobOrderForm", form);
        }
      } else {
        model.addAttribute("users", new ArrayList<>());
      }

      int yellowDays = 30;
      int redDays = 90;
      try {
        SystemSettingDto yellowSetting =
            backendApiClient.get(
                "/api/v1/settings/job_order.age_yellow_days", SystemSettingDto.class);
        yellowDays = Integer.parseInt(yellowSetting.value());
      } catch (Exception e) {
        log.warn("Could not fetch yellow days setting, using default");
      }
      try {
        SystemSettingDto redSetting =
            backendApiClient.get("/api/v1/settings/job_order.age_red_days", SystemSettingDto.class);
        redDays = Integer.parseInt(redSetting.value());
      } catch (Exception e) {
        log.warn("Could not fetch red days setting, using default");
      }
      model.addAttribute("ageYellowDays", yellowDays);
      model.addAttribute("ageRedDays", redDays);

      if (!model.containsAttribute("handoverForm")) {
        JobOrderHandoverForm handoverForm = new JobOrderHandoverForm();
        // The time is pre-filled client-side in the user's browser
        // (see orders-detail.html, openHandoverModal) so that the user's
        // browser timezone (not the server/container) is used.
        handoverForm.setRecipientSquadron(
            order.requestingOrgUnit() != null ? order.requestingOrgUnit().shorthand() : null);
        model.addAttribute("handoverForm", handoverForm);
      }

      // Item orders carry their own handover form (per-line whole-unit delivery). Empty by default;
      // the modal renders one row per still-outstanding ordered-item line, bound by request-param
      // name. The flag gates the "log handover" button so it hides once every line is delivered.
      if (!model.containsAttribute("itemHandoverForm")) {
        model.addAttribute("itemHandoverForm", new JobOrderItemHandoverForm());
      }
      model.addAttribute("hasOutstandingItemLines", hasOutstandingItemLines(order));

      // Item-order blueprint coverage: which members of the responsible squadron/SK own the
      // blueprints for the ordered items. The backend gates this members-only (it returns 403 for a
      // non-member viewing an otherwise-public SK order), so the call is isolated in its own
      // try/catch — on any failure the section is simply omitted rather than failing the whole
      // page.
      if ("ITEM".equals(order.type())) {
        try {
          model.addAttribute(
              "itemBlueprintOwners",
              backendApiClient.get(
                  "/api/v1/orders/" + id + "/item-blueprint-owners",
                  JobOrderItemBlueprintOwnersDto.class));
        } catch (Exception e) {
          log.debug("Blueprint coverage unavailable for order {}: {}", id, e.getMessage());
        }
      }
    } catch (Exception e) {
      log.error("Failed to fetch order", e);
      log.error("Failed to load job order", e);
      if (fragment != null) {
        return "orders-detail :: fragmentError";
      }
      model.addAttribute("error", "error.joborder.load.details");
      return "redirect:/orders";
    }
    // In-place section swap (#571/#575): re-render only the section the caller mutated. The full
    // model is already built above, so each fragment renders with every attribute it needs.
    if (fragment != null) {
      return switch (fragment.toLowerCase(java.util.Locale.ROOT)) {
        case "materials" -> "orders-detail :: materialsSection";
        case "aggregated" -> "orders-detail :: aggregatedSection";
        default -> "orders-detail";
      };
    }
    return "orders-detail";
  }

  /**
   * Renders the create-order form ({@code /orders/create}). Seeds the materials + orderable-item
   * catalogs and the two owner-pickers; the {@code source} parameter threads through so the
   * post-save redirect can return to the originating page. For an anonymous guest, the responsible
   * (processing) picker is pre-selected to the configured intake Spezialkommando (see {@link
   * #preselectIntakeForGuest}).
   *
   * @param source optional origin marker
   * @param principal the caller, or {@code null} for an anonymous guest
   * @param model Thymeleaf model populated with form and reference catalogs
   * @return the {@code order-create} view name
   */
  @GetMapping("/create")
  public String viewCreateForm(
      @RequestParam(required = false) String source,
      @AuthenticationPrincipal OidcUser principal,
      Model model) {
    if (!model.containsAttribute("jobOrderForm")) {
      JobOrderForm form = new JobOrderForm();
      form.setSource(source);
      model.addAttribute("jobOrderForm", form);
    } else {
      JobOrderForm form = (JobOrderForm) model.getAttribute("jobOrderForm");
      if (form != null && form.getSource() == null) {
        form.setSource(source);
      }
    }
    if (!model.containsAttribute("jobOrderItemForm")) {
      JobOrderItemForm itemForm = new JobOrderItemForm();
      itemForm.setSource(source);
      model.addAttribute("jobOrderItemForm", itemForm);
    }
    model.addAttribute("materials", fetchMaterials());
    model.addAttribute("hasOrderableItems", hasOrderableItems());
    model.addAttribute("squadrons", fetchSquadrons());
    addOwnerPickerOptions(model);
    // Anonymous guests have no org-unit context; default the responsible (processing) picker to the
    // configured intake Spezialkommando — the unit the backend routes a guest order to absent a
    // profit-eligible pick — so the form shows it up front. Authenticated callers pick their own.
    if (principal == null) {
      preselectIntakeForGuest(model);
    }
    return "orders-create";
  }

  /**
   * Persists a new item order. Builds the backend item-order payload from the dynamically-bound
   * item editor (lines that lack an item, blueprint, or positive amount are dropped) and posts it
   * to the backend. Mirrors {@link #createOrder} for redirect / flash behaviour.
   *
   * @param form the bound item-order form
   * @param redirectAttributes flash carrier for toasts / re-render
   * @param principal the caller, or {@code null} for anonymous
   * @return redirect target
   */
  @PostMapping("/items")
  public String createItemOrder(
      @ModelAttribute("jobOrderItemForm") JobOrderItemForm form,
      @ModelAttribute("canViewJobOrders") boolean canViewJobOrders,
      RedirectAttributes redirectAttributes,
      @AuthenticationPrincipal OidcUser principal) {
    try {
      List<CreateJobOrderItemLineDto> lines =
          form.getItems().stream()
              .filter(
                  l ->
                      l.getGameItemId() != null
                          && l.getBlueprintId() != null
                          && l.getAmount() != null
                          && l.getAmount() > 0)
              .map(
                  l ->
                      new CreateJobOrderItemLineDto(
                          l.getGameItemId(),
                          l.getBlueprintId(),
                          l.getAmount(),
                          l.getMaterials().stream()
                              .filter(m -> m.getMaterialId() != null && m.getQuality() != null)
                              .map(
                                  m ->
                                      new CreateJobOrderItemMaterialDto(
                                          m.getMaterialId(), m.getQuality()))
                              .collect(Collectors.toList()),
                          l.getClientLineId(),
                          l.getParentClientLineId()))
              .collect(Collectors.toList());

      if (lines.isEmpty()) {
        redirectAttributes.addFlashAttribute("errorToast", "error.joborder.item.invalid");
        redirectAttributes.addFlashAttribute("jobOrderItemForm", form);
        return "redirect:/orders/create"
            + (form.getSource() != null ? "?source=" + form.getSource() : "");
      }

      CreateJobOrderItemRequestDto dto =
          new CreateJobOrderItemRequestDto(
              form.getResponsibleOrgUnitId(),
              form.getRequestingOrgUnitId(),
              form.getHandle(),
              form.getComment(),
              lines,
              form.getVersion());
      backendApiClient.post("/api/v1/orders/items", dto, JobOrderDto.class, true);
      redirectAttributes.addFlashAttribute("successToast", "success.joborder.create");

      // Anonymous guests and non-profit members cannot browse the queue, so keep them on the create
      // form (with the success toast) instead of bouncing them to a list they may not see.
      if (principal == null || !canViewJobOrders) {
        return "redirect:/orders/create"
            + (form.getSource() != null ? "?source=" + form.getSource() : "");
      }
      return "redirect:/orders";
    } catch (Exception e) {
      log.error("Failed to create item order", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.joborder.create.failed");
      redirectAttributes.addFlashAttribute("jobOrderItemForm", form);
      return "redirect:/orders/create"
          + (form.getSource() != null ? "?source=" + form.getSource() : "");
    }
  }

  /**
   * Renders the item-order edit page — the create form reused in edit mode. Loads the order, blocks
   * non-item orders and orders that already have an item-handover (those are frozen), prefills the
   * metadata form, and injects the existing item lines as {@code window.EDIT_ITEMS} so the create
   * form's JS rebuilds them (item + blueprint + amount + per-material quality + sub-assembly
   * parent).
   *
   * @param id the item-order id
   * @param model Thymeleaf model
   * @param redirectAttributes flash carrier for the block cases
   * @return the {@code orders-create} view (in edit mode) or a redirect to the detail page
   */
  @GetMapping("/{id}/items/edit")
  @PreAuthorize("hasRole('LOGISTICIAN')")
  public String viewEditItemForm(
      @PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
    JobOrderDto order;
    try {
      order = backendApiClient.get("/api/v1/orders/" + id, JobOrderDto.class);
    } catch (Exception e) {
      log.error("Failed to load item order for editing", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.joborder.load.details");
      return "redirect:/orders";
    }
    if (order == null || !"ITEM".equals(order.type())) {
      redirectAttributes.addFlashAttribute("errorToast", "error.joborder.item.edit.notItem");
      return "redirect:/orders/" + id;
    }
    if (order.itemHandovers() != null && !order.itemHandovers().isEmpty()) {
      redirectAttributes.addFlashAttribute("errorToast", "error.joborder.item.edit.hasHandovers");
      return "redirect:/orders/" + id;
    }

    // The shared create template renders both the (hidden) material form and the item form, so the
    // material form's th:object bean must exist even in item-edit mode.
    if (!model.containsAttribute("jobOrderForm")) {
      model.addAttribute("jobOrderForm", new JobOrderForm());
    }
    if (!model.containsAttribute("jobOrderItemForm")) {
      JobOrderItemForm itemForm = new JobOrderItemForm();
      itemForm.setHandle(order.handle());
      itemForm.setComment(order.comment());
      itemForm.setRequestingOrgUnitId(
          order.requestingOrgUnit() != null ? order.requestingOrgUnit().id() : null);
      itemForm.setVersion(order.version());
      model.addAttribute("jobOrderItemForm", itemForm);
    }
    model.addAttribute("editOrderId", id);
    model.addAttribute("editItems", buildEditItems(order));
    model.addAttribute("materials", fetchMaterials());
    model.addAttribute("hasOrderableItems", hasOrderableItems());
    model.addAttribute("squadrons", fetchSquadrons());
    addOwnerPickerOptions(model);
    return "orders-create";
  }

  /**
   * Builds the prefill payload for {@code window.EDIT_ITEMS}: one entry per ordered-item line with
   * its game item, blueprint, amount, the index of its sub-assembly parent line (or {@code null}),
   * and a material-id → quality map. The list index doubles as the client line id, so a child's
   * {@code parentId} is the list index of its parent.
   *
   * @param order the item order being edited
   * @return the JS-serializable prefill list (Thymeleaf inlines it as a JSON array)
   */
  private List<java.util.Map<String, Object>> buildEditItems(JobOrderDto order) {
    List<de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderItemDto> items =
        order.items() != null ? order.items() : List.of();
    java.util.Map<UUID, Integer> idToIndex = new java.util.HashMap<>();
    for (int i = 0; i < items.size(); i++) {
      idToIndex.put(items.get(i).id(), i);
    }
    List<java.util.Map<String, Object>> result = new ArrayList<>();
    for (de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderItemDto line : items) {
      java.util.Map<String, String> qualities = new java.util.LinkedHashMap<>();
      if (line.materials() != null) {
        for (de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderItemMaterialDto m :
            line.materials()) {
          if (m.material() != null && m.material().id() != null) {
            qualities.put(m.material().id().toString(), m.qualityRequirement());
          }
        }
      }
      java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
      entry.put("gameItemId", line.gameItem() != null ? line.gameItem().id().toString() : null);
      // The item picker now loads its options on demand, so the saved item's name must travel with
      // the prefill to seed the combobox's displayed label (the id alone would render blank).
      entry.put("gameItemName", line.gameItem() != null ? line.gameItem().name() : null);
      entry.put("blueprintId", line.blueprint() != null ? line.blueprint().id().toString() : null);
      entry.put("amount", line.amount() != null ? line.amount() : 1);
      entry.put(
          "parentId", line.parentItemId() != null ? idToIndex.get(line.parentItemId()) : null);
      entry.put("qualities", qualities);
      result.add(entry);
    }
    return result;
  }

  /**
   * Persists an edit to an item order's lines + metadata. Mirrors {@link #createItemOrder}'s
   * payload build but relays to the backend item-edit endpoint ({@code PUT
   * /api/v1/orders/{id}/items}) and redirects back to the detail page.
   *
   * @param id the item-order id
   * @param form the bound item-order form (lines rebuilt client-side)
   * @param redirectAttributes flash carrier
   * @return redirect to the detail page
   */
  @PostMapping("/{id}/items/update")
  @PreAuthorize("hasRole('LOGISTICIAN')")
  public String updateItemOrder(
      @PathVariable UUID id,
      @ModelAttribute("jobOrderItemForm") JobOrderItemForm form,
      RedirectAttributes redirectAttributes) {
    try {
      List<CreateJobOrderItemLineDto> lines =
          form.getItems().stream()
              .filter(
                  l ->
                      l.getGameItemId() != null
                          && l.getBlueprintId() != null
                          && l.getAmount() != null
                          && l.getAmount() > 0)
              .map(
                  l ->
                      new CreateJobOrderItemLineDto(
                          l.getGameItemId(),
                          l.getBlueprintId(),
                          l.getAmount(),
                          l.getMaterials().stream()
                              .filter(m -> m.getMaterialId() != null && m.getQuality() != null)
                              .map(
                                  m ->
                                      new CreateJobOrderItemMaterialDto(
                                          m.getMaterialId(), m.getQuality()))
                              .collect(Collectors.toList()),
                          l.getClientLineId(),
                          l.getParentClientLineId()))
              .collect(Collectors.toList());

      if (lines.isEmpty()) {
        redirectAttributes.addFlashAttribute("errorToast", "error.joborder.item.invalid");
        return "redirect:/orders/" + id + "/items/edit";
      }

      CreateJobOrderItemRequestDto dto =
          new CreateJobOrderItemRequestDto(
              null,
              form.getRequestingOrgUnitId(),
              form.getHandle(),
              form.getComment(),
              lines,
              form.getVersion());
      backendApiClient.put("/api/v1/orders/" + id + "/items", dto, JobOrderDto.class);
      redirectAttributes.addFlashAttribute("successToast", "success.joborder.update");
      return "redirect:/orders/" + id;
    } catch (Exception e) {
      log.error("Failed to update item order {}", id, e);
      redirectAttributes.addFlashAttribute("errorToast", "error.joborder.update.failed");
      return "redirect:/orders/" + id + "/items/edit";
    }
  }

  /**
   * JSON proxy for the create form's blueprint picker: returns the blueprints that produce the
   * given orderable item. Relays to the backend item-catalog endpoint.
   *
   * @param gameItemId the orderable item
   * @return the blueprint references producing it
   */
  @GetMapping("/item-blueprints/{gameItemId}")
  @ResponseBody
  public List<BlueprintReferenceDto> itemBlueprints(@PathVariable UUID gameItemId) {
    try {
      List<BlueprintReferenceDto> result =
          backendApiClient.get(
              "/api/v1/orders/item-catalog/" + gameItemId + "/blueprints",
              new ParameterizedTypeReference<List<BlueprintReferenceDto>>() {},
              true);
      return result != null ? result : List.of();
    } catch (Exception e) {
      log.error("Failed to fetch blueprints for item {}", gameItemId, e);
      return List.of();
    }
  }

  /**
   * JSON proxy for the create form's material-derivation preview: returns the resolved materials,
   * sub-assembly suggestions and unresolved-ingredient names for a blueprint at the given amount.
   *
   * @param blueprintId the chosen blueprint
   * @param amount the whole-unit amount to scale by (defaults to 1)
   * @return the derivation preview, or {@code null} on failure
   */
  @GetMapping("/item-derivation/{blueprintId}")
  @ResponseBody
  public ItemDerivationDto itemDerivation(
      @PathVariable UUID blueprintId,
      @RequestParam(required = false, defaultValue = "1") int amount) {
    try {
      return backendApiClient.get(
          "/api/v1/orders/item-catalog/blueprints/" + blueprintId + "/derivation?amount=" + amount,
          ItemDerivationDto.class,
          true);
    } catch (Exception e) {
      log.error("Failed to derive materials for blueprint {}", blueprintId, e);
      return null;
    }
  }

  /**
   * JSON proxy for the item-order picker's live search: looks up orderable items by a free-text
   * term and returns the matching references (capped server-side at 25). Replaces preloading the
   * first N items and filtering client-side, which silently hid every item past the alphabetical
   * cap once the catalog outgrew it. Public for parity with the anonymous create form; the term
   * rides as a URI variable so spaces and quotes survive the frontend&rarr;backend hop intact.
   *
   * @param q the case-insensitive item-name search term ({@code null} / blank = first page)
   * @return up to 25 matching orderable item references, or an empty list on failure
   */
  @GetMapping("/item-search")
  @ResponseBody
  public List<GameItemReferenceDto> itemSearch(@RequestParam(required = false) String q) {
    try {
      PageResponse<GameItemReferenceDto> page =
          backendApiClient.getPublic(
              "/api/v1/orders/item-catalog?search={q}&size=25&sort=name,asc",
              new ParameterizedTypeReference<PageResponse<GameItemReferenceDto>>() {},
              q == null ? "" : q);
      return page != null && page.content() != null ? page.content() : List.of();
    } catch (Exception e) {
      log.error("Failed to search orderable items", e);
      return List.of();
    }
  }

  /**
   * Returns {@code true} iff the option list contains at least one Spezialkommando entry. Computed
   * server-side because Thymeleaf's SpEL backend cannot parse Java lambda syntax inside template
   * expressions ({@code .stream().anyMatch(o -> …)} fails with EL1042E), and pre-computing the flag
   * keeps the template free of {@code #lists.contains(list.![kind], …)}-style gymnastics.
   *
   * @param options the active-org-unit list, may be {@code null}.
   * @return {@code true} when at least one row's {@code kind} is {@code SPECIAL_COMMAND}.
   */
  private static boolean containsSpecialCommand(List<OrgUnitMembershipOptionDto> options) {
    if (options == null) {
      return false;
    }
    for (OrgUnitMembershipOptionDto o : options) {
      if ("SPECIAL_COMMAND".equals(o.kind())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Persists a new job order. Validation failures re-render inline so the BindingResult stays
   * request-scoped. The source parameter determines the post-save redirect target.
   *
   * @return inline form view on failure, otherwise redirect
   */
  @PostMapping("/create")
  public String createOrder(
      @ModelAttribute("jobOrderForm") JobOrderForm form,
      @ModelAttribute("canViewJobOrders") boolean canViewJobOrders,
      RedirectAttributes redirectAttributes,
      @AuthenticationPrincipal OidcUser principal) {
    try {
      List<CreateJobOrderMaterialDto> materials =
          form.getMaterials().stream()
              .filter(m -> m.getMaterialId() != null && m.getAmount() != null && m.getAmount() > 0)
              .map(
                  m ->
                      new CreateJobOrderMaterialDto(
                          m.getMaterialId(), m.getMinQuality(), m.getAmount()))
              .collect(Collectors.toList());

      if (materials.isEmpty()) {
        redirectAttributes.addFlashAttribute("errorToast", "error.joborder.material.invalid");
        redirectAttributes.addFlashAttribute("jobOrderForm", form);
        return "redirect:/orders/create"
            + (form.getSource() != null ? "?source=" + form.getSource() : "");
      }

      CreateJobOrderDto dto =
          new CreateJobOrderDto(
              form.getResponsibleOrgUnitId(),
              form.getRequestingOrgUnitId(),
              form.getHandle(),
              form.getComment(),
              materials,
              form.getVersion());
      backendApiClient.post("/api/v1/orders", dto, JobOrderDto.class, true);
      redirectAttributes.addFlashAttribute("successToast", "success.joborder.create");

      // Anonymous guests and non-profit members cannot browse the queue, so keep them on the create
      // form (with the success toast) instead of bouncing them to a list they may not see.
      if (principal == null || !canViewJobOrders) {
        return "redirect:/orders/create"
            + (form.getSource() != null ? "?source=" + form.getSource() : "");
      }
      if ("index".equals(form.getSource())) {
        return "redirect:/orders";
      }
      // fallback
      return "redirect:/orders";
    } catch (Exception e) {
      log.error("Failed to create order", e);
      log.error("Failed to create job order", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.joborder.create.failed");
      redirectAttributes.addFlashAttribute("jobOrderForm", form);
      return "redirect:/orders/create"
          + (form.getSource() != null ? "?source=" + form.getSource() : "");
    }
  }

  /**
   * Reorders an order's priority (drag-and-drop between job orders). Backend uses pessimistic
   * locking on the entire job-order priority sequence to serialize concurrent reorders, per the
   * concurrency pattern in CLAUDE.md.
   *
   * @return redirect to {@code /orders}
   */
  @PostMapping("/{id}/priority")
  @PreAuthorize("isAuthenticated()")
  public String updatePriority(
      @PathVariable UUID id,
      @RequestParam Integer priority,
      RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.put(
          "/api/v1/orders/" + id + "/priority?priority=" + priority, null, JobOrderDto.class);
      redirectAttributes.addFlashAttribute("successToast", "success.joborder.priority");
    } catch (Exception e) {
      log.error("Failed to update priority", e);
      log.error("Failed to update job order priority", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.joborder.priority.failed");
    }
    return "redirect:/orders";
  }

  /**
   * AJAX twin of {@link #updatePriority}: persists a drag-drop reorder without a full-page reload
   * (epic #571 / #575). Returns the dragged order so the caller can confirm success; the
   * order-index JS then re-renders the whole queue via a {@code ?fragment=results} swap, because
   * the backend reshuffles <em>every</em> active order's priority slot (not just this one), so
   * sibling rows' {@code data-priority} attributes must refresh or the next drag computes a stale
   * target slot. The gate is tightened to {@code LOGISTICIAN} to match the backend so a
   * non-logistician gets a clean propagated 403 toast instead of a redirect.
   *
   * @param id the order whose priority slot changed
   * @param priority the new 1-based target slot
   * @return the updated order on success, or the propagated RFC 7807 backend error
   */
  @PutMapping("/{id}/priority/ajax")
  @PreAuthorize("hasRole('LOGISTICIAN')")
  @ResponseBody
  public org.springframework.http.ResponseEntity<Object> updatePriorityAjax(
      @PathVariable UUID id, @RequestParam Integer priority) {
    try {
      JobOrderDto result =
          backendApiClient.put(
              "/api/v1/orders/" + id + "/priority?priority=" + priority, null, JobOrderDto.class);
      return org.springframework.http.ResponseEntity.ok(result);
    } catch (BackendServiceException bse) {
      log.error("Failed to update priority (ajax) for order {}: {}", id, bse.getMessage());
      return propagateBackendError(bse);
    } catch (Exception e) {
      log.error("Failed to update priority (ajax) for order {}", id, e);
      return org.springframework.http.ResponseEntity.status(
              org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
          .build();
    }
  }

  /**
   * Re-emits a backend {@link BackendServiceException} as an {@code application/problem+json}
   * response preserving the stable {@code code} and human-readable {@code detail} from the upstream
   * RFC 7807 body. The order-detail / order-index AJAX layer ({@code krt-fetch.js}) reads {@code
   * code} to decide between a "stale data, reload?" prompt (only {@code OPTIMISTIC_LOCK} / {@code
   * PESSIMISTIC_LOCK}) and a plain error toast for domain conflicts; returning {@code .build()}
   * with only the status would strip that signal and make every 409 look like an optimistic-lock
   * conflict.
   *
   * @param e parsed backend exception with status + RFC 7807 fields
   * @return problem+json response mirroring the upstream status and body
   */
  private static org.springframework.http.ResponseEntity<Object> propagateBackendError(
      BackendServiceException e) {
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
   * AJAX status transition endpoint. Updates the job order's status (OPEN → IN_PROGRESS →
   * COMPLETED, REJECTED). Backend enforces the state machine and rejects illegal transitions with a
   * 400.
   *
   * @return updated order on success, propagated backend status code on failure
   */
  @PostMapping("/{id}/status")
  @PreAuthorize("isAuthenticated()")
  @org.springframework.web.bind.annotation.ResponseBody
  public org.springframework.http.ResponseEntity<Object> updateStatus(
      @PathVariable UUID id, @RequestBody UpdateJobOrderStatusDto dto) {
    try {
      JobOrderDto result =
          backendApiClient.put("/api/v1/orders/" + id + "/status", dto, JobOrderDto.class);
      return org.springframework.http.ResponseEntity.ok(result);
    } catch (BackendServiceException bse) {
      log.error("Failed to update status for order {}: {}", id, bse.getMessage());
      return propagateBackendError(bse);
    } catch (Exception e) {
      log.error("Failed to update status for order {}", id, e);
      return org.springframework.http.ResponseEntity.status(
              org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
          .build();
    }
  }

  /**
   * AJAX create-or-update of a material claim ("Eintragung") on a public SK order (Phase 6, #346).
   * Relays the payload to the backend's claim upsert and propagates its status code so the
   * order-detail JS can show a clean toast — 400 (not an open SK order / unknown bucket /
   * overclaim), 403 (may not act for the claiming squadron), 409 (concurrent claim modification) —
   * instead of a stack trace. The coarse {@code hasRole('LOGISTICIAN')} gate here is the same one
   * the backend carries; the fine per-squadron / responsible-SK matrix is enforced backend-side.
   *
   * @param id the order id.
   * @param dto the claim payload (material, quality bucket, claiming squadron, amount).
   * @return the persisted claim on success, or the propagated backend error status.
   */
  @PostMapping("/{id}/claims")
  @PreAuthorize("hasRole('LOGISTICIAN')")
  @ResponseBody
  public org.springframework.http.ResponseEntity<Object> upsertClaim(
      @PathVariable UUID id, @RequestBody CreateClaimDto dto) {
    try {
      ClaimDto result =
          backendApiClient.post("/api/v1/orders/" + id + "/claims", dto, ClaimDto.class);
      return org.springframework.http.ResponseEntity.status(
              org.springframework.http.HttpStatus.CREATED)
          .body(result);
    } catch (BackendServiceException bse) {
      log.error("Failed to upsert claim on order {}: {}", id, bse.getMessage());
      return propagateBackendError(bse);
    } catch (Exception e) {
      log.error("Failed to upsert claim on order {}", id, e);
      return org.springframework.http.ResponseEntity.status(
              org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
          .build();
    }
  }

  /**
   * AJAX withdrawal of a material claim (Phase 6, #346). Relays to the backend's claim delete and
   * propagates the status code (404 unknown claim, 400 terminal/non-SK order, 403 forbidden) for a
   * clean toast.
   *
   * @param id the order id.
   * @param claimId the claim to withdraw.
   * @return 204 on success, or the propagated backend error status.
   */
  @PostMapping("/{id}/claims/{claimId}/withdraw")
  @PreAuthorize("hasRole('LOGISTICIAN')")
  @ResponseBody
  public org.springframework.http.ResponseEntity<Object> withdrawClaim(
      @PathVariable UUID id, @PathVariable UUID claimId) {
    try {
      backendApiClient.delete("/api/v1/orders/" + id + "/claims/" + claimId, Void.class);
      return org.springframework.http.ResponseEntity.noContent().build();
    } catch (BackendServiceException bse) {
      log.error("Failed to withdraw claim {} on order {}: {}", claimId, id, bse.getMessage());
      return propagateBackendError(bse);
    } catch (Exception e) {
      log.error("Failed to withdraw claim {} on order {}", claimId, id, e);
      return org.springframework.http.ResponseEntity.status(
              org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
          .build();
    }
  }

  /**
   * Persists an edit to the order's metadata (note, target inventory location, materials list).
   * Restricted to logisticians at the controller boundary; backend additionally enforces a
   * fine-grained role check on the {@code @PreAuthorize}-annotated service method.
   *
   * @return redirect to the order detail page
   */
  @PostMapping("/{id}/update")
  @PreAuthorize("hasRole('LOGISTICIAN')")
  public String updateOrder(
      @PathVariable UUID id,
      @ModelAttribute("jobOrderForm") JobOrderForm form,
      RedirectAttributes redirectAttributes) {
    try {
      List<CreateJobOrderMaterialDto> materials =
          form.getMaterials().stream()
              .filter(m -> m.getMaterialId() != null && m.getAmount() != null && m.getAmount() > 0)
              .map(
                  m ->
                      new CreateJobOrderMaterialDto(
                          m.getMaterialId(), m.getMinQuality(), m.getAmount()))
              .collect(Collectors.toList());

      if (materials.isEmpty()) {
        redirectAttributes.addFlashAttribute("errorToast", "error.joborder.material.invalid");
        redirectAttributes.addFlashAttribute("jobOrderForm", form);
        return "redirect:/orders/" + id;
      }

      CreateJobOrderDto dto =
          new CreateJobOrderDto(
              null,
              form.getRequestingOrgUnitId(),
              form.getHandle(),
              form.getComment(),
              materials,
              form.getVersion());
      backendApiClient.put("/api/v1/orders/" + id, dto, JobOrderDto.class);
      redirectAttributes.addFlashAttribute("successToast", "success.joborder.update");
      return "redirect:/orders/" + id;
    } catch (Exception e) {
      log.error("Failed to update order", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.joborder.update.failed");
      redirectAttributes.addFlashAttribute("jobOrderForm", form);
      return "redirect:/orders/" + id;
    }
  }

  /**
   * Cancels (soft-deletes) a job order. Backend rejects the delete when the order has linked
   * inventory items, per the {@code EntityInUseException} pattern.
   *
   * @return redirect to {@code /orders}
   */
  @PostMapping("/{id}/delete")
  @PreAuthorize("isAuthenticated()")
  public String deleteOrder(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/orders/" + id, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "success.joborder.delete");
    } catch (Exception e) {
      log.error("Failed to delete order", e);
      log.error("Failed to delete job order", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.joborder.delete.failed");
    }
    return "redirect:/orders";
  }

  /**
   * AJAX twin of {@link #deleteOrder} (#575): cancels (soft-deletes) the order without a server
   * redirect, so the order-detail JS can confirm via the KRT dialog and then navigate to the list
   * itself. On a backend rejection (e.g. the order still has linked inventory) the RFC 7807 error
   * is propagated so the page shows a toast and stays put instead of redirect-reflashing. The
   * classic {@code POST}→redirect above stays the no-JS fallback.
   *
   * @param id the order to cancel
   * @return 204 on success, or the propagated RFC 7807 backend error
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  @ResponseBody
  public org.springframework.http.ResponseEntity<Object> deleteOrderAjax(@PathVariable UUID id) {
    try {
      backendApiClient.delete("/api/v1/orders/" + id, Void.class);
      return org.springframework.http.ResponseEntity.noContent().build();
    } catch (BackendServiceException bse) {
      log.error("Failed to delete order {} (ajax): {}", id, bse.getMessage());
      return propagateBackendError(bse);
    } catch (Exception e) {
      log.error("Failed to delete order {} (ajax)", id, e);
      return org.springframework.http.ResponseEntity.status(
              org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
          .build();
    }
  }

  /**
   * Adds an assignee to the job order and re-renders the Bearbeiter section as an AJAX fragment (no
   * full-page reload). The user-picker is fed by {@link UserProxyController}'s search endpoint.
   *
   * @param id job-order id
   * @param userId the user to add
   * @param model the view model populated for the fragment render
   * @param principal the authenticated caller
   * @return the {@code orders-detail :: assigneesSection} fragment view name
   */
  @PostMapping("/{id}/assignees")
  @PreAuthorize("isAuthenticated()")
  public String addAssignee(
      @PathVariable UUID id,
      @RequestParam UUID userId,
      Model model,
      @AuthenticationPrincipal OidcUser principal) {
    JobOrderDto order =
        callAssigneeMutation(
            "add assignee",
            () ->
                backendApiClient.post(
                    "/api/v1/orders/" + id + "/assignees/" + userId, null, JobOrderDto.class));
    populateAssigneeSectionModel(model, principal, order);
    return "orders-detail :: assigneesSection";
  }

  /**
   * Creates a material handover for the job order.
   *
   * <p>The backend service uses the {@code …WithinTransaction} concurrency pattern (see CLAUDE.md):
   * it iterates the order's materials, collects ids that need a bulk clearing update, runs the bulk
   * update exactly once after the loop, and re-fetches the aggregate root before the completion
   * check — without that pattern a second {@code save()} on a detached child would silently merge
   * and bump {@code @Version} a second time, triggering 409s for clean callers.
   *
   * @return redirect to the order detail page
   */
  @PostMapping("/{id}/handovers")
  @PreAuthorize("hasRole('LOGISTICIAN') or hasRole('OFFICER') or hasRole('ADMIN')")
  public String createHandover(
      @PathVariable UUID id,
      @ModelAttribute("handoverForm") JobOrderHandoverForm form,
      RedirectAttributes redirectAttributes) {
    try {
      List<JobOrderHandoverItemCreateDto> items =
          form.getItems().stream()
              .filter(
                  item ->
                      item.getInventoryItemId() != null
                          && item.getAmount() != null
                          && item.getAmount() > 0)
              .map(
                  item ->
                      new JobOrderHandoverItemCreateDto(
                          item.getInventoryItemId(), item.getAmount()))
              .collect(Collectors.toList());

      if (items.isEmpty()) {
        redirectAttributes.addFlashAttribute("errorToast", "error.joborder.handover.noitems");
        return "redirect:/orders/" + id;
      }

      // The frontend hidden input transmits the handover time as a UTC ISO-Instant (e.g.
      // "2026-04-25T10:04:00.000Z") that is produced client-side from the user's browser-local
      // date/time inputs by datetime-splitter.js. Parsing as Instant preserves the absolute
      // point in time and avoids timezone drift (previously LocalDateTime.parse threw on the
      // trailing "Z" and silently fell back to Instant.now(), which not only ignored the user
      // input but also produced wrong displayed times for users in DST/non-UTC zones).
      Instant handoverTime = Instant.now();
      String rawHandoverTime = form.getHandoverTime();
      if (rawHandoverTime != null && !rawHandoverTime.isBlank()) {
        try {
          handoverTime = Instant.parse(rawHandoverTime);
        } catch (Exception eiso) {
          try {
            handoverTime =
                LocalDateTime.parse(rawHandoverTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
          } catch (Exception elocal) {
            log.warn("Could not parse handoverTime {}, using now()", rawHandoverTime);
          }
        }
      }

      JobOrderHandoverCreateDto dto =
          new JobOrderHandoverCreateDto(
              handoverTime, form.getRecipientHandle(), form.getRecipientSquadron(), items);

      backendApiClient.post("/api/v1/orders/" + id + "/handovers", dto, JobOrderHandoverDto.class);
      redirectAttributes.addFlashAttribute("successToast", "success.joborder.handover");
    } catch (BackendServiceException bse) {
      // Backend already returned an RFC7807 Problem+JSON response. Log status, problem code,
      // correlationId and field errors so a 400 VALIDATION_FAILED can be diagnosed from the
      // frontend log without having to reproduce the request. No PII (rejected values) is logged.
      de.greluc.krt.iri.basetool.frontend.logging.BackendErrorLogging.warn(
          log, "POST /api/v1/orders/{id}/handovers", id, bse);
      redirectAttributes.addFlashAttribute("errorToast", "error.joborder.handover.failed");
    } catch (Exception e) {
      log.error(
          "Failed to create handover for jobOrder={} via POST /api/v1/orders/{}/handovers",
          id,
          id,
          e);
      redirectAttributes.addFlashAttribute("errorToast", "error.joborder.handover.failed");
    }
    return "redirect:/orders/" + id;
  }

  /**
   * Records an item handover for an item order: relays the per-line delivered whole-unit quantities
   * to the Phase 3 backend endpoint, which decrements outstanding amounts and auto-completes the
   * order once every line is fully delivered. Rows with a null or non-positive amount are dropped;
   * an empty result short-circuits with an error toast. On success the page redirects (a full
   * reload), so the refreshed {@code @Version} is picked up and no stale-version 409 can follow.
   *
   * @param id the item order's id
   * @param form the bound item-handover form (per-line amounts + recipient + time)
   * @param redirectAttributes flash channel for the success/error toast
   * @return redirect to the order detail page
   */
  @PostMapping("/{id}/item-handovers")
  @PreAuthorize("hasRole('LOGISTICIAN') or hasRole('OFFICER') or hasRole('ADMIN')")
  public String createItemHandover(
      @PathVariable UUID id,
      @ModelAttribute("itemHandoverForm") JobOrderItemHandoverForm form,
      RedirectAttributes redirectAttributes) {
    try {
      List<JobOrderItemHandoverEntryCreateDto> entries =
          form.getEntries().stream()
              .filter(
                  e -> e.getJobOrderItemId() != null && e.getAmount() != null && e.getAmount() > 0)
              .map(
                  e -> new JobOrderItemHandoverEntryCreateDto(e.getJobOrderItemId(), e.getAmount()))
              .collect(Collectors.toList());

      if (entries.isEmpty()) {
        redirectAttributes.addFlashAttribute("errorToast", "error.joborder.handover.noitems");
        return "redirect:/orders/" + id;
      }

      // handoverTime arrives as a client-produced UTC ISO-Instant (see orders-detail.html); parse
      // as Instant to preserve the absolute point in time, falling back to local-datetime parsing
      // and finally now() so a malformed value never blocks the handover. Mirrors createHandover.
      Instant handoverTime = Instant.now();
      String rawHandoverTime = form.getHandoverTime();
      if (rawHandoverTime != null && !rawHandoverTime.isBlank()) {
        try {
          handoverTime = Instant.parse(rawHandoverTime);
        } catch (Exception eiso) {
          try {
            handoverTime =
                LocalDateTime.parse(rawHandoverTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
          } catch (Exception elocal) {
            log.warn("Could not parse item handoverTime {}, using now()", rawHandoverTime);
          }
        }
      }

      JobOrderItemHandoverCreateDto dto =
          new JobOrderItemHandoverCreateDto(handoverTime, form.getRecipientHandle(), entries);
      backendApiClient.post(
          "/api/v1/orders/" + id + "/item-handovers", dto, JobOrderItemHandoverDto.class);
      redirectAttributes.addFlashAttribute("successToast", "success.joborder.handover");
    } catch (BackendServiceException bse) {
      de.greluc.krt.iri.basetool.frontend.logging.BackendErrorLogging.warn(
          log, "POST /api/v1/orders/{id}/item-handovers", id, bse);
      redirectAttributes.addFlashAttribute("errorToast", "error.joborder.handover.failed");
    } catch (Exception e) {
      log.error(
          "Failed to create item handover for jobOrder={} via POST"
              + " /api/v1/orders/{}/item-handovers",
          id,
          id,
          e);
      redirectAttributes.addFlashAttribute("errorToast", "error.joborder.handover.failed");
    }
    return "redirect:/orders/" + id;
  }

  /**
   * Removes a material requirement from the order without deleting any associated inventory items
   * (the items keep their job-order link cleared by the backend).
   *
   * @return redirect to the order detail page
   */
  @PostMapping("/{id}/materials/unlink")
  @PreAuthorize("hasAnyRole('LOGISTICIAN', 'OFFICER', 'ADMIN')")
  public String unlinkMaterial(
      @PathVariable UUID id, @RequestParam UUID materialId, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/orders/" + id + "/materials/" + materialId, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "orders.detail.material.unlink.success");
    } catch (BackendServiceException bse) {
      de.greluc.krt.iri.basetool.frontend.logging.BackendErrorLogging.warn(
          log, "DELETE /api/v1/orders/{id}/materials/{materialId}", id, bse);
      redirectAttributes.addFlashAttribute("errorToast", "orders.detail.material.unlink.error");
    } catch (Exception e) {
      log.error("Failed to unlink material {} from jobOrder={}", materialId, id, e);
      redirectAttributes.addFlashAttribute("errorToast", "orders.detail.material.unlink.error");
    }
    return "redirect:/orders/" + id;
  }

  /**
   * Detaches an inventory item from the job order. The item stays in the user's inventory but no
   * longer counts towards the order's progress.
   *
   * @return redirect to the order detail page
   */
  @PostMapping("/{id}/inventory/{inventoryItemId}/unlink")
  @PreAuthorize("hasAnyRole('LOGISTICIAN', 'OFFICER', 'ADMIN')")
  public String unlinkInventoryItem(
      @PathVariable UUID id,
      @PathVariable UUID inventoryItemId,
      RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete(
          "/api/v1/orders/" + id + "/inventory/" + inventoryItemId + "/unlink", Void.class);
      redirectAttributes.addFlashAttribute(
          "successToast", "orders.detail.inventory.unlink.success");
    } catch (BackendServiceException bse) {
      de.greluc.krt.iri.basetool.frontend.logging.BackendErrorLogging.warn(
          log, "DELETE /api/v1/orders/{id}/inventory/{inventoryItemId}/unlink", id, bse);
      redirectAttributes.addFlashAttribute("errorToast", "orders.detail.inventory.unlink.error");
    } catch (Exception e) {
      log.error("Failed to unlink inventory item {} from jobOrder={}", inventoryItemId, id, e);
      redirectAttributes.addFlashAttribute("errorToast", "orders.detail.inventory.unlink.error");
    }
    return "redirect:/orders/" + id;
  }

  /**
   * AJAX twin of {@link #unlinkInventoryItem} (#575): detaches the inventory item and returns the
   * refreshed order so the detail page can re-render the material section in place AND pick up the
   * bumped order {@code @Version} (the detach mutates the aggregate, so a subsequent status/
   * handover write would otherwise 409). The classic {@code POST}→redirect above stays the no-JS
   * fallback.
   *
   * @param id the order id
   * @param inventoryItemId the inventory item to detach
   * @return the refreshed order on success, or the propagated RFC 7807 backend error
   */
  @DeleteMapping("/{id}/inventory/{inventoryItemId}/unlink/ajax")
  @PreAuthorize("hasAnyRole('LOGISTICIAN', 'OFFICER', 'ADMIN')")
  @ResponseBody
  public org.springframework.http.ResponseEntity<Object> unlinkInventoryItemAjax(
      @PathVariable UUID id, @PathVariable UUID inventoryItemId) {
    try {
      backendApiClient.delete(
          "/api/v1/orders/" + id + "/inventory/" + inventoryItemId + "/unlink", Void.class);
      JobOrderDto order = backendApiClient.get("/api/v1/orders/" + id, JobOrderDto.class);
      return org.springframework.http.ResponseEntity.ok(order);
    } catch (BackendServiceException bse) {
      log.error(
          "Failed to unlink inventory item {} from order {} (ajax): {}",
          inventoryItemId,
          id,
          bse.getMessage());
      return propagateBackendError(bse);
    } catch (Exception e) {
      log.error("Failed to unlink inventory item {} from order {} (ajax)", inventoryItemId, id, e);
      return org.springframework.http.ResponseEntity.status(
              org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
          .build();
    }
  }

  /**
   * Removes an assignee from the job order and re-renders the Bearbeiter section as an AJAX
   * fragment (no full-page reload).
   *
   * @param id job-order id
   * @param userId the user to remove
   * @param model the view model populated for the fragment render
   * @param principal the authenticated caller
   * @return the {@code orders-detail :: assigneesSection} fragment view name
   */
  @DeleteMapping("/{id}/assignees/{userId}")
  @PreAuthorize("isAuthenticated()")
  public String removeAssignee(
      @PathVariable UUID id,
      @PathVariable UUID userId,
      Model model,
      @AuthenticationPrincipal OidcUser principal) {
    JobOrderDto order =
        callAssigneeMutation(
            "remove assignee",
            () ->
                backendApiClient.delete(
                    "/api/v1/orders/" + id + "/assignees/" + userId, JobOrderDto.class));
    populateAssigneeSectionModel(model, principal, order);
    return "orders-detail :: assigneesSection";
  }

  /**
   * Sets (creates or replaces) the current user's — or, for a Logistician+, any assignee's — note
   * on the order, then re-renders the Bearbeiter section as an AJAX fragment. The backend enforces
   * the self-or-logistician rule and the optimistic lock on the assignee edge (HTTP 409 on stale
   * input, relayed here so the page JS can prompt a reload).
   *
   * @param id job-order id
   * @param userId the assignee whose note is changed
   * @param body the new note text + the assignee edge version last seen by the client
   * @param model the view model populated for the fragment render
   * @param principal the authenticated caller
   * @return the {@code orders-detail :: assigneesSection} fragment view name
   */
  @PutMapping("/{id}/assignees/{userId}/note")
  @PreAuthorize("isAuthenticated()")
  public String setAssigneeNote(
      @PathVariable UUID id,
      @PathVariable UUID userId,
      @RequestBody AssigneeNoteRequest body,
      Model model,
      @AuthenticationPrincipal OidcUser principal) {
    JobOrderDto order =
        callAssigneeMutation(
            "set assignee note",
            () ->
                backendApiClient.put(
                    "/api/v1/orders/" + id + "/assignees/" + userId + "/note",
                    body,
                    JobOrderDto.class));
    populateAssigneeSectionModel(model, principal, order);
    return "orders-detail :: assigneesSection";
  }

  /**
   * Clears an assignee's note and re-renders the Bearbeiter section as an AJAX fragment. Same
   * self-or-logistician + optimistic-lock semantics as {@link #setAssigneeNote}.
   *
   * @param id job-order id
   * @param userId the assignee whose note is cleared
   * @param version the assignee edge version last seen by the client
   * @param model the view model populated for the fragment render
   * @param principal the authenticated caller
   * @return the {@code orders-detail :: assigneesSection} fragment view name
   */
  @DeleteMapping("/{id}/assignees/{userId}/note")
  @PreAuthorize("isAuthenticated()")
  public String deleteAssigneeNote(
      @PathVariable UUID id,
      @PathVariable UUID userId,
      @RequestParam(required = false) Long version,
      Model model,
      @AuthenticationPrincipal OidcUser principal) {
    String query = version != null ? "?version=" + version : "";
    JobOrderDto order =
        callAssigneeMutation(
            "delete assignee note",
            () ->
                backendApiClient.delete(
                    "/api/v1/orders/" + id + "/assignees/" + userId + "/note" + query,
                    JobOrderDto.class));
    populateAssigneeSectionModel(model, principal, order);
    return "orders-detail :: assigneesSection";
  }

  /**
   * Runs an assignee-section mutation against the backend and translates a backend failure into the
   * matching HTTP status so the order-detail page JS can react (409 → reload prompt, 403 →
   * forbidden toast, else generic error). Keeps the four AJAX endpoints free of duplicated
   * try/catch.
   *
   * @param action short action label for the log line
   * @param call the backend call returning the updated order
   * @return the updated order on success
   */
  private JobOrderDto callAssigneeMutation(
      String action, java.util.function.Supplier<JobOrderDto> call) {
    try {
      return call.get();
    } catch (BackendServiceException bse) {
      log.warn("Failed to {} (status {})", action, bse.getStatusCode());
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.valueOf(bse.getStatusCode()));
    } catch (Exception e) {
      log.error("Failed to {}", action, e);
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Populates the model attributes the {@code assigneesSection} fragment reads — the updated order,
   * the caller's user id, the Logistician flag and (for Logisticians) the full user list backing
   * the add-user picker. Shared by the four assignee AJAX endpoints and mirrors what {@link
   * #viewOrderDetail} sets for the initial full-page render.
   *
   * @param model the view model to populate
   * @param principal the authenticated caller
   * @param order the freshly mutated order
   */
  private void populateAssigneeSectionModel(Model model, OidcUser principal, JobOrderDto order) {
    model.addAttribute("order", order);
    model.addAttribute("currentUserId", getCurrentUserId(principal));
    boolean canAssign = isLogistician(principal);
    model.addAttribute("isLogistician", canAssign);
    model.addAttribute("users", canAssign ? fetchUsers() : new ArrayList<>());
  }

  /**
   * Request body for the assignee-note PUT endpoint (frontend mirror of the backend record).
   *
   * @param note the new note text (blank/{@code null} clears the note)
   * @param version the assignee edge version the client last saw
   */
  public record AssigneeNoteRequest(String note, Long version) {}

  /**
   * AJAX endpoint that lists inventory items eligible for linking to the given material on the
   * given order. Used by the order-detail page's "link inventory" picker.
   *
   * @return list of inventory items (raw JSON), or 500 on backend failure
   */
  @GetMapping("/{id}/materials/{matId}/inventory")
  @ResponseBody
  @PreAuthorize("isAuthenticated()")
  public List<InventoryItemDto> getInventoryItemsForMaterial(
      @PathVariable UUID id, @PathVariable UUID matId) {
    try {
      return backendApiClient.get(
          "/api/v1/orders/" + id + "/materials/" + matId + "/inventory",
          new ParameterizedTypeReference<>() {});
    } catch (Exception e) {
      log.error("Failed to fetch inventory items for job order {} and material {}", id, matId, e);
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
          "Failed to load inventory items");
    }
  }

  private List<UserDto> fetchUsers() {
    try {
      PageResponse<UserDto> p =
          backendApiClient.get(
              "/api/v1/users?size=1000",
              new ParameterizedTypeReference<PageResponse<UserDto>>() {});
      if (p != null && p.content() != null) {
        return new ArrayList<>(p.content());
      }
    } catch (Exception e) {
      log.warn("Failed to fetch users (might not be an admin/officer)");
    }
    return new ArrayList<>();
  }

  private List<MaterialDto> fetchMaterials() {
    try {
      List<MaterialDto> list =
          backendApiClient.getCached(
              "/api/v1/materials/job-order",
              new ParameterizedTypeReference<List<MaterialDto>>() {},
              true);
      if (list != null) {
        return new ArrayList<>(list);
      }
    } catch (Exception e) {
      log.error("Failed to fetch job-order materials", e);
    }
    return new ArrayList<>();
  }

  /**
   * Probes whether the backend has at least one orderable item (a blueprint output with a
   * resolvable material). The item-order form needs only this existence flag at render time — to
   * choose between the picker and the "no orderable items" banner — because the picker itself now
   * searches the catalog live ({@code GET /orders/item-search}) instead of preloading every item. A
   * {@code size=1} probe stays cheap regardless of catalog size; a backend hiccup fails open
   * (assume items exist) so a transient blip never hides the live-search picker behind the empty
   * banner.
   *
   * @return {@code true} when at least one orderable item exists (or the probe could not decide)
   */
  private boolean hasOrderableItems() {
    try {
      PageResponse<GameItemReferenceDto> page =
          backendApiClient.getCached(
              "/api/v1/orders/item-catalog?size=1&sort=name,asc",
              new ParameterizedTypeReference<PageResponse<GameItemReferenceDto>>() {},
              true);
      return page == null || page.content() == null || !page.content().isEmpty();
    } catch (Exception e) {
      log.error("Failed to probe orderable items", e);
      return true;
    }
  }

  /**
   * Collects the display names of an item order's lines whose blueprint derived no procurable
   * material (an empty {@code materials} snapshot). The persisted order keeps only resolved
   * requirements, so an empty list is the detail-time signal that a recipe's RESOURCE ingredients
   * were all unresolved or ITEM-only; the detail view surfaces these in a warning banner so the
   * logistician knows the aggregated-materials view is incomplete for that line. Returns an empty
   * list for material orders or when every line derived at least one material.
   *
   * @param order the loaded order (any kind)
   * @return distinct item names lacking derived materials, in line order; never {@code null}
   */
  private List<String> itemsWithoutDerivedMaterials(JobOrderDto order) {
    List<String> names = new ArrayList<>();
    if (order == null || !"ITEM".equals(order.type()) || order.items() == null) {
      return names;
    }
    for (JobOrderItemDto item : order.items()) {
      if (item.materials() == null || item.materials().isEmpty()) {
        String name = item.gameItem() != null ? item.gameItem().name() : null;
        if (name != null && !names.contains(name)) {
          names.add(name);
        }
      }
    }
    return names;
  }

  /**
   * Reports whether an item order still has at least one ordered-item line with outstanding
   * (ordered minus delivered) whole units. Gates the item-handover button: once every line is fully
   * delivered the order is COMPLETED and the button is hidden. Always {@code false} for material
   * orders or an order with no item lines.
   *
   * @param order the loaded order (any kind)
   * @return {@code true} if any item line has a positive outstanding quantity
   */
  private boolean hasOutstandingItemLines(JobOrderDto order) {
    if (order == null || !"ITEM".equals(order.type()) || order.items() == null) {
      return false;
    }
    for (JobOrderItemDto item : order.items()) {
      int ordered = item.amount() != null ? item.amount() : 0;
      int delivered = item.deliveredAmount() != null ? item.deliveredAmount() : 0;
      if (ordered - delivered > 0) {
        return true;
      }
    }
    return false;
  }

  private List<SquadronDto> fetchSquadrons() {
    try {
      PageResponse<SquadronDto> p =
          backendApiClient.getCached(
              "/api/v1/squadrons?size=1000&sort=name,asc",
              new ParameterizedTypeReference<>() {},
              true);
      if (p != null && p.content() != null) {
        return new ArrayList<>(p.content());
      }
    } catch (Exception e) {
      log.error("Failed to fetch squadrons", e);
    }
    return new ArrayList<>();
  }

  /**
   * Fetches the active-org-units catalog from {@code GET /api/v1/org-units/active} — the single
   * source for both Job Order owner-pickers. Job Orders are cross-staffel workspaces, so the picker
   * offers every active Staffel + Spezialkommando, not just the caller's memberships; each option
   * carries its {@code isProfitEligible} flag so {@link #addOwnerPickerOptions} can derive the
   * responsible picker from the requesting one without a second, authenticated SK-catalog call.
   * Read through the public client ({@code isPublic = true}) because the endpoint is {@code
   * permitAll}: the create form is reachable anonymously, and the authenticated client has no
   * bearer token for a guest — it would 401 and leave both pickers empty. Falls back to an empty
   * list on backend hiccup so the rest of the form stays renderable.
   *
   * @return picker options or empty list; never {@code null}.
   */
  private List<OrgUnitMembershipOptionDto> fetchActiveOrgUnitOptions() {
    try {
      List<OrgUnitMembershipOptionDto> options =
          backendApiClient.get(
              "/api/v1/org-units/active", new ParameterizedTypeReference<>() {}, true);
      return options != null ? options : List.of();
    } catch (Exception e) {
      log.warn("Failed to fetch active org units for Job Order owner-picker", e);
      return List.of();
    }
  }

  /**
   * Populates the create/edit form model with the two owner-picker option lists, both derived from
   * a single {@link #fetchActiveOrgUnitOptions} fetch: {@code requestingOptions} (any active
   * squadron or SK may be the customer) is the full list, and {@code responsibleOptions} (only
   * profit-eligible squadrons + SKs may process orders) is the {@code isProfitEligible} subset.
   * Each carries a boolean flag telling the template whether to render the SK optgroup. Sourcing
   * both from the one {@code permitAll} endpoint is what lets the anonymous create form show profit
   * SKs in the responsible picker — the previous split fetch needed an authenticated SK-catalog
   * call that dropped every SK for a guest.
   *
   * @param model the Thymeleaf model to populate.
   */
  private void addOwnerPickerOptions(Model model) {
    List<OrgUnitMembershipOptionDto> requestingOptions = fetchActiveOrgUnitOptions();
    List<OrgUnitMembershipOptionDto> responsibleOptions =
        requestingOptions.stream()
            .filter(o -> Boolean.TRUE.equals(o.isProfitEligible()))
            .collect(Collectors.toList());
    model.addAttribute("responsibleOptions", responsibleOptions);
    model.addAttribute("responsibleHasSpecialCommand", containsSpecialCommand(responsibleOptions));
    model.addAttribute("requestingOptions", requestingOptions);
    model.addAttribute("requestingHasSpecialCommand", containsSpecialCommand(requestingOptions));
  }

  /**
   * Pre-selects the configured intake Spezialkommando as the responsible (processing) unit on the
   * anonymous create form's two form beans, but only when the caller has not already chosen one (a
   * re-render after a failed submit keeps their pick). Mirrors the backend's guest fallback — a
   * guest order with no profit-eligible pick routes to the intake SK — so the form shows up front
   * the unit the order will actually land on. Never invoked for authenticated callers (they pick
   * their own unit). No-op when no intake SK is configured.
   *
   * @param model the Thymeleaf model carrying the two form beans.
   */
  private void preselectIntakeForGuest(Model model) {
    UUID intakeId = fetchIntakeSpecialCommandId();
    if (intakeId == null) {
      return;
    }
    if (model.getAttribute("jobOrderForm") instanceof JobOrderForm form
        && form.getResponsibleOrgUnitId() == null) {
      form.setResponsibleOrgUnitId(intakeId);
    }
    if (model.getAttribute("jobOrderItemForm") instanceof JobOrderItemForm itemForm
        && itemForm.getResponsibleOrgUnitId() == null) {
      itemForm.setResponsibleOrgUnitId(intakeId);
    }
  }

  /**
   * Resolves the configured intake Spezialkommando id (system setting {@code
   * job_order.intake_special_command_id}) for the anonymous responsible-picker preselection. Read
   * through the public client because the settings endpoint is {@code permitAll} and the create
   * form is reachable by guests. Returns {@code null} when the setting is unset / blank / malformed
   * or the backend call fails, so the picker simply renders with nothing preselected.
   *
   * @return the intake SK id, or {@code null} when none is configured / resolvable.
   */
  private UUID fetchIntakeSpecialCommandId() {
    try {
      SystemSettingDto setting =
          backendApiClient.get(
              "/api/v1/settings/job_order.intake_special_command_id", SystemSettingDto.class, true);
      if (setting != null && setting.value() != null && !setting.value().isBlank()) {
        return UUID.fromString(setting.value().trim());
      }
    } catch (Exception e) {
      log.warn("Failed to resolve intake Spezialkommando id for responsible-picker preselection");
    }
    return null;
  }

  private UUID getCurrentUserId(OidcUser principal) {
    if (principal == null) {
      return null;
    }
    try {
      return UUID.fromString(principal.getSubject());
    } catch (Exception e) {
      try {
        UserDto me = backendApiClient.get("/api/v1/users/me", UserDto.class);
        return me != null ? me.id() : null;
      } catch (Exception ex) {
        log.warn("Failed to get current user ID from backend: {}", ex.getMessage());
        return null;
      }
    }
  }

  private boolean isLogistician(OidcUser principal) {
    if (principal == null) {
      return false;
    }

    org.springframework.security.core.Authentication auth =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    Collection<? extends GrantedAuthority> authorities =
        (auth != null) ? auth.getAuthorities() : principal.getAuthorities();

    Collection<? extends GrantedAuthority> reachableAuthorities =
        roleHierarchy.getReachableGrantedAuthorities(authorities);
    log.debug(
        "JobOrder: Checking logistician status for user {}. Original authorities: {}."
            + " Reachable authorities: {}",
        principal.getName(),
        authorities,
        reachableAuthorities);
    boolean result =
        reachableAuthorities.stream()
            .anyMatch(
                a ->
                    a.getAuthority().equals("ROLE_LOGISTICIAN")
                        || a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_OFFICER"));
    log.debug("JobOrder: Is logistician: {}", result);
    return result;
  }
}

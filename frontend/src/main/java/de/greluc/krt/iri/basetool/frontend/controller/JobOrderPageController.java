package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.*;
import de.greluc.krt.iri.basetool.frontend.model.dto.UpdateJobOrderStatusDto;
import de.greluc.krt.iri.basetool.frontend.model.form.JobOrderForm;
import de.greluc.krt.iri.basetool.frontend.model.form.JobOrderHandoverForm;
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
import org.springframework.web.bind.annotation.*;
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
   * Renders the job-order list ({@code /orders}). The status filter follows a three-stage
   * precedence: explicit query parameter wins, otherwise a persisted cookie ({@code
   * orders_filter_status}, validated against {@link #VALID_STATUSES}), otherwise the default of
   * {@code OPEN} + {@code IN_PROGRESS}. When the user sets an explicit filter, it gets written back
   * into the cookie with a 30-day TTL.
   *
   * @param status optional explicit status filter
   * @param cookieStatus previous persisted filter from the cookie
   * @param response servlet response, used to update the persistence cookie
   * @param model Thymeleaf model populated with orders, selected statuses and the aging thresholds
   *     for the row-color rendering
   * @return the {@code orders-index} view name
   */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public String viewOrders(
      @RequestParam(required = false) List<String> status,
      @CookieValue(name = "orders_filter_status", required = false) String cookieStatus,
      HttpServletResponse response,
      Model model) {
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
      response.addCookie(cookie);
    }

    List<JobOrderDto> orders = new ArrayList<>();
    int yellowDays = 30;
    int redDays = 90;
    try {
      String statusParam = String.join(",", status);
      PageResponse<JobOrderDto> p =
          backendApiClient.get(
              "/api/v1/orders?size=1000&sort=priority,asc&status=" + statusParam,
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
    model.addAttribute("ageYellowDays", yellowDays);
    model.addAttribute("ageRedDays", redDays);
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
      @PathVariable UUID id, Model model, @AuthenticationPrincipal OidcUser principal) {
    try {
      JobOrderDto order = backendApiClient.get("/api/v1/orders/" + id, JobOrderDto.class);
      model.addAttribute("order", order);
      model.addAttribute("currentUserId", getCurrentUserId(principal));

      boolean canAssign = isLogistician(principal);
      model.addAttribute("isLogistician", canAssign);

      if (canAssign) {
        model.addAttribute("users", fetchUsers());
        model.addAttribute("materials", fetchMaterials());
        model.addAttribute("squadrons", fetchSquadrons());

        if (!model.containsAttribute("jobOrderForm")) {
          JobOrderForm form = new JobOrderForm();
          form.setSquadron(order.squadron());
          form.setHandle(order.handle());
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
        handoverForm.setRecipientSquadron(order.squadron());
        model.addAttribute("handoverForm", handoverForm);
      }
    } catch (Exception e) {
      log.error("Failed to fetch order", e);
      log.error("Failed to load job order", e);
      model.addAttribute("error", "error.joborder.load.details");
      return "redirect:/orders";
    }
    return "orders-detail";
  }

  /**
   * Renders the create-order form ({@code /orders/create}). Seeds the materials, job-types and
   * locations catalogs; the {@code source} parameter threads through so the post-save redirect can
   * return to the originating page.
   *
   * @param source optional origin marker
   * @param model Thymeleaf model populated with form and reference catalogs
   * @return the {@code order-create} view name
   */
  @GetMapping("/create")
  public String viewCreateForm(@RequestParam(required = false) String source, Model model) {
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
    model.addAttribute("materials", fetchMaterials());
    model.addAttribute("squadrons", fetchSquadrons());
    return "orders-create";
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
      RedirectAttributes redirectAttributes,
      @AuthenticationPrincipal OidcUser principal) {
    try {
      List<CreateJobOrderMaterialDto> materials =
          form.getMaterials().stream()
              .filter(m -> m.getMaterialId() != null && m.getAmount() != null && m.getAmount() > 0)
              .map(m -> new CreateJobOrderMaterialDto(m.getMaterialId(), 750, m.getAmount()))
              .collect(Collectors.toList());

      if (materials.isEmpty()) {
        redirectAttributes.addFlashAttribute("errorToast", "error.joborder.material.invalid");
        redirectAttributes.addFlashAttribute("jobOrderForm", form);
        return "redirect:/orders/create"
            + (form.getSource() != null ? "?source=" + form.getSource() : "");
      }

      CreateJobOrderDto dto =
          new CreateJobOrderDto(form.getSquadron(), form.getHandle(), materials, form.getVersion());
      backendApiClient.post("/api/v1/orders", dto, JobOrderDto.class, true);
      redirectAttributes.addFlashAttribute("successToast", "success.joborder.create");

      if (principal == null) {
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
   * AJAX status transition endpoint. Updates the job order's status (OPEN → IN_PROGRESS →
   * COMPLETED, REJECTED). Backend enforces the state machine and rejects illegal transitions with a
   * 400.
   *
   * @return updated order on success, propagated backend status code on failure
   */
  @PostMapping("/{id}/status")
  @PreAuthorize("isAuthenticated()")
  @org.springframework.web.bind.annotation.ResponseBody
  public org.springframework.http.ResponseEntity<JobOrderDto> updateStatus(
      @PathVariable UUID id, @RequestBody UpdateJobOrderStatusDto dto) {
    try {
      JobOrderDto result =
          backendApiClient.put("/api/v1/orders/" + id + "/status", dto, JobOrderDto.class);
      return org.springframework.http.ResponseEntity.ok(result);
    } catch (BackendServiceException bse) {
      log.error("Failed to update status for order {}: {}", id, bse.getMessage());
      return org.springframework.http.ResponseEntity.status(bse.getStatusCode()).build();
    } catch (Exception e) {
      log.error("Failed to update status for order {}", id, e);
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
              .map(m -> new CreateJobOrderMaterialDto(m.getMaterialId(), 750, m.getAmount()))
              .collect(Collectors.toList());

      if (materials.isEmpty()) {
        redirectAttributes.addFlashAttribute("errorToast", "error.joborder.material.invalid");
        redirectAttributes.addFlashAttribute("jobOrderForm", form);
        return "redirect:/orders/" + id;
      }

      CreateJobOrderDto dto =
          new CreateJobOrderDto(form.getSquadron(), form.getHandle(), materials, form.getVersion());
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
   * Adds an assignee to the job order. The user-picker is fed by {@link UserProxyController}'s
   * search endpoint.
   *
   * @return redirect to the order detail page
   */
  @PostMapping("/{id}/assignees")
  @PreAuthorize("isAuthenticated()")
  public String addAssignee(
      @PathVariable UUID id, @RequestParam UUID userId, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.post(
          "/api/v1/orders/" + id + "/assignees/" + userId, null, JobOrderDto.class);
      redirectAttributes.addFlashAttribute("successToast", "success.joborder.assignee.added");
    } catch (Exception e) {
      log.error("Failed to add assignee", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.joborder.assignee.add");
    }
    return "redirect:/orders/" + id;
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
        } catch (Exception eIso) {
          try {
            handoverTime =
                LocalDateTime.parse(rawHandoverTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
          } catch (Exception eLocal) {
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
   * Removes an assignee from the job order.
   *
   * @return redirect to the order detail page
   */
  @PostMapping("/{id}/assignees/remove")
  @PreAuthorize("isAuthenticated()")
  public String removeAssignee(
      @PathVariable UUID id, @RequestParam UUID userId, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/orders/" + id + "/assignees/" + userId, JobOrderDto.class);
      redirectAttributes.addFlashAttribute("successToast", "success.joborder.assignee.removed");
    } catch (Exception e) {
      log.error("Failed to remove assignee", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.joborder.assignee.remove");
    }
    return "redirect:/orders/" + id;
  }

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
              new ParameterizedTypeReference<List<MaterialDto>>() {});
      if (list != null) {
        return new ArrayList<>(list);
      }
    } catch (Exception e) {
      log.error("Failed to fetch job-order materials", e);
    }
    return new ArrayList<>();
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

  private UUID getCurrentUserId(OidcUser principal) {
    if (principal == null) return null;
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
    if (principal == null) return false;

    org.springframework.security.core.Authentication auth =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    Collection<? extends GrantedAuthority> authorities =
        (auth != null) ? auth.getAuthorities() : principal.getAuthorities();

    Collection<? extends GrantedAuthority> reachableAuthorities =
        roleHierarchy.getReachableGrantedAuthorities(authorities);
    log.info(
        "[DEBUG_LOG] JobOrder: Checking logistician status for user {}. Original authorities: {}. Reachable authorities: {}",
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
    log.info("[DEBUG_LOG] JobOrder: Is logistician: {}", result);
    return result;
  }
}

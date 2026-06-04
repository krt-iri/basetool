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

import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.LocationDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionListDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OrgUnitMembershipOptionDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.RefineryOrderDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.RefineryOrderListDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.RefineryOrderStoreDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.RefineryOrderStoreItemDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.RefiningMethodDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.SystemSettingDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.frontend.model.form.RefineryGoodForm;
import de.greluc.krt.iri.basetool.frontend.model.form.RefineryOrderForm;
import de.greluc.krt.iri.basetool.frontend.model.form.RefineryOrderStoreForm;
import de.greluc.krt.iri.basetool.frontend.model.form.RefineryOrderStoreItemForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** REST controller for RefineryOrderPageController endpoints. */
@Controller
@RequestMapping("/refinery-orders")
@RequiredArgsConstructor
@Slf4j
public class RefineryOrderPageController {

  private final BackendApiClient backendApiClient;
  private final RoleHierarchy roleHierarchy;

  /**
   * Parses the start instant submitted by the form as a UTC {@link java.time.Instant}.
   *
   * <p>Why: All timestamps in the system are persisted and transported solely in UTC (AGENTS.md
   * "Consistent Date/Time/Zone Handling"). The frontend (datetime-splitter.js) therefore always
   * sends an ISO-Instant string with 'Z' or with an offset. Backward compatible forms are
   * additionally parsed (date only, local DateTime without zone - the latter is defensively
   * interpreted as UTC to avoid an implicit and DST-prone use of {@code ZoneId.systemDefault()}).
   */
  static java.time.Instant parseStartedAt(String raw) {
    if (raw == null || raw.trim().isEmpty()) {
      return java.time.Instant.now();
    }
    String input = raw.trim();
    try {
      return java.time.Instant.parse(input);
    } catch (Exception ignored) {
      /* not an instant */
    }
    try {
      return java.time.OffsetDateTime.parse(input).toInstant();
    } catch (Exception ignored) {
      /* not offset date time */
    }
    if (input.length() == 10) {
      // Date only -> start of day in UTC
      return java.time.LocalDate.parse(input).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    }
    // LocalDateTime without zone -> defensively interpret as UTC to avoid a double
    // DST conversion. Correct inputs always carry 'Z' or an offset.
    return java.time.LocalDateTime.parse(input).toInstant(java.time.ZoneOffset.UTC);
  }

  /**
   * Renders the refinery-order list ({@code /refinery-orders}). Default filter is {@code
   * OPEN}+{@code IN_PROGRESS} so the operational view is uncluttered by completed/canceled orders.
   * The {@code onlyMine} toggle switches between the all-orders endpoint and the per-user endpoint
   * — both return at most 1000 rows in one page, sorted by {@code startedAt} desc.
   *
   * @param status optional list of statuses to include
   * @param onlyMine if true, restrict to the caller's own orders
   * @param model Thymeleaf model populated with {@code orders}, selected statuses and the full
   *     status list for the filter UI
   * @param principal authenticated OIDC user (used to derive the logistician hint)
   * @return the {@code refinery-orders-index} view name
   */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public String viewOrders(
      @RequestParam(required = false) List<String> status,
      @RequestParam(required = false) Boolean onlyMine,
      Model model,
      @AuthenticationPrincipal OidcUser principal) {
    if (status == null || status.isEmpty()) {
      status = List.of("OPEN", "IN_PROGRESS");
    }

    boolean isLogistician = isLogistician(principal);

    List<RefineryOrderListDto> orders = new ArrayList<>();
    try {
      String statusParam = String.join(",", status);
      // Now everyone sees all orders (Read-only for normal members)
      String url =
          "/api/v1/refinery-orders/all?size=1000&sort=startedAt,desc&status=" + statusParam;
      if (Boolean.TRUE.equals(onlyMine)) {
        url =
            "/api/v1/refinery-orders/my-orders?size=1000&sort=startedAt,desc&status=" + statusParam;
      }
      PageResponse<RefineryOrderListDto> p =
          backendApiClient.get(url, new ParameterizedTypeReference<>() {});
      if (p != null && p.content() != null) {
        orders = new ArrayList<>(p.content());
      }
    } catch (Exception e) {
      log.error("Failed to fetch refinery orders", e);
      model.addAttribute("errorToast", "error.refineryorder.load");
    }
    model.addAttribute("orders", orders);
    model.addAttribute("selectedStatuses", status);
    model.addAttribute("onlyMine", Boolean.TRUE.equals(onlyMine));
    model.addAttribute("allStatuses", List.of("OPEN", "IN_PROGRESS", "COMPLETED", "CANCELED"));
    return "refinery-orders-index";
  }

  /**
   * Renders the create-order form ({@code /refinery-orders/create}). Seeds an empty form with the
   * current user as the default owner; loads the materials, methods, locations, missions, user list
   * and the refinery rounding mode for the form's dropdowns and computations.
   *
   * @param source optional origin marker for the return-to-page link after save
   * @param model Thymeleaf model populated with the form and reference catalogs
   * @param principal authenticated OIDC user (used to derive the default owner)
   * @return the {@code refinery-orders-create} view name
   */
  @GetMapping("/create")
  @PreAuthorize("isAuthenticated()")
  public String viewCreateForm(
      @RequestParam(required = false) String source,
      Model model,
      @AuthenticationPrincipal OidcUser principal) {
    boolean isLogistician = isLogistician(principal);
    model.addAttribute("isLogistician", isLogistician);

    if (!model.containsAttribute("refineryOrderForm")) {
      RefineryOrderForm form = new RefineryOrderForm();
      form.setSource(source);
      UUID currentUserId = getCurrentUserId(principal);
      if (currentUserId != null) {
        form.setOwnerId(currentUserId);
      }
      model.addAttribute("refineryOrderForm", form);
    } else {
      RefineryOrderForm form = (RefineryOrderForm) model.getAttribute("refineryOrderForm");
      if (form != null && form.getSource() == null) {
        form.setSource(source);
      }
    }
    model.addAttribute("materials", fetchMaterials());
    model.addAttribute("methods", fetchMethods());
    model.addAttribute("locations", fetchLocations());
    model.addAttribute("missions", fetchMissions());
    model.addAttribute("users", fetchUsers());
    model.addAttribute("roundingMode", fetchRoundingMode());

    // Preload the UEX yield map for any location already picked on the form (POST-validation
    // re-render), so the bonus badges render on first paint without an extra round-trip. A
    // fresh GET has no location yet, falls through to an empty map, and the client-side
    // onLocationChange handler will fetch the map when the user picks a refinery.
    RefineryOrderForm formForYields = (RefineryOrderForm) model.getAttribute("refineryOrderForm");
    UUID preselectedLocationId = formForYields != null ? formForYields.getLocationId() : null;
    model.addAttribute("materialYieldBonuses", fetchYieldsForLocation(preselectedLocationId));
    model.addAttribute("ownerOptions", fetchOwnerPickerOptions(formForYields, principal));
    return "refinery-orders-create";
  }

  /**
   * Persists a new refinery order from the create form.
   *
   * <p>Translates each form-bound {@code RefineryGoodForm} into a {@code RefineryGoodDto} with
   * minimal id-only Material/Location/User stubs (the backend re-hydrates the full records from the
   * ids). Empty goods short-circuit with a localized error before reaching the backend. On failure
   * the form is flashed back so the user keeps their input.
   *
   * @param form refinery-order create form
   * @param bindingResult validation errors carrier
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /refinery-orders/create} on failure (preserves input), otherwise to
   *     the source page or the list
   */
  @PostMapping("/create")
  @PreAuthorize("isAuthenticated()")
  public String createOrder(
      @Valid @ModelAttribute("refineryOrderForm") RefineryOrderForm form,
      BindingResult bindingResult,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      redirectAttributes.addFlashAttribute("errorToast", "error.refineryorder.create.failed");
      redirectAttributes.addFlashAttribute("refineryOrderForm", form);
      return "redirect:/refinery-orders/create"
          + (form.getSource() != null ? "?source=" + form.getSource() : "");
    }
    try {
      List<de.greluc.krt.iri.basetool.frontend.model.dto.RefineryGoodDto> goodsDto =
          new ArrayList<>();
      for (RefineryGoodForm g : form.getGoods()) {
        if (g.getInputMaterialId() != null && g.getInputQuantity() != null) {
          de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto inMat =
              new de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto(
                  g.getInputMaterialId(),
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null);
          de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto outMat =
              g.getOutputMaterialId() != null
                  ? new de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto(
                      g.getOutputMaterialId(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null)
                  : null;
          goodsDto.add(
              new de.greluc.krt.iri.basetool.frontend.model.dto.RefineryGoodDto(
                  null,
                  inMat,
                  g.getInputQuantity(),
                  outMat,
                  g.getOutputQuantity(),
                  g.getQuality() != null ? g.getQuality() : 0,
                  null));
        }
      }

      if (goodsDto.isEmpty()) {
        redirectAttributes.addFlashAttribute("errorToast", "error.refineryorder.material.invalid");
        redirectAttributes.addFlashAttribute("refineryOrderForm", form);
        return "redirect:/refinery-orders/create"
            + (form.getSource() != null ? "?source=" + form.getSource() : "");
      }

      java.time.Instant startedAtTime = parseStartedAt(form.getStartedAt());

      RefineryOrderDto orderDto =
          new RefineryOrderDto(
              null,
              form.getOwnerId() != null
                  ? new de.greluc.krt.iri.basetool.frontend.model.dto.UserReferenceDto(
                      form.getOwnerId(), null, null, null, null)
                  : null,
              form.getLocationId() != null
                  ? new de.greluc.krt.iri.basetool.frontend.model.dto.LocationDto(
                      form.getLocationId(), null, null, false, false, null)
                  : null,
              form.getMissionId() != null
                  ? new de.greluc.krt.iri.basetool.frontend.model.dto.MissionReferenceDto(
                      form.getMissionId(), null, null, null)
                  : null,
              startedAtTime,
              (long)
                  ((form.getDurationHours() != null ? form.getDurationHours() : 0) * 60
                      + (form.getDurationMinutes() != null ? form.getDurationMinutes() : 0)),
              nullToZero(form.getExpenses()),
              nullToZero(form.getOtherExpenses()),
              nullToZero(form.getOreSales()),
              null,
              form.getRefiningMethodId() != null
                  ? new de.greluc.krt.iri.basetool.frontend.model.dto.RefiningMethodDto(
                      form.getRefiningMethodId(), null, null, null, null, null, null)
                  : null,
              goodsDto,
              form.getStatus() != null
                  ? form.getStatus()
                  : de.greluc.krt.iri.basetool.frontend.model.dto.RefineryOrderStatus.OPEN,
              null,
              form.getVersion(),
              form.getOwningOrgUnitId());

      log.info("Sending refinery order DTO: {}", orderDto);

      backendApiClient.post("/api/v1/refinery-orders", orderDto, RefineryOrderDto.class);
      redirectAttributes.addFlashAttribute("successToast", "success.refineryorder.create");

      if ("index".equals(form.getSource())) {
        return "redirect:/refinery-orders";
      }
      return "redirect:/refinery-orders";
    } catch (Exception e) {
      log.error("Failed to create refinery order", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.refineryorder.create.failed");
      redirectAttributes.addFlashAttribute("refineryOrderForm", form);
      return "redirect:/refinery-orders/create"
          + (form.getSource() != null ? "?source=" + form.getSource() : "");
    }
  }

  /**
   * Renders the detail view of a single refinery order ({@code /refinery-orders/{id}}).
   *
   * <p>The {@code canEdit} flag is resolved here (not in the template): logisticians can edit every
   * order; the order's owner can edit their own. Backend's PUT enforces the same rule so this is
   * purely a UX gate.
   *
   * @param id refinery order id
   * @param model Thymeleaf model populated with order, edit/role flags and the dropdown catalogs
   * @param principal authenticated OIDC user (used to derive owner and logistician flags)
   * @return the {@code refinery-orders-details} view name
   */
  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public String viewOrderDetail(
      @PathVariable UUID id, Model model, @AuthenticationPrincipal OidcUser principal) {
    boolean isLogistician = isLogistician(principal);

    if (!model.containsAttribute("refineryOrderForm") || !model.containsAttribute("storeForm")) {
      try {
        RefineryOrderDto orderDto =
            backendApiClient.get("/api/v1/refinery-orders/" + id, RefineryOrderDto.class);
        UUID currentUserId = getCurrentUserId(principal);

        boolean isOwner =
            currentUserId != null
                && orderDto.owner() != null
                && orderDto.owner().id() != null
                && orderDto.owner().id().equals(currentUserId);

        boolean canEdit = isLogistician || isOwner;
        model.addAttribute("canEdit", canEdit);
        model.addAttribute("isLogistician", isLogistician);
        model.addAttribute("order", orderDto);

        if (!model.containsAttribute("refineryOrderForm")) {
          RefineryOrderForm form = new RefineryOrderForm();

          if (orderDto.startedAt() != null) {
            form.setStartedAt(orderDto.startedAt().toString());
          }
          if (orderDto.durationMinutes() != null) {
            form.setDurationHours((int) (orderDto.durationMinutes() / 60));
            form.setDurationMinutes((int) (orderDto.durationMinutes() % 60));
          }
          form.setExpenses(orderDto.expenses() != null ? orderDto.expenses() : 0d);
          form.setOtherExpenses(orderDto.otherExpenses() != null ? orderDto.otherExpenses() : 0d);
          form.setOreSales(orderDto.oreSales() != null ? orderDto.oreSales() : 0d);
          if (orderDto.location() != null) {
            form.setLocationId(orderDto.location().id());
          }
          if (orderDto.mission() != null) {
            form.setMissionId(orderDto.mission().id());
          }
          if (orderDto.refiningMethod() != null) {
            form.setRefiningMethodId(orderDto.refiningMethod().id());
          }
          if (orderDto.owner() != null) {
            form.setOwnerId(orderDto.owner().id());
          }
          form.setStatus(orderDto.status());
          form.setVersion(orderDto.version());

          if (orderDto.goods() != null && !orderDto.goods().isEmpty()) {
            List<RefineryGoodForm> goodsForm = new ArrayList<>();
            for (de.greluc.krt.iri.basetool.frontend.model.dto.RefineryGoodDto goodDto :
                orderDto.goods()) {
              RefineryGoodForm goodForm = new RefineryGoodForm();
              if (goodDto.inputMaterial() != null) {
                goodForm.setInputMaterialId(goodDto.inputMaterial().id());
              }
              if (goodDto.outputMaterial() != null) {
                goodForm.setOutputMaterialId(goodDto.outputMaterial().id());
              }
              goodForm.setInputQuantity(goodDto.inputQuantity());
              goodForm.setOutputQuantity(goodDto.outputQuantity());
              goodForm.setQuality(goodDto.quality());
              goodsForm.add(goodForm);
            }
            form.setGoods(goodsForm);
          }

          model.addAttribute("refineryOrderForm", form);
        }

        if (!model.containsAttribute("storeForm")) {
          RefineryOrderStoreForm storeForm = new RefineryOrderStoreForm();
          if (orderDto.goods() != null) {
            String roundingMode = fetchRoundingMode();
            for (de.greluc.krt.iri.basetool.frontend.model.dto.RefineryGoodDto good :
                orderDto.goods()) {
              if (good.outputMaterial() != null) {
                RefineryOrderStoreItemForm storeItem = new RefineryOrderStoreItemForm();
                storeItem.setMaterialId(good.outputMaterial().id());
                storeItem.setMaterialName(good.outputMaterial().name());

                // The output quantity from the refinery good is stored as units (centi-SCU)
                // when the material is SCU-measured: 100 units == 1 SCU. We divide by 100 to
                // get the SCU value the user will enter in the store dialog. Default to the
                // SCU branch whenever the material's quantityType is anything other than the
                // explicit string "PIECE": UEX-imported materials historically have a NULL
                // quantity_type (the UEX sync never set the field — see issue #230), and a
                // refinery never produces piece-counted goods anyway, so treating "unknown"
                // as SCU here is both safer (avoids 100x over-booking) and matches the
                // domain. Backed by the V95 migration that backfills NULL -> 'SCU'.
                double amount = 0.0;
                String materialQuantityType = good.outputMaterial().quantityType();
                if (good.outputQuantity() != null) {
                  if ("PIECE".equals(materialQuantityType)) {
                    amount = good.outputQuantity();
                  } else {
                    java.math.RoundingMode rm;
                    try {
                      rm = java.math.RoundingMode.valueOf(roundingMode);
                    } catch (Exception e) {
                      rm = java.math.RoundingMode.HALF_UP;
                    }
                    amount =
                        java.math.BigDecimal.valueOf(good.outputQuantity() / 100.0)
                            .setScale(3, rm)
                            .doubleValue();
                  }
                  storeItem.setAmountFixed(true);
                } else {
                  storeItem.setAmountFixed(false);
                }
                storeItem.setAmount(amount);
                // Normalize the per-row quantityType the template sees so the unit label
                // ("(SCU)" / "(Stück)") matches the converted amount above.
                storeItem.setQuantityType("PIECE".equals(materialQuantityType) ? "PIECE" : "SCU");

                storeItem.setQuality(good.quality() != null ? good.quality() : 0);
                if (orderDto.location() != null) {
                  storeItem.setLocationId(orderDto.location().id());
                }
                if (currentUserId != null) {
                  storeItem.setUserId(currentUserId);
                }
                storeForm.getItems().add(storeItem);
              }
            }
          }
          model.addAttribute("storeForm", storeForm);
        }
      } catch (Exception e) {
        log.error("Failed to fetch refinery order details", e);
        model.addAttribute("errorToast", "error.refineryorder.load");
        return "redirect:/refinery-orders";
      }
    }
    model.addAttribute("orderId", id);
    model.addAttribute("materials", fetchMaterials());
    model.addAttribute("methods", fetchMethods());
    model.addAttribute("locations", fetchLocations());
    model.addAttribute("allLocations", fetchAllLocations());
    RefineryOrderForm formInModel = (RefineryOrderForm) model.getAttribute("refineryOrderForm");
    UUID preserveMissionId = formInModel != null ? formInModel.getMissionId() : null;
    model.addAttribute("missions", fetchMissions(preserveMissionId));
    model.addAttribute("users", fetchUsers());
    model.addAttribute("jobOrders", fetchActiveJobOrders());
    model.addAttribute("roundingMode", fetchRoundingMode());

    // Pre-load the UEX yield map for the order's current location so the detail page can render
    // the bonus badge for every input material in the dropdown on first paint, without an extra
    // network round-trip. The same map is then refreshed client-side via the AJAX endpoint below
    // whenever the user picks a different refinery from the location dropdown.
    UUID currentLocationId = null;
    Object orderAttr = model.getAttribute("order");
    if (orderAttr instanceof RefineryOrderDto orderForLookup && orderForLookup.location() != null) {
      currentLocationId = orderForLookup.location().id();
    }
    model.addAttribute("materialYieldBonuses", fetchYieldsForLocation(currentLocationId));
    return "refinery-orders-details";
  }

  /**
   * AJAX endpoint that proxies the backend's {@code GET
   * /api/v1/refinery-orders/locations/{locationId}/yields} so the detail page's bonus badges can
   * refresh client-side when the user picks a different refinery without a full page reload.
   * Returns the UEX {@code materialId -> percent} map as JSON (positive = bonus, negative = malus,
   * 0 = explicit baseline, absent key = no UEX row known for the (location, material) pair). Errors
   * land as an empty map rather than a 5xx so a transient backend hiccup just falls back to an
   * empty badge instead of breaking the form.
   *
   * @param locationId target refinery location
   * @return per-material yield map keyed by material UUID
   */
  @GetMapping("/locations/{locationId}/yields")
  @PreAuthorize("isAuthenticated()")
  @ResponseBody
  public Map<String, Integer> getYieldsForLocation(@PathVariable UUID locationId) {
    return fetchYieldsForLocation(locationId);
  }

  /**
   * Pulls the per-material UEX bonus/malus map from the backend for {@code locationId}. Wraps every
   * failure mode in an empty map: a {@code null} id (the order has no location picked yet), an
   * unknown id (the backend returns an empty map by design), or a transient network/backend error
   * (logged at WARN). The shared yield-badge JS module treats "empty map" identically to "no UEX
   * data" so all three branches fall through cleanly on both the create and the detail page.
   */
  private Map<String, Integer> fetchYieldsForLocation(UUID locationId) {
    if (locationId == null) {
      return Map.of();
    }
    try {
      Map<String, Integer> yields =
          backendApiClient.get(
              "/api/v1/refinery-orders/locations/" + locationId + "/yields",
              new ParameterizedTypeReference<>() {});
      return yields != null ? yields : Map.of();
    } catch (Exception e) {
      log.warn("Failed to fetch refinery yields for location {}: {}", locationId, e.getMessage());
      return Map.of();
    }
  }

  /**
   * Persists an edit to an existing refinery order. Mirrors {@link #createOrder} for the
   * stubs-by-id pattern; additionally carries the optimistic-lock {@code version} from the form.
   * Empty goods list short-circuits with a localized error before the backend call.
   *
   * @param id refinery order id
   * @param form refinery-order edit form
   * @param bindingResult validation errors carrier
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /refinery-orders/{id}} on failure, otherwise to the list
   */
  @PostMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public String updateOrder(
      @PathVariable UUID id,
      @Valid @ModelAttribute("refineryOrderForm") RefineryOrderForm form,
      BindingResult bindingResult,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      redirectAttributes.addFlashAttribute("errorToast", "error.refineryorder.update.failed");
      redirectAttributes.addFlashAttribute("refineryOrderForm", form);
      return "redirect:/refinery-orders/" + id;
    }
    try {
      List<de.greluc.krt.iri.basetool.frontend.model.dto.RefineryGoodDto> goodsDto =
          new ArrayList<>();
      for (RefineryGoodForm g : form.getGoods()) {
        if (g.getInputMaterialId() != null && g.getInputQuantity() != null) {
          de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto inMat =
              new de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto(
                  g.getInputMaterialId(),
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null);
          de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto outMat =
              g.getOutputMaterialId() != null
                  ? new de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto(
                      g.getOutputMaterialId(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null)
                  : null;
          goodsDto.add(
              new de.greluc.krt.iri.basetool.frontend.model.dto.RefineryGoodDto(
                  null,
                  inMat,
                  g.getInputQuantity(),
                  outMat,
                  g.getOutputQuantity(),
                  g.getQuality() != null ? g.getQuality() : 0,
                  null));
        }
      }

      if (goodsDto.isEmpty()) {
        redirectAttributes.addFlashAttribute("errorToast", "error.refineryorder.material.invalid");
        redirectAttributes.addFlashAttribute("refineryOrderForm", form);
        return "redirect:/refinery-orders/" + id;
      }

      java.time.Instant startedAtTime = parseStartedAt(form.getStartedAt());

      RefineryOrderDto orderDto =
          new RefineryOrderDto(
              id,
              form.getOwnerId() != null
                  ? new de.greluc.krt.iri.basetool.frontend.model.dto.UserReferenceDto(
                      form.getOwnerId(), null, null, null, null)
                  : null,
              form.getLocationId() != null
                  ? new de.greluc.krt.iri.basetool.frontend.model.dto.LocationDto(
                      form.getLocationId(), null, null, false, false, null)
                  : null,
              form.getMissionId() != null
                  ? new de.greluc.krt.iri.basetool.frontend.model.dto.MissionReferenceDto(
                      form.getMissionId(), null, null, null)
                  : null,
              startedAtTime,
              (long)
                  ((form.getDurationHours() != null ? form.getDurationHours() : 0) * 60
                      + (form.getDurationMinutes() != null ? form.getDurationMinutes() : 0)),
              nullToZero(form.getExpenses()),
              nullToZero(form.getOtherExpenses()),
              nullToZero(form.getOreSales()),
              null,
              form.getRefiningMethodId() != null
                  ? new de.greluc.krt.iri.basetool.frontend.model.dto.RefiningMethodDto(
                      form.getRefiningMethodId(), null, null, null, null, null, null)
                  : null,
              goodsDto,
              form.getStatus() != null
                  ? form.getStatus()
                  : de.greluc.krt.iri.basetool.frontend.model.dto.RefineryOrderStatus.OPEN,
              null,
              form.getVersion(),
              // Edit path: owningOrgUnitId is not editable, the existing stamp survives.
              null);

      backendApiClient.put("/api/v1/refinery-orders/" + id, orderDto, RefineryOrderDto.class);
      redirectAttributes.addFlashAttribute("successToast", "success.refineryorder.update");
    } catch (Exception e) {
      log.error("Failed to update refinery order", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.refineryorder.update.failed");
      redirectAttributes.addFlashAttribute("refineryOrderForm", form);
      return "redirect:/refinery-orders/" + id;
    }
    return "redirect:/refinery-orders";
  }

  /**
   * Cancels a refinery order. The backend treats this as a soft-cancel (status transition);
   * fine-grained authorization is enforced backend-side, the frontend only authenticates.
   *
   * @param id refinery order id
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /refinery-orders}
   */
  @PostMapping("/{id}/delete")
  @PreAuthorize("isAuthenticated()")
  public String deleteOrder(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/refinery-orders/" + id, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "success.refineryorder.cancel");
    } catch (Exception e) {
      log.error("Failed to cancel refinery order", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.refineryorder.cancel.failed");
    }
    return "redirect:/refinery-orders";
  }

  /**
   * Completes a refinery order by storing the refined output as inventory entries.
   *
   * <p>The store form picks the target location and (optionally) the receiving user/job-order; the
   * backend computes the inventory rows from the order's goods and the chosen target. A validation
   * failure flashes the form back and re-opens the store modal on the detail page.
   *
   * @param id refinery order id
   * @param form store form
   * @param bindingResult validation errors carrier
   * @param redirectAttributes flash attributes carrier
   * @return redirect to the order detail on failure, otherwise to the list
   */
  @PostMapping("/{id}/store")
  @PreAuthorize("isAuthenticated()")
  public String storeOrder(
      @PathVariable UUID id,
      @Valid @ModelAttribute("storeForm") RefineryOrderStoreForm form,
      BindingResult bindingResult,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      redirectAttributes.addFlashAttribute("errorToast", "error.refineryorder.store.invalid");
      redirectAttributes.addFlashAttribute("storeForm", form);
      redirectAttributes.addFlashAttribute("showStoreModal", true);
      return "redirect:/refinery-orders/" + id;
    }
    try {
      List<RefineryOrderStoreItemDto> dtoList = new ArrayList<>();
      for (RefineryOrderStoreItemForm f : form.getItems()) {
        dtoList.add(
            new RefineryOrderStoreItemDto(
                f.getMaterialId(),
                f.getLocationId(),
                f.getQuality(),
                f.getAmount(),
                f.getUserId(),
                f.getJobOrderId(),
                f.getNote()));
      }
      RefineryOrderStoreDto dto = new RefineryOrderStoreDto(dtoList);
      backendApiClient.post("/api/v1/refinery-orders/" + id + "/store", dto, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "success.refineryorder.store");
    } catch (Exception e) {
      log.error("Failed to store refinery order", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.refineryorder.store.failed");
      redirectAttributes.addFlashAttribute("storeForm", form);
      redirectAttributes.addFlashAttribute("showStoreModal", true);
      return "redirect:/refinery-orders/" + id;
    }
    return "redirect:/refinery-orders";
  }

  private List<MaterialDto> fetchMaterials() {
    try {
      PageResponse<MaterialDto> p =
          backendApiClient.getCached(
              "/api/v1/materials?size=1000", new ParameterizedTypeReference<>() {}, true);
      if (p != null && p.content() != null) {
        return new ArrayList<>(p.content());
      }
    } catch (Exception e) {
      log.error("Failed to fetch materials", e);
    }
    return new ArrayList<>();
  }

  private List<RefiningMethodDto> fetchMethods() {
    try {
      PageResponse<RefiningMethodDto> p =
          backendApiClient.getCached(
              "/api/v1/refining-methods?size=1000", new ParameterizedTypeReference<>() {}, true);
      if (p != null && p.content() != null) {
        return new ArrayList<>(p.content());
      }
    } catch (Exception e) {
      log.error("Failed to fetch refining methods", e);
    }
    return new ArrayList<>();
  }

  private List<LocationDto> fetchAllLocations() {
    try {
      PageResponse<LocationDto> p =
          backendApiClient.getCached(
              "/api/v1/locations?size=1000", new ParameterizedTypeReference<>() {});
      if (p != null && p.content() != null) {
        return new ArrayList<>(p.content());
      }
    } catch (Exception e) {
      log.error("Failed to fetch all locations", e);
    }
    return new ArrayList<>();
  }

  private List<LocationDto> fetchLocations() {
    try {
      List<LocationDto> locs =
          backendApiClient.getCached(
              "/api/v1/locations/refineries", new ParameterizedTypeReference<>() {});
      if (locs != null) {
        return locs;
      }
    } catch (Exception e) {
      log.error("Failed to fetch refinery locations", e);
    }
    return new ArrayList<>();
  }

  private List<MissionListDto> fetchMissions() {
    return fetchMissions(null);
  }

  /**
   * Fetches the missions catalog for the refinery-order dropdowns, restricted to the last three
   * months of {@code plannedStartTime} (future-scheduled missions are included) and sorted
   * newest-first. Older missions are dropped so the dropdown does not balloon with historical
   * operations the user is unlikely to pick. Pass {@code preserveMissionId} to retain a specific
   * mission regardless of its plannedStartTime — used on the detail page so an existing order's
   * linked mission stays visible even when it falls outside the three-month window.
   *
   * @param preserveMissionId mission id to keep in the result regardless of its plannedStartTime,
   *     or {@code null} to apply the cut-off without preservation.
   * @return mutable list of missions in newest-first order; empty on backend failure.
   */
  private List<MissionListDto> fetchMissions(UUID preserveMissionId) {
    try {
      PageResponse<MissionListDto> p =
          backendApiClient.get("/api/v1/missions?size=1000", new ParameterizedTypeReference<>() {});
      if (p == null) {
        return new ArrayList<>();
      }
      java.time.Instant cutoff =
          java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusMonths(3).toInstant();
      return filterAndSortMissionsForDropdown(p.content(), cutoff, preserveMissionId);
    } catch (Exception e) {
      log.error("Failed to fetch missions", e);
    }
    return new ArrayList<>();
  }

  /**
   * Filters the given missions to those whose {@code plannedStartTime} is on or after {@code
   * cutoff} (missions without a planned start are dropped) and sorts the result newest-first. If
   * {@code preserveMissionId} is non-null and the matching mission is in {@code all} but missing
   * from the filtered slice, it is appended to the end so the caller can keep it as a selected
   * option without losing it from the dropdown.
   *
   * <p>Package-private for direct unit testing — the date arithmetic in the surrounding {@link
   * #fetchMissions(UUID)} would otherwise be hidden behind the API client mock.
   *
   * @param all unfiltered mission list from the backend (may be {@code null} or empty).
   * @param cutoff inclusive lower bound for {@code plannedStartTime}.
   * @param preserveMissionId mission id to retain regardless of the cut-off, or {@code null}.
   * @return mutable list of missions in newest-first order.
   */
  static List<MissionListDto> filterAndSortMissionsForDropdown(
      List<MissionListDto> all, java.time.Instant cutoff, UUID preserveMissionId) {
    if (all == null || all.isEmpty()) {
      return new ArrayList<>();
    }
    List<MissionListDto> filtered =
        new ArrayList<>(
            all.stream()
                .filter(m -> m.plannedStartTime() != null && !m.plannedStartTime().isBefore(cutoff))
                .sorted(Comparator.comparing(MissionListDto::plannedStartTime).reversed())
                .toList());
    if (preserveMissionId != null
        && filtered.stream().noneMatch(m -> preserveMissionId.equals(m.id()))) {
      all.stream()
          .filter(m -> preserveMissionId.equals(m.id()))
          .findFirst()
          .ifPresent(filtered::add);
    }
    return filtered;
  }

  /**
   * Resolves the {@link OrgUnitMembershipOptionDto} list that drives the R5.d owner-picker fragment
   * on the refinery-order create form. The target user is the form's {@code ownerId} when a
   * logistician has explicitly picked another user; otherwise the calling user (a self-entry).
   *
   * <p>Falls back to an empty list when the lookup fails — the fragment collapses to a hidden state
   * for an empty option list, so a transient backend hiccup does not break the form render.
   *
   * @param form the inbound refinery-order form (may be {@code null} on the very first GET before
   *     binding).
   * @param principal the OIDC principal of the calling user; used to derive the fallback target
   *     user when the form does not carry an explicit owner.
   * @return picker options or empty list; never {@code null}.
   */
  private List<OrgUnitMembershipOptionDto> fetchOwnerPickerOptions(
      RefineryOrderForm form, OidcUser principal) {
    UUID targetUserId = form != null ? form.getOwnerId() : null;
    if (targetUserId == null) {
      targetUserId = getCurrentUserId(principal);
    }
    if (targetUserId == null) {
      return List.of();
    }
    try {
      List<OrgUnitMembershipOptionDto> options =
          backendApiClient.get(
              "/api/v1/users/" + targetUserId + "/memberships",
              new ParameterizedTypeReference<>() {});
      return options != null ? options : List.of();
    } catch (Exception e) {
      log.warn("Failed to fetch memberships for refinery-order owner-picker", e);
      return List.of();
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
      log.error("Failed to fetch users", e);
    }
    return new ArrayList<>();
  }

  private List<JobOrderDto> fetchActiveJobOrders() {
    try {
      PageResponse<JobOrderDto> p =
          backendApiClient.get(
              "/api/v1/orders?size=1000&status=OPEN,IN_PROGRESS",
              new ParameterizedTypeReference<>() {});
      if (p != null && p.content() != null) {
        return new ArrayList<>(p.content());
      }
    } catch (Exception e) {
      log.error("Failed to fetch job orders", e);
    }
    return new ArrayList<>();
  }

  private String fetchRoundingMode() {
    try {
      SystemSettingDto setting =
          backendApiClient.get("/api/v1/settings/refinery.rounding.mode", SystemSettingDto.class);
      return setting.value();
    } catch (Exception e) {
      log.warn("Failed to fetch refinery rounding mode, using default UP");
      return "UP";
    }
  }

  private UUID getCurrentUserId(OidcUser principal) {
    if (principal == null) {
      return null;
    }
    try {
      // Try the subject directly (fastest path)
      return UUID.fromString(principal.getSubject());
    } catch (Exception e) {
      // Fallback: ask the backend for our id
      try {
        UserDto me = backendApiClient.get("/api/v1/users/me", UserDto.class);
        return me != null ? me.id() : null;
      } catch (Exception ex) {
        log.warn("Failed to get current user ID from backend: {}", ex.getMessage());
        return null;
      }
    }
  }

  /**
   * Returns {@code 0.0} when the value is {@code null}. Used when saving a refinery order for the
   * money fields ({@code expenses}, {@code otherExpenses}, {@code oreSales}): the frontend
   * pre-fills these with 0 and a blur-handler restores 0 when the user clears the field, but
   * Spring's form-binding still produces {@code null} for an empty submission. Normalising to 0
   * means the backend always sees an explicit numeric value and the displayed-vs-stored value never
   * disagrees on re-render.
   */
  private static Double nullToZero(Double value) {
    return value != null ? value : 0d;
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

    log.debug(
        "Checking logistician status for user: {}, authorities: {}",
        principal.getName(),
        authorities);
    Collection<? extends GrantedAuthority> reachableAuthorities =
        roleHierarchy.getReachableGrantedAuthorities(authorities);
    log.debug("Reachable authorities: {}", reachableAuthorities);
    boolean result =
        reachableAuthorities.stream()
            .anyMatch(
                a ->
                    a.getAuthority().equals("ROLE_LOGISTICIAN")
                        || a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_OFFICER"));
    if (!result) {
      try {
        // Fallback: check the DB flag from the backend
        de.greluc.krt.iri.basetool.frontend.model.dto.UserDto me =
            backendApiClient.get(
                "/api/v1/users/me", de.greluc.krt.iri.basetool.frontend.model.dto.UserDto.class);
        if (me != null && Boolean.TRUE.equals(me.isLogistician())) {
          // M-16: do NOT log {@code principal.getName()} (the Keycloak username — PII). Log only
          // a stable short pseudonym derived from the principal name's hashCode, in the same
          // shape as {@code BackendRoleSyncFilter.maskPrincipal}. The narrower {@code log.debug}
          // a few lines down does not carry the principal at all.
          log.info(
              "Granting logistician by backend flag for user: u-{}",
              Integer.toHexString(principal.getName().hashCode()));
          result = true;
        }
      } catch (Exception e) {
        log.debug("Fallback check for logistician via /users/me failed: {}", e.getMessage());
      }
    }
    log.debug("Is logistician (final): {}", result);
    return result;
  }
}

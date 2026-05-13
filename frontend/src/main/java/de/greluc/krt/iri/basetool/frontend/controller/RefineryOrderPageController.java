package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.LocationDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionListDto;
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
import java.util.List;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    return "refinery-orders-create";
  }

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
                      null)
                  : null;
          goodsDto.add(
              new de.greluc.krt.iri.basetool.frontend.model.dto.RefineryGoodDto(
                  null,
                  inMat,
                  g.getInputQuantity(),
                  outMat,
                  g.getOutputQuantity(),
                  g.getQuality(),
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
                      form.getLocationId(), null, null, false, null)
                  : null,
              form.getMissionId() != null
                  ? new de.greluc.krt.iri.basetool.frontend.model.dto.MissionReferenceDto(
                      form.getMissionId(), null, null, null)
                  : null,
              startedAtTime,
              (long)
                  ((form.getDurationHours() != null ? form.getDurationHours() : 0) * 60
                      + (form.getDurationMinutes() != null ? form.getDurationMinutes() : 0)),
              zeroToNull(form.getExpenses()),
              zeroToNull(form.getOtherExpenses()),
              zeroToNull(form.getOreSales()),
              null,
              form.getRefiningMethodId() != null
                  ? new de.greluc.krt.iri.basetool.frontend.model.dto.RefiningMethodDto(
                      form.getRefiningMethodId(), null, null, null, null, null, null)
                  : null,
              goodsDto,
              form.getStatus() != null
                  ? form.getStatus()
                  : de.greluc.krt.iri.basetool.frontend.model.dto.RefineryOrderStatus.OPEN,
              form.getVersion());

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
          form.setExpenses(orderDto.expenses());
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

                double amount = 0.0;
                if (good.outputQuantity() != null) {
                  if ("SCU".equals(good.outputMaterial().quantityType())) {
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
                  } else {
                    amount = good.outputQuantity();
                  }
                  storeItem.setAmountFixed(true);
                } else {
                  storeItem.setAmountFixed(false);
                }
                storeItem.setAmount(amount);
                storeItem.setQuantityType(good.outputMaterial().quantityType());

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
    model.addAttribute("missions", fetchMissions());
    model.addAttribute("users", fetchUsers());
    model.addAttribute("jobOrders", fetchActiveJobOrders());
    model.addAttribute("roundingMode", fetchRoundingMode());
    return "refinery-orders-details";
  }

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
                      null)
                  : null;
          goodsDto.add(
              new de.greluc.krt.iri.basetool.frontend.model.dto.RefineryGoodDto(
                  null,
                  inMat,
                  g.getInputQuantity(),
                  outMat,
                  g.getOutputQuantity(),
                  g.getQuality(),
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
                      form.getLocationId(), null, null, false, null)
                  : null,
              form.getMissionId() != null
                  ? new de.greluc.krt.iri.basetool.frontend.model.dto.MissionReferenceDto(
                      form.getMissionId(), null, null, null)
                  : null,
              startedAtTime,
              (long)
                  ((form.getDurationHours() != null ? form.getDurationHours() : 0) * 60
                      + (form.getDurationMinutes() != null ? form.getDurationMinutes() : 0)),
              zeroToNull(form.getExpenses()),
              zeroToNull(form.getOtherExpenses()),
              zeroToNull(form.getOreSales()),
              null,
              form.getRefiningMethodId() != null
                  ? new de.greluc.krt.iri.basetool.frontend.model.dto.RefiningMethodDto(
                      form.getRefiningMethodId(), null, null, null, null, null, null)
                  : null,
              goodsDto,
              form.getStatus() != null
                  ? form.getStatus()
                  : de.greluc.krt.iri.basetool.frontend.model.dto.RefineryOrderStatus.OPEN,
              form.getVersion());

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
    try {
      PageResponse<MissionListDto> p =
          backendApiClient.get("/api/v1/missions?size=1000", new ParameterizedTypeReference<>() {});
      if (p != null && p.content() != null) {
        return new ArrayList<>(p.content());
      }
    } catch (Exception e) {
      log.error("Failed to fetch missions", e);
    }
    return new ArrayList<>();
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
   * Sets a Double value to {@code null} if it is {@code null} or {@code 0.0}. Used when saving a
   * refinery order for the optional money fields ({@code expenses}, {@code otherExpenses}, {@code
   * oreSales}) because these are pre-filled with 0 in the frontend but should semantically count as
   * "not set".
   */
  private static Double zeroToNull(Double value) {
    if (value == null) {
      return null;
    }
    return value == 0.0 ? null : value;
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
          log.info("Granting logistician by backend flag for user: {}", principal.getName());
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

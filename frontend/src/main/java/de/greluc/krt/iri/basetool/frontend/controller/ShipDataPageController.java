package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.ManufacturerDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.ShipTypeDto;
import de.greluc.krt.iri.basetool.frontend.model.form.ManufacturerForm;
import de.greluc.krt.iri.basetool.frontend.model.form.ShipTypeForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ship-data")
@RequiredArgsConstructor
@Slf4j
public class ShipDataPageController {

  private final BackendApiClient backendApiClient;

  @GetMapping
  @SuppressWarnings("unchecked")
  public String listData(Model model) {
    if (!model.containsAttribute("manufacturerForm")) {
      model.addAttribute("manufacturerForm", new ManufacturerForm("", "", "", "", ""));
    }
    if (!model.containsAttribute("shipTypeForm")) {
      model.addAttribute("shipTypeForm", new ShipTypeForm("", null, ""));
    }

    try {
      PageResponse<ManufacturerDto> manufacturersPage =
          backendApiClient.get(
              "/api/v1/manufacturers?size=1000&sort=name,asc&includeHidden=true",
              new ParameterizedTypeReference<PageResponse<ManufacturerDto>>() {});

      List<ManufacturerDto> manufacturers = new ArrayList<>();
      if (manufacturersPage != null && manufacturersPage.content() != null) {
        manufacturers = new ArrayList<>(manufacturersPage.content());
        manufacturers.sort(
            Comparator.comparing(ManufacturerDto::name, String.CASE_INSENSITIVE_ORDER));
      }
      model.addAttribute("manufacturers", manufacturers);

      PageResponse<ShipTypeDto> shipTypesPage =
          backendApiClient.get(
              "/api/v1/ship-types?size=1000&sort=name,asc&includeHidden=true",
              new ParameterizedTypeReference<PageResponse<ShipTypeDto>>() {});

      List<ShipTypeDto> shipTypes = new ArrayList<>();
      if (shipTypesPage != null && shipTypesPage.content() != null) {
        shipTypes = new ArrayList<>(shipTypesPage.content());
        shipTypes.sort(Comparator.comparing(ShipTypeDto::name, String.CASE_INSENSITIVE_ORDER));
      }
      model.addAttribute("shipTypes", shipTypes);

    } catch (Exception e) {
      log.error("Error loading ship data", e);
      model.addAttribute("error", "error.shipdata.load");
    }

    return "ship-data";
  }

  // Manufacturers
  @PostMapping("/manufacturers/{id}/visibility")
  @PreAuthorize("hasRole('ADMIN')")
  public String toggleManufacturerVisibility(
      @PathVariable @NotNull UUID id,
      @RequestParam boolean hidden,
      RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.put(
          "/api/v1/manufacturers/" + id + "/visibility?hidden=" + hidden, null, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Update Manufacturer visibility failed", e);
      return "redirect:/ship-data?error=UpdateManufacturerVisibilityFailed";
    }
    return "redirect:/ship-data";
  }

  // ShipTypes
  @PostMapping("/ship-types/{id}/visibility")
  @PreAuthorize("hasRole('ADMIN')")
  public String toggleShipTypeVisibility(
      @PathVariable @NotNull UUID id,
      @RequestParam boolean hidden,
      RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.put(
          "/api/v1/ship-types/" + id + "/visibility?hidden=" + hidden, null, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Update ShipType visibility failed", e);
      return "redirect:/ship-data?error=UpdateShipTypeVisibilityFailed";
    }
    return "redirect:/ship-data";
  }

  @PostMapping("/reset-fitted")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public String resetAllFitted(RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.post("/api/v1/hangar/ships/reset-fitted", null, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.ship_unfitted");
    } catch (Exception e) {
      log.error("Reset all fitted failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.shipdata.unfit.failed");
    }
    return "redirect:/ship-data";
  }

  private String parseString(Object o) {
    return o == null ? null : o.toString();
  }

  private UUID parseUuid(Object o) {
    if (o == null) return null;
    if (o instanceof UUID u) return u;
    try {
      return UUID.fromString(o.toString());
    } catch (Exception e) {
      return null;
    }
  }
}

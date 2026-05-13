package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.LocationDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/locations")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
public class AdminLocationsPageController {

  private final BackendApiClient backendApiClient;

  @GetMapping
  public String listData(Model model) {
    try {
      PageResponse<LocationDto> locationsPage =
          backendApiClient.get(
              "/api/v1/locations?size=1000&sort=name,asc&includeHidden=true",
              new ParameterizedTypeReference<PageResponse<LocationDto>>() {});

      List<LocationDto> locations = new ArrayList<>();
      if (locationsPage != null && locationsPage.content() != null) {
        locations = new ArrayList<>(locationsPage.content());
        locations.sort(
            Comparator.comparing(
                l -> l.name() == null ? "" : l.name(), String.CASE_INSENSITIVE_ORDER));
      }
      model.addAttribute("locations", locations);

    } catch (Exception e) {
      log.error("Error loading locations data", e);
      model.addAttribute("error", "error.admin.locations.load");
    }
    return "admin/locations";
  }

  @PostMapping("/{id}/toggle-visibility")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public String toggleLocationVisibility(
      @PathVariable @NotNull UUID id,
      @RequestParam boolean hidden,
      RedirectAttributes redirectAttributes) {
    try {
      LocationDto currentLocation =
          backendApiClient.get("/api/v1/locations/" + id, LocationDto.class);
      LocationDto body =
          new LocationDto(
              id,
              currentLocation.name(),
              currentLocation.description(),
              hidden,
              currentLocation.version());
      backendApiClient.put("/api/v1/locations/" + id, body, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (BackendServiceException e) {
      log.error("Toggle location visibility failed", e);
      if (e.getStatusCode() == 409 && "concurrency-conflict".equals(e.getProblemType())) {
        redirectAttributes.addFlashAttribute("errorToast", "error.concurrency.conflict");
      } else {
        redirectAttributes.addFlashAttribute("errorToast", "error.admin.locations.load");
      }
      return "redirect:/admin/locations";
    } catch (Exception e) {
      log.error("Toggle location visibility failed", e);
      return "redirect:/admin/locations?error=ToggleVisibilityFailed";
    }
    return "redirect:/admin/locations";
  }
}

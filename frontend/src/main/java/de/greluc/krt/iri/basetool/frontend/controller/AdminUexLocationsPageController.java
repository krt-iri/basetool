package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.CityDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OutpostDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.PoiDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.SpaceStationDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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

/**
 * Spring MVC controller for the admin UEX-overrides page ({@code /admin/uex-locations}).
 *
 * <p>The page lets admins/officers pin the {@code hasLoadingDock} flag on cities, space stations,
 * outposts and POIs so the hourly UEX sweep cannot reset a manual correction. Each entity type is
 * rendered in its own section; the dispatcher endpoint routes the three button states ({@code
 * uex}/{@code yes}/{@code no}) to the matching backend {@code PATCH}/{@code DELETE} call.
 */
@Controller
@RequestMapping("/admin/uex-locations")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
public class AdminUexLocationsPageController {

  private static final Set<String> ALLOWED_KINDS =
      Set.of("cities", "space-stations", "outposts", "pois");

  private final BackendApiClient backendApiClient;

  /**
   * Fetches up to 10 000 cities, space stations, outposts and POIs (one paginated call each), sorts
   * the lists case-insensitively by name and renders the unified admin page. Any backend failure
   * puts an error key in the model rather than blanking the page.
   *
   * @param model Thymeleaf model populated with four sorted lists
   * @return the {@code admin/uex-locations} view name
   */
  @GetMapping
  public String listData(Model model) {
    try {
      List<CityDto> cities = parseCities(loadPage("/api/v1/cities?size=10000&sort=name,asc"));
      sortByName(cities, CityDto::name);
      model.addAttribute("cities", cities);

      List<SpaceStationDto> stations =
          parseStations(loadPage("/api/v1/space-stations?size=10000&sort=name,asc"));
      sortByName(stations, SpaceStationDto::name);
      model.addAttribute("stations", stations);

      List<OutpostDto> outposts =
          parseOutposts(loadPage("/api/v1/outposts?size=10000&sort=name,asc"));
      sortByName(outposts, OutpostDto::name);
      model.addAttribute("outposts", outposts);

      List<PoiDto> pois = parsePois(loadPage("/api/v1/pois?size=10000&sort=name,asc"));
      sortByName(pois, PoiDto::name);
      model.addAttribute("pois", pois);
    } catch (Exception e) {
      log.error("Error loading UEX-locations data", e);
      model.addAttribute("error", "error.admin.uex_locations.load");
    }
    return "admin/uex-locations";
  }

  /**
   * Dispatches an override action against one of the four supported entity kinds. The {@code kind}
   * path variable maps 1:1 to the backend URL segment (e.g. {@code cities} → {@code
   * /api/v1/cities/{id}/loading-dock}).
   *
   * @param kind one of {@code cities}, {@code space-stations}, {@code outposts}, {@code pois}
   * @param id entity id
   * @param action one of {@code uex} (clear pin), {@code yes} or {@code no} (set pin)
   * @param redirectAttributes flash attributes carrier
   * @return redirect back to {@code /admin/uex-locations}
   */
  @PostMapping("/{kind}/{id}/loading-dock")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public String updateLoadingDockOverride(
      @PathVariable @NotNull String kind,
      @PathVariable @NotNull UUID id,
      @RequestParam String action,
      RedirectAttributes redirectAttributes) {
    if (!ALLOWED_KINDS.contains(kind)) {
      redirectAttributes.addFlashAttribute("errorToast", "error.admin.uex.flag.update");
      return "redirect:/admin/uex-locations";
    }
    String base = "/api/v1/" + kind + "/" + id;
    try {
      switch (action) {
        case "uex" -> backendApiClient.delete(base + "/loading-dock-override", Void.class);
        case "yes" -> backendApiClient.patch(base + "/loading-dock?value=true", null, Void.class);
        case "no" -> backendApiClient.patch(base + "/loading-dock?value=false", null, Void.class);
        default -> {
          redirectAttributes.addFlashAttribute("errorToast", "error.admin.uex.flag.update");
          return "redirect:/admin/uex-locations";
        }
      }
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (BackendServiceException e) {
      log.error("Override update failed for kind {}", kind, e);
      redirectAttributes.addFlashAttribute("errorToast", "error.admin.uex.flag.update");
    } catch (Exception e) {
      log.error("Override update failed for kind {}", kind, e);
      return "redirect:/admin/uex-locations?error=OverrideUpdateFailed";
    }
    return "redirect:/admin/uex-locations";
  }

  private PageResponse<Map<String, Object>> loadPage(String uri) {
    return backendApiClient.get(
        uri, new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {});
  }

  private List<CityDto> parseCities(PageResponse<Map<String, Object>> page) {
    if (page == null || page.content() == null) {
      return new ArrayList<>();
    }
    return page.content().stream()
        .map(
            m ->
                new CityDto(
                    parseUuid(m.get("id")),
                    parseString(m.get("name")),
                    parseString(m.get("starSystemName")),
                    parseString(m.get("planetName")),
                    parseNullableBoolean(m.get("hasLoadingDock")),
                    Boolean.TRUE.equals(m.get("hasLoadingDockOverridden"))))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private List<SpaceStationDto> parseStations(PageResponse<Map<String, Object>> page) {
    if (page == null || page.content() == null) {
      return new ArrayList<>();
    }
    return page.content().stream()
        .map(
            m ->
                new SpaceStationDto(
                    parseUuid(m.get("id")),
                    parseString(m.get("name")),
                    parseString(m.get("starSystemName")),
                    parseString(m.get("planetName")),
                    parseNullableBoolean(m.get("hasLoadingDock")),
                    Boolean.TRUE.equals(m.get("hasLoadingDockOverridden"))))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private List<OutpostDto> parseOutposts(PageResponse<Map<String, Object>> page) {
    if (page == null || page.content() == null) {
      return new ArrayList<>();
    }
    return page.content().stream()
        .map(
            m ->
                new OutpostDto(
                    parseUuid(m.get("id")),
                    parseString(m.get("name")),
                    parseString(m.get("starSystemName")),
                    parseString(m.get("planetName")),
                    parseNullableBoolean(m.get("hasLoadingDock")),
                    Boolean.TRUE.equals(m.get("hasLoadingDockOverridden"))))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private List<PoiDto> parsePois(PageResponse<Map<String, Object>> page) {
    if (page == null || page.content() == null) {
      return new ArrayList<>();
    }
    return page.content().stream()
        .map(
            m ->
                new PoiDto(
                    parseUuid(m.get("id")),
                    parseString(m.get("name")),
                    parseString(m.get("starSystemName")),
                    parseString(m.get("planetName")),
                    parseNullableBoolean(m.get("hasLoadingDock")),
                    Boolean.TRUE.equals(m.get("hasLoadingDockOverridden"))))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private <T> void sortByName(List<T> list, java.util.function.Function<T, String> nameAccessor) {
    list.sort(
        Comparator.comparing(
            t -> {
              String n = nameAccessor.apply(t);
              return n == null ? "" : n;
            },
            String.CASE_INSENSITIVE_ORDER));
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

  private Boolean parseNullableBoolean(Object o) {
    if (o == null) {
      return null;
    }
    if (o instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(o.toString());
  }
}

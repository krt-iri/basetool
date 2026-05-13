package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin REST proxy that forwards material-related read requests from the browser to the backend.
 *
 * <p>Browser-side JS calls land here under {@code /api/proxy/materials/**}; the controller adds the
 * bearer token via {@link BackendApiClient} and forwards to the corresponding {@code
 * /api/v1/materials/**} backend endpoint. Authentication is enforced at this seam
 * ({@code @PreAuthorize("isAuthenticated()")}) so an unauthenticated browser can never hit the
 * proxy and the backend never sees an unauthenticated request via this path.
 */
@RestController
@RequestMapping("/api/proxy/materials")
@RequiredArgsConstructor
public class MaterialProxyController {

  private final BackendApiClient backendApiClient;

  /**
   * Returns the list of terminals where the given material is traded. Empty list on backend failure
   * or missing payload — the frontend renders an "unavailable" placeholder rather than propagating
   * the error.
   *
   * @param id material id
   * @return list of terminal records (raw JSON maps), never {@code null}
   */
  @GetMapping("/{id}/terminals")
  @PreAuthorize("isAuthenticated()")
  public List<Map<String, Object>> getMaterialTerminals(@PathVariable UUID id) {
    List<Map<String, Object>> response =
        backendApiClient.get(
            "/api/v1/materials/" + id + "/terminals",
            new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    return response != null ? response : List.of();
  }

  /**
   * Forwards the profit-calculation query to the backend, appending each star-system name as a
   * repeated {@code starSystemNames} query parameter (Spring's default list-binding form). The
   * star-system filter is optional — omitting it returns the calculation across all systems.
   *
   * @param shipId chosen ship's id (defines capacity)
   * @param starSystemNames optional list of star-system names to constrain the source terminals
   * @return list of profit-calculation rows, never {@code null}
   */
  @GetMapping("/profit-calculation")
  @PreAuthorize("isAuthenticated()")
  public List<Map<String, Object>> getProfitCalculation(
      @RequestParam UUID shipId, @RequestParam(required = false) List<String> starSystemNames) {

    StringBuilder url =
        new StringBuilder("/api/v1/materials/profit-calculation?shipId=").append(shipId);
    if (starSystemNames != null && !starSystemNames.isEmpty()) {
      for (String system : starSystemNames) {
        url.append("&starSystemNames=").append(system);
      }
    }

    List<Map<String, Object>> response =
        backendApiClient.get(
            url.toString(), new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    return response != null ? response : List.of();
  }
}

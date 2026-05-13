package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.dto.UexLocationDto;
import de.greluc.krt.iri.basetool.backend.service.PersonalInventoryItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only typeahead endpoint backing the personal-inventory location picker. Returns a combined
 * list of UEX cities and space stations from the locally synced mirror.
 */
@RestController
@RequestMapping("/api/v1/uex/locations")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "UEX Locations", description = "Lookup UEX cities and space stations.")
@SecurityRequirement(name = "bearerAuth")
public class UexLocationController {

  /** Hard cap to keep typeahead payloads small. */
  private static final int DEFAULT_LIMIT = 25;

  private static final int MAX_LIMIT = 2000;

  private final PersonalInventoryItemService service;

  @GetMapping("/search")
  @Operation(summary = "Search UEX cities and space stations by name (case insensitive).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Up to 2000 matches, sorted alphabetically."),
    @ApiResponse(responseCode = "401", description = "Authentication required.")
  })
  public List<UexLocationDto> search(
      @RequestParam(required = false) String q, @RequestParam(required = false) Integer limit) {
    int effectiveLimit = limit == null ? DEFAULT_LIMIT : Math.min(MAX_LIMIT, Math.max(1, limit));
    return service.searchLocations(q, effectiveLimit);
  }
}

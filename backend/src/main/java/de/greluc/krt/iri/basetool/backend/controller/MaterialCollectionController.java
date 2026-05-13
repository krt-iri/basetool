package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.dto.MaterialCollectionEntryDto;
import de.greluc.krt.iri.basetool.backend.service.InventoryItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the material collection overview of a job order. Provides a sorted list of
 * all inventory items linked to a specific job order.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class MaterialCollectionController {

  private final InventoryItemService inventoryItemService;

  @Operation(
      summary = "Get material collection for a job order",
      description =
          "Returns all inventory items linked to the given job order, sorted by owner name, location, material name, quality (desc), quantity (desc).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Material collection returned successfully"),
    @ApiResponse(responseCode = "403", description = "Access denied – authentication required"),
    @ApiResponse(responseCode = "404", description = "Job order not found")
  })
  @GetMapping("/{jobOrderId}/material-collection")
  @PreAuthorize("isAuthenticated()")
  public List<MaterialCollectionEntryDto> getMaterialCollection(
      @PathVariable @NotNull UUID jobOrderId) {
    return inventoryItemService.getMaterialCollection(jobOrderId);
  }
}

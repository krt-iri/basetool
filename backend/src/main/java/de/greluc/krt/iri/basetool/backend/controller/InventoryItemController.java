package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.dto.*;
import de.greluc.krt.iri.basetool.backend.service.AuthHelperService;
import de.greluc.krt.iri.basetool.backend.service.InventoryItemService;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryItemController {
  private final InventoryItemService inventoryItemService;
  private final UserService userService;
  private final AuthHelperService authHelperService;

  @GetMapping("/aggregated")
  @Transactional(readOnly = true)
  public PageResponse<AggregatedInventoryDto> getAggregatedInventory(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false, defaultValue = "material.name,asc;quality,desc;amount,desc")
          String sort) {
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page, size, sort, Set.of("amount", "quality", "material.name"), "material.name");
    Page<AggregatedInventoryDto> p = inventoryItemService.getAggregatedInventory(pageable);
    return new PageResponse<>(
        p.getContent(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  @GetMapping("/material/{materialId}")
  @Transactional(readOnly = true)
  public PageResponse<InventoryItemDto> getInventoryByMaterial(
      @PathVariable @NotNull UUID materialId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false, defaultValue = "quality,desc;location.name,asc;amount,desc")
          String sort) {
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page, size, sort, Set.of("amount", "quality", "id", "location.name"), "quality");
    Page<InventoryItemDto> p = inventoryItemService.getInventoryByMaterial(materialId, pageable);
    return new PageResponse<>(
        p.getContent(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  @GetMapping("/my-inventory")
  @Transactional(readOnly = true)
  public PageResponse<InventoryItemDto> getMyInventory(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false, defaultValue = "material.name,asc;quality,desc;amount,desc")
          String sort) {
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page, size, sort, Set.of("amount", "quality", "id", "material.name"), "quality");
    Page<InventoryItemDto> p =
        inventoryItemService.getUserInventory(userService.getUserIdFromJwt(jwt), pageable);
    return new PageResponse<>(
        p.getContent(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  @GetMapping("/my-inventory/grouped")
  @Transactional(readOnly = true)
  public List<de.greluc.krt.iri.basetool.backend.model.dto.GroupedInventoryDto>
      getMyGroupedInventory(
          @AuthenticationPrincipal Jwt jwt,
          @RequestParam(required = false) List<UUID> materialIds,
          @RequestParam(required = false) Integer minQuality,
          @RequestParam(required = false) List<UUID> jobOrderIds,
          @RequestParam(required = false) List<UUID> missionIds) {
    return inventoryItemService.getMyAggregatedInventory(
        userService.getUserIdFromJwt(jwt), materialIds, minQuality, jobOrderIds, missionIds);
  }

  @GetMapping("/all")
  @Transactional(readOnly = true)
  public PageResponse<InventoryItemDto> getAllInventory(
      @RequestParam(required = false) List<UUID> materialIds,
      @RequestParam(required = false) Integer minQuality,
      @RequestParam(required = false) List<UUID> jobOrderIds,
      @RequestParam(required = false) List<UUID> missionIds,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false, defaultValue = "material.name,asc;quality,desc;amount,desc")
          String sort) {
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page, size, sort, Set.of("amount", "quality", "id", "material.name"), "quality");
    Page<InventoryItemDto> p =
        inventoryItemService.getAllInventory(
            materialIds, minQuality, jobOrderIds, missionIds, pageable);
    return new PageResponse<>(
        p.getContent(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  @GetMapping("/all/grouped")
  @Transactional(readOnly = true)
  public List<de.greluc.krt.iri.basetool.backend.model.dto.GroupedInventoryDto>
      getAllGroupedInventory(
          @RequestParam(required = false) List<UUID> materialIds,
          @RequestParam(required = false) Integer minQuality,
          @RequestParam(required = false) List<UUID> jobOrderIds,
          @RequestParam(required = false) List<UUID> missionIds) {
    return inventoryItemService.getAllAggregatedInventory(
        materialIds, minQuality, jobOrderIds, missionIds);
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public InventoryItemDto createInventoryItem(
      @AuthenticationPrincipal Jwt jwt, @RequestBody @Valid InventoryItemCreateDto dto) {
    boolean isLogistician = authHelperService.isLogisticianOrAbove();
    return inventoryItemService.createInventoryItem(
        dto, userService.getUserIdFromJwt(jwt), isLogistician);
  }

  @PostMapping("/{id}/book-out")
  @PreAuthorize("isAuthenticated()")
  public org.springframework.http.ResponseEntity<InventoryItemDto> bookOutInventoryItem(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable @NotNull UUID id,
      @RequestBody @Valid InventoryItemBookOutDto dto) {
    boolean isLogistician = authHelperService.isLogisticianOrAbove();
    InventoryItemDto result =
        inventoryItemService.bookOutInventoryItem(
            id, dto, userService.getUserIdFromJwt(jwt), isLogistician);
    if (result == null) {
      return org.springframework.http.ResponseEntity.noContent().build();
    }
    return org.springframework.http.ResponseEntity.ok(result);
  }

  /**
   * Sets, updates or removes the free-text note of an inventory item. Owner may always modify their
   * own note; non-owners require LOGISTICIAN (or higher via role hierarchy) role. Optimistic
   * locking is enforced via the {@code version} field in the request.
   */
  @PutMapping("/{id}/note")
  @PreAuthorize("isAuthenticated()")
  public InventoryItemDto updateInventoryItemNote(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable @NotNull UUID id,
      @RequestBody @Valid InventoryItemNoteUpdateRequest request) {
    boolean isLogistician = authHelperService.isLogisticianOrAbove();
    return inventoryItemService.updateNote(
        id, request, userService.getUserIdFromJwt(jwt), isLogistician);
  }

  @Operation(
      summary = "Bulk checkout",
      description =
          "Removes all specified inventory items that belong to the authenticated user. Associations to job orders and missions are cleared before deletion.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Bulk checkout successful"),
    @ApiResponse(responseCode = "400", description = "Invalid request (empty list)"),
    @ApiResponse(
        responseCode = "403",
        description = "Access denied – item belongs to another user"),
    @ApiResponse(responseCode = "404", description = "One or more items not found")
  })
  @PostMapping("/bulk-checkout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("isAuthenticated()")
  public void bulkCheckout(
      @AuthenticationPrincipal Jwt jwt, @RequestBody @Valid BulkCheckoutRequest request) {
    inventoryItemService.bulkCheckout(request, userService.getUserIdFromJwt(jwt));
  }

  @Operation(
      summary = "Update delivered status",
      description =
          "Updates the delivered flag of an inventory item. Applies optimistic locking via the version field.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Delivered status updated successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid request"),
    @ApiResponse(responseCode = "403", description = "Access denied"),
    @ApiResponse(responseCode = "404", description = "Inventory item not found"),
    @ApiResponse(responseCode = "409", description = "Optimistic locking conflict")
  })
  @PatchMapping("/{id}/delivered")
  @PreAuthorize("isAuthenticated()")
  public InventoryItemDto updateDelivered(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable @NotNull UUID id,
      @RequestBody @Valid UpdateDeliveredRequest request) {
    boolean isLogistician = authHelperService.isLogisticianOrAbove();
    return inventoryItemService.updateDelivered(
        id, request, userService.getUserIdFromJwt(jwt), isLogistician);
  }

  @PutMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public InventoryItemDto updateInventoryItem(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable @NotNull UUID id,
      @RequestBody @Valid InventoryItemUpdateDto dto) {
    boolean isLogistician = authHelperService.isLogisticianOrAbove();
    return inventoryItemService.updateInventoryItem(
        id, dto, userService.getUserIdFromJwt(jwt), isLogistician);
  }
}

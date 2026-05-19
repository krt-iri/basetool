package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.dto.AggregatedInventoryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BulkCheckoutRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemBookOutDto;
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemDto;
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemNoteUpdateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemUpdateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.UpdateDeliveredRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for inventory items — covers the aggregated/grouped read variants used by the
 * inventory page, the user-scoped {@code /my-inventory} subset, the admin-wide {@code /all}, the
 * create / update / note-only update endpoints, the book-out flow and the bulk-checkout.
 *
 * <p>Owner-vs-logistician decisions happen at the HTTP boundary via {@link
 * AuthHelperService#isLogisticianOrAbove()} and are passed as a boolean to the service — the
 * service stays free of {@code SecurityContextHolder} reads (ArchUnit rule).
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryItemController {
  private final InventoryItemService inventoryItemService;
  private final UserService userService;
  private final AuthHelperService authHelperService;

  /**
   * Aggregated per-material inventory view. Default sort favors material name, then quality
   * descending, then amount — the order operators actually want.
   *
   * @return paged aggregated DTOs
   */
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

  /**
   * Per-material drilldown — every individual inventory row for the given material.
   *
   * @param materialId material to drill into
   * @return paged inventory items
   */
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

  /**
   * Calling user's own inventory items. Owner id derived from the JWT — no impersonation.
   *
   * @return paged inventory items
   */
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

  /**
   * Calling user's inventory grouped by material with totals/average-quality — drives the "personal
   * inventory" page's outer rows.
   *
   * @return grouped DTOs
   */
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

  /**
   * Squadron-wide flat inventory list (admin/logistician view).
   *
   * @return paged inventory items
   */
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

  /**
   * Squadron-wide grouped variant — same shape as {@link #getMyGroupedInventory} but scoped to all
   * users.
   *
   * @return grouped DTOs
   */
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

  /**
   * Creates an inventory item. Logistician role lets the caller set an arbitrary owner; everyone
   * else gets the calling user as owner.
   *
   * @return the persisted DTO
   */
  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public InventoryItemDto createInventoryItem(
      @AuthenticationPrincipal Jwt jwt, @RequestBody @Valid InventoryItemCreateDto dto) {
    boolean isLogistician = authHelperService.isLogisticianOrAbove();
    return inventoryItemService.createInventoryItem(
        dto, userService.getUserIdFromJwt(jwt), isLogistician);
  }

  /**
   * Books out an item (consume / transfer / sell). Returns 204 No Content when the post-decrement
   * quantity drops below the epsilon and the row is removed entirely; 200 OK with the persisted
   * item otherwise.
   *
   * @return the persisted DTO or 204
   */
  @PostMapping("/{id}/book-out")
  @PreAuthorize("isAuthenticated() and @squadronScopeService.canEditInventoryItem(#id)")
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
  @PreAuthorize("isAuthenticated() and @squadronScopeService.canEditInventoryItem(#id)")
  public InventoryItemDto updateInventoryItemNote(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable @NotNull UUID id,
      @RequestBody @Valid InventoryItemNoteUpdateRequest request) {
    boolean isLogistician = authHelperService.isLogisticianOrAbove();
    return inventoryItemService.updateNote(
        id, request, userService.getUserIdFromJwt(jwt), isLogistician);
  }

  /**
   * Removes a list of inventory items in one transaction. Same concurrency pattern as {@link
   * #bookOutInventoryItem} — collected ids run through a single bulk-update after the loop instead
   * of one bulk-update per loop iteration.
   */
  @Operation(
      summary = "Bulk checkout",
      description =
          "Removes all specified inventory items that belong to the authenticated user."
              + " Associations to job orders and missions are cleared before deletion.")
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

  /**
   * Admin/logistician shortcut to flip the {@code delivered} flag without going through the full
   * book-out machinery.
   *
   * @return the persisted DTO
   */
  @Operation(
      summary = "Update delivered status",
      description =
          "Updates the delivered flag of an inventory item. Applies optimistic locking via the"
              + " version field.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Delivered status updated successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid request"),
    @ApiResponse(responseCode = "403", description = "Access denied"),
    @ApiResponse(responseCode = "404", description = "Inventory item not found"),
    @ApiResponse(responseCode = "409", description = "Optimistic locking conflict")
  })
  @PatchMapping("/{id}/delivered")
  @PreAuthorize("isAuthenticated() and @squadronScopeService.canEditInventoryItem(#id)")
  public InventoryItemDto updateDelivered(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable @NotNull UUID id,
      @RequestBody @Valid UpdateDeliveredRequest request) {
    boolean isLogistician = authHelperService.isLogisticianOrAbove();
    return inventoryItemService.updateDelivered(
        id, request, userService.getUserIdFromJwt(jwt), isLogistician);
  }

  /**
   * Updates the soft associations of an inventory item (mission, job order, owner). Quantity and
   * material identity go through {@link #bookOutInventoryItem} instead.
   *
   * @return the persisted DTO
   */
  @PutMapping("/{id}")
  @PreAuthorize("isAuthenticated() and @squadronScopeService.canEditInventoryItem(#id)")
  public InventoryItemDto updateInventoryItem(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable @NotNull UUID id,
      @RequestBody @Valid InventoryItemUpdateDto dto) {
    boolean isLogistician = authHelperService.isLogisticianOrAbove();
    return inventoryItemService.updateInventoryItem(
        id, dto, userService.getUserIdFromJwt(jwt), isLogistician);
  }

  /**
   * Admin-only: removes every non-personal inventory item ("globales Lager leeren"). Personal
   * entries are deliberately left untouched. Gated by {@code hasRole('ADMIN')} so neither {@code
   * OFFICER} nor {@code LOGISTICIAN} can trigger the squadron-wide wipe.
   *
   * @return 204 No Content
   */
  @Operation(
      summary = "Delete all global inventory",
      description =
          "Removes every non-personal inventory item (the shared squadron stock). Personal"
              + " entries (personal = true) remain untouched. Admin-only.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "All global inventory items removed"),
    @ApiResponse(responseCode = "401", description = "Not authenticated"),
    @ApiResponse(responseCode = "403", description = "Caller is not an admin")
  })
  @DeleteMapping("/all")
  @PreAuthorize("hasRole('ADMIN')")
  public org.springframework.http.ResponseEntity<Void> deleteAllGlobalInventory() {
    inventoryItemService.deleteAllGlobalInventory();
    return org.springframework.http.ResponseEntity.noContent().build();
  }
}

package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintReferenceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderItemRequestDto;
import de.greluc.krt.iri.basetool.backend.model.dto.GameItemReferenceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.HandoverReportPreviewRequestDto;
import de.greluc.krt.iri.basetool.backend.model.dto.ItemDerivationDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderItemHandoverCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderItemHandoverDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.UpdateJobOrderStatusDto;
import de.greluc.krt.iri.basetool.backend.service.AuthHelperService;
import de.greluc.krt.iri.basetool.backend.service.JobOrderHandoverReportService;
import de.greluc.krt.iri.basetool.backend.service.JobOrderHandoverService;
import de.greluc.krt.iri.basetool.backend.service.JobOrderItemHandoverService;
import de.greluc.krt.iri.basetool.backend.service.JobOrderItemService;
import de.greluc.krt.iri.basetool.backend.service.JobOrderService;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface over the job-order aggregate (the squadron's request-and-fulfill queue). Reads are
 * open to any authenticated user; mutations require LOGISTICIAN or above; delete is ADMIN-only.
 * Job-order creation is {@code permitAll()} so unauthenticated squadron members can file requests
 * via a public form.
 *
 * <p>Heavy concurrency lives in the service layer: {@code updateJobOrderPriority} acquires a
 * pessimistic write lock for the reorder shift; {@code updateJobOrderStatus} atomically unlinks
 * every inventory item on transition to a terminal state; handover creation follows the
 * bulk-update-after-loop pattern from CLAUDE.md to avoid the
 * {@code @Modifying(clearAutomatically=true)} trap. {@link #addAssignee}/{@link #removeAssignee}
 * resolve self-vs-logistician at the HTTP boundary so the service stays free of {@code
 * SecurityContextHolder} reads.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Operations related to job orders")
public class JobOrderController {
  private final JobOrderService jobOrderService;
  private final JobOrderItemService jobOrderItemService;
  private final JobOrderItemHandoverService jobOrderItemHandoverService;
  private final JobOrderHandoverService jobOrderHandoverService;
  private final JobOrderHandoverReportService jobOrderHandoverReportService;
  private final UserService userService;
  private final AuthHelperService authHelperService;

  /**
   * Records a materials handover for the job order. Multi-item flows use the bulk-update-after-loop
   * pattern: per-item mutations rely on Hibernate dirty-checking inside the loop, then a single
   * bulk unlink runs after persistence to avoid clearing the persistence context mid-iteration.
   *
   * @param id job-order id
   * @param dto handover create payload (per-item quantities)
   * @return the persisted handover DTO
   */
  @PostMapping("/{id}/handovers")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Create a job order handover",
      description = "Logs a handover of materials for this job order.")
  @PreAuthorize("hasRole('LOGISTICIAN') or hasRole('OFFICER') or hasRole('ADMIN')")
  public JobOrderHandoverDto createHandover(
      @PathVariable UUID id, @RequestBody @Valid JobOrderHandoverCreateDto dto) {
    return jobOrderHandoverService.createHandover(id, dto);
  }

  /**
   * Records an item handover for an item order: increments each ordered line's delivered count and
   * auto-completes the order once every line is fully delivered. Same authorisation as the material
   * handover (LOGISTICIAN+).
   *
   * @param id job-order id
   * @param dto item-handover payload (per-line delivered quantities)
   * @return the persisted item-handover DTO
   */
  @PostMapping("/{id}/item-handovers")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Create an item handover",
      description = "Logs a handover of produced items for this item order.")
  @PreAuthorize("hasRole('LOGISTICIAN') or hasRole('OFFICER') or hasRole('ADMIN')")
  public JobOrderItemHandoverDto createItemHandover(
      @PathVariable UUID id, @RequestBody @Valid JobOrderItemHandoverCreateDto dto) {
    return jobOrderItemHandoverService.createItemHandover(id, dto);
  }

  /**
   * Renders a persisted handover as a downloadable PDF. The optional {@code X-User-Time-Zone}
   * header overrides UTC for the timestamps in the document; an invalid IANA zone is silently
   * dropped (the service falls back to UTC) rather than failing the request.
   *
   * @param jobOrderId job-order id
   * @param handoverId handover id
   * @param userTimeZone IANA zone (e.g. {@code Europe/Berlin}); optional
   * @return PDF body with {@code application/pdf} and attachment Content-Disposition
   */
  @GetMapping("/{jobOrderId}/handovers/{handoverId}/report")
  @Operation(
      summary = "Download handover report PDF",
      description = "Generates and downloads a PDF handover report for a persisted handover.")
  @io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "PDF generated successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Job order or handover not found")
  })
  @PreAuthorize("hasAnyRole('LOGISTICIAN', 'OFFICER', 'ADMIN')")
  public ResponseEntity<byte[]> downloadHandoverReport(
      @PathVariable UUID jobOrderId,
      @PathVariable UUID handoverId,
      @RequestHeader(value = "X-User-Time-Zone", required = false) String userTimeZone) {
    java.time.ZoneId userZone = null;
    if (userTimeZone != null && !userTimeZone.isBlank()) {
      try {
        userZone = java.time.ZoneId.of(userTimeZone);
      } catch (java.time.DateTimeException ex) {
        // Invalid IANA zone in header \u2192 fall back to UTC inside the service.
      }
    }
    byte[] pdf =
        jobOrderHandoverReportService.generateHandoverReport(jobOrderId, handoverId, userZone);
    String filename = "uebergabeprotokoll-" + jobOrderId + ".pdf";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDispositionFormData("attachment", filename);
    return ResponseEntity.ok().headers(headers).body(pdf);
  }

  /**
   * Renders a preview PDF from unsaved handover data — used by the create-handover form to show the
   * document before commit. Nothing is persisted.
   *
   * @param jobOrderId job-order id
   * @param dto unsaved handover data
   * @return PDF body with {@code application/pdf} and attachment Content-Disposition
   */
  @PostMapping("/{jobOrderId}/handovers/report/preview")
  @Operation(
      summary = "Preview handover report PDF",
      description = "Generates a PDF handover report preview from unsaved handover data.")
  @io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "PDF generated successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Invalid request data"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden")
  })
  @PreAuthorize("hasAnyRole('LOGISTICIAN', 'OFFICER', 'ADMIN')")
  public ResponseEntity<byte[]> previewHandoverReport(
      @PathVariable UUID jobOrderId, @RequestBody @Valid HandoverReportPreviewRequestDto dto) {
    byte[] pdf = jobOrderHandoverReportService.generateHandoverReportPreview(dto);
    String filename = "uebergabeprotokoll-vorschau.pdf";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDispositionFormData("attachment", filename);
    return ResponseEntity.ok().headers(headers).body(pdf);
  }

  /**
   * Creates a new job order. {@code permitAll()} so any squadron member — including unauthenticated
   * guests using the public request form — can file a request. Anonymous callers receive a redacted
   * response that drops {@code assignees}, {@code handovers} and {@code version}: the created order
   * has no assignees / handovers at create time anyway, and the optimistic-lock version has no
   * purpose for a caller that cannot update the order (PUT/DELETE require LOGISTICIAN+). The {@code
   * cleanup…ForGuest} naming follows the convention recognised by the ArchUnit rule {@code
   * anonymousReadableMissionEndpointsMustRedactGuestPii}.
   *
   * @param dto create payload
   * @param jwt the caller's JWT, or {@code null} for anonymous callers
   * @return the persisted DTO (redacted for anonymous callers)
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Create a new job order",
      description = "Allows anyone to create a job order.")
  @PreAuthorize("permitAll()")
  public JobOrderDto createJobOrder(
      @RequestBody @Valid CreateJobOrderDto dto, @AuthenticationPrincipal Jwt jwt) {
    JobOrderDto created = jobOrderService.createJobOrder(dto);
    if (jwt == null) {
      created = cleanupJobOrderForGuest(created);
    }
    return created;
  }

  /**
   * Creates a new item-based job order. {@code permitAll()} for parity with the material-order
   * create endpoint, with the same guest redaction. The required materials are derived server-side
   * from each ordered item's chosen blueprint and snapshotted onto the order; the response carries
   * the derived per-item materials and the aggregated material view.
   *
   * @param dto item-order create payload (ordered finished items + per-material quality choices)
   * @param jwt the caller's JWT, or {@code null} for anonymous callers
   * @return the persisted DTO (redacted for anonymous callers)
   */
  @PostMapping("/items")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Create a new item job order",
      description =
          "Creates an item-based job order; the required materials are derived from each ordered"
              + " item's blueprint.")
  @PreAuthorize("permitAll()")
  public JobOrderDto createItemJobOrder(
      @RequestBody @Valid CreateJobOrderItemRequestDto dto, @AuthenticationPrincipal Jwt jwt) {
    JobOrderDto created = jobOrderService.createItemJobOrder(dto);
    if (jwt == null) {
      created = cleanupJobOrderForGuest(created);
    }
    return created;
  }

  /**
   * Paged picker of orderable items (blueprint outputs with at least one resolvable material) for
   * the item-order create form. {@code permitAll()} for parity with the public create endpoint so
   * the anonymous request form can populate its item picker. Returns game reference data only (no
   * PII).
   *
   * @param search optional case-insensitive item-name filter
   * @param page zero-based page index
   * @param size page size
   * @param sort sort spec (only {@code name} is whitelisted)
   * @return paged orderable item references
   */
  @GetMapping("/item-catalog")
  @Operation(
      summary = "List orderable items",
      description =
          "Returns a paginated list of items that can be ordered (blueprint outputs with at least"
              + " one resolvable material).")
  @PreAuthorize("permitAll()")
  @Transactional(readOnly = true)
  public PageResponse<GameItemReferenceDto> getOrderableItems(
      @RequestParam(required = false) String search,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size,
      @RequestParam(required = false, defaultValue = "name,asc") String sort) {
    Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("name"), "name");
    Page<GameItemReferenceDto> p = jobOrderItemService.findOrderableItems(search, pageable);
    return new PageResponse<>(
        p.getContent(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  /**
   * Lists the blueprints that produce a given orderable item. Drives the create form's blueprint
   * picker, shown when an item has more than one recipe (issue #304 decision 2).
   *
   * @param gameItemId the orderable item
   * @return blueprint references producing that item
   */
  @GetMapping("/item-catalog/{gameItemId}/blueprints")
  @Operation(
      summary = "List blueprints for an orderable item",
      description = "Returns the blueprints that produce the given item.")
  @PreAuthorize("permitAll()")
  @Transactional(readOnly = true)
  public List<BlueprintReferenceDto> getBlueprintsForItem(@PathVariable UUID gameItemId) {
    return jobOrderItemService.blueprintsForItem(gameItemId);
  }

  /**
   * Previews the material derivation for a chosen blueprint at a given amount: resolved materials
   * (with default quality), adoptable sub-assembly suggestions, and unresolved-ingredient names for
   * the create-form warning banner.
   *
   * @param blueprintId the chosen blueprint
   * @param amount the whole-unit amount to scale by (defaults to 1)
   * @return the derivation preview
   */
  @GetMapping("/item-catalog/blueprints/{blueprintId}/derivation")
  @Operation(
      summary = "Preview blueprint material derivation",
      description =
          "Returns the materials, sub-assembly suggestions and unresolved ingredients derived from"
              + " a blueprint at the given amount.")
  @PreAuthorize("permitAll()")
  @Transactional(readOnly = true)
  public ItemDerivationDto getBlueprintDerivation(
      @PathVariable UUID blueprintId,
      @RequestParam(required = false, defaultValue = "1") int amount) {
    return jobOrderItemService.deriveForPreview(blueprintId, amount);
  }

  /**
   * Strips fields from a job-order DTO that an anonymous caller has no business seeing or that
   * carry no value for them: the {@code assignees} list (would expose member PII if the order ever
   * had assignees at create time — defence-in-depth), the {@code handovers} list (logistician audit
   * trail) and the optimistic-lock {@code version} (anonymous cannot update the order). The {@code
   * id} / {@code displayId} / squadron references / {@code type} / {@code materials} / {@code
   * items} / {@code aggregatedMaterials} / status are preserved so the public form can show a
   * confirmation page with the order number for either order kind. The order's own free-text {@code
   * comment} is preserved — it is the order's own note, not collaborator-identifying data.
   *
   * @param dto the persisted job-order DTO
   * @return a slim acknowledgement DTO safe for anonymous callers
   */
  private JobOrderDto cleanupJobOrderForGuest(JobOrderDto dto) {
    return new JobOrderDto(
        dto.id(),
        dto.displayId(),
        dto.creatingSquadron(),
        dto.requestingSquadron(),
        dto.handle(),
        dto.comment(),
        dto.priority(),
        dto.status(),
        dto.type(),
        dto.materials(),
        dto.items(),
        dto.aggregatedMaterials(),
        java.util.Collections.emptyList(),
        java.util.Collections.emptyList(),
        java.util.Collections.emptyList(),
        dto.createdAt(),
        null);
  }

  /**
   * Paged job-order list. Default sort is by {@code priority,asc} (lowest priority = top of queue),
   * filterable by one or more statuses and optionally by squadron involvement (creating OR
   * requesting). The squadron filter is a UI display preference rather than an access-control gate
   * — Job Orders are a cross-staffel workspace (MULTI_SQUADRON_PLAN.md section 4.4), and any
   * authenticated caller is allowed to ask for the cross-staffel union by omitting the parameter.
   *
   * @param status optional status filter (logical OR across values)
   * @param squadronId optional squadron filter; matches orders whose creating OR requesting
   *     squadron equals this id. {@code null} means "no squadron restriction" (cross-staffel view).
   * @return paged job-order DTOs
   */
  @GetMapping
  @Operation(
      summary = "Get all job orders",
      description = "Returns a paginated list of job orders.")
  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public PageResponse<JobOrderDto> getAllJobOrders(
      @RequestParam(required = false) List<JobOrderStatus> status,
      @RequestParam(required = false) UUID squadronId,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size,
      @RequestParam(required = false, defaultValue = "priority,asc") String sort) {
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page, size, sort, Set.of("priority", "createdAt"), "priority");
    Page<JobOrderDto> p = jobOrderService.getAllJobOrders(status, squadronId, pageable);
    return new PageResponse<>(
        p.getContent(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  /**
   * Lightweight projection (id + label) of active job orders for typeaheads. Excludes terminal
   * states.
   *
   * @return active job orders as reference DTOs
   */
  @GetMapping("/lookup")
  @Operation(
      summary = "Lookup active job orders",
      description = "Returns a reference list of active job orders.")
  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public List<de.greluc.krt.iri.basetool.backend.model.dto.JobOrderReferenceDto> lookupJobOrders() {
    return jobOrderService.findAllActiveReference();
  }

  /**
   * Returns a single job order with its per-material stock totals (linked inventory items summed
   * server-side, so the frontend doesn't have to re-aggregate).
   *
   * @param id job-order id
   * @return the job-order DTO
   */
  @GetMapping("/{id}")
  @Operation(
      summary = "Get job order by ID",
      description = "Returns a job order and calculates the current material stock.")
  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public JobOrderDto getJobOrderById(@PathVariable UUID id) {
    return jobOrderService.getJobOrderById(id);
  }

  /**
   * Returns every inventory item linked to a specific material of a job order. Drives the
   * per-material drill-down in the order detail view.
   *
   * @param id job-order id
   * @param matId material id
   * @return inventory-item DTOs
   */
  @GetMapping("/{id}/materials/{matId}/inventory")
  @Operation(
      summary = "Get inventory items for a job order material",
      description = "Returns all inventory items linked to a specific material in a job order.")
  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public List<de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemDto>
      getInventoryItemsForJobOrderMaterial(@PathVariable UUID id, @PathVariable UUID matId) {
    return jobOrderService.getInventoryItemsForJobOrderMaterial(id, matId);
  }

  /**
   * Updates the status. Transitions to a terminal state (COMPLETED, REJECTED) cascade an atomic
   * unlink of every inventory item that pointed at the order, run after the dirty-check phase
   * (canonical {@code completeJobOrderWithinTransaction} pattern — the inner method is {@code
   * MANDATORY} and relies on dirty-checking instead of issuing its own {@code save}/{@code flush},
   * which would otherwise collide with the already-incremented {@code @Version}).
   *
   * @param id job-order id
   * @param dto status payload (carries the expected version)
   * @return the persisted DTO
   */
  @PutMapping("/{id}/status")
  @Operation(
      summary = "Update job order status",
      description =
          "Updates the status of a job order. For terminal statuses (COMPLETED, REJECTED), all"
              + " linked inventory items are unlinked atomically. Requires the current version for"
              + " optimistic locking.")
  @io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Status updated successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden – insufficient role"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Job order not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "Conflict – optimistic locking failure (version mismatch)")
  })
  @PreAuthorize("hasRole('LOGISTICIAN')")
  public JobOrderDto updateJobOrderStatus(
      @PathVariable UUID id, @RequestBody @Valid UpdateJobOrderStatusDto dto) {
    return jobOrderService.updateJobOrderStatus(id, dto);
  }

  /**
   * Sets the priority and shifts every other order to keep the queue contiguous. The service
   * acquires a pessimistic write lock for the reorder to keep concurrent priority changes
   * consistent.
   *
   * @param id job-order id
   * @param priority new priority slot (lower = earlier in queue)
   * @return the persisted DTO
   */
  @PutMapping("/{id}/priority")
  @Operation(
      summary = "Update job order priority",
      description = "Updates priority and shifts others.")
  @PreAuthorize("hasRole('LOGISTICIAN')")
  public JobOrderDto updateJobOrderPriority(@PathVariable UUID id, @RequestParam Integer priority) {
    return jobOrderService.updateJobOrderPriority(id, priority);
  }

  /**
   * Bulk update — replaces details + the material list in one call.
   *
   * @param id job-order id
   * @param dto update payload (same shape as create)
   * @return the persisted DTO
   */
  @PutMapping("/{id}")
  @Operation(summary = "Update job order", description = "Updates job order details and materials.")
  @PreAuthorize("hasRole('LOGISTICIAN')")
  public JobOrderDto updateJobOrder(
      @PathVariable UUID id, @RequestBody @Valid CreateJobOrderDto dto) {
    return jobOrderService.updateJobOrder(id, dto);
  }

  /**
   * ADMIN-only delete. Surviving orders' priorities shift up to keep the queue contiguous.
   *
   * @param id job-order id
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Delete a job order",
      description = "Deletes a job order and shifts priorities.")
  @PreAuthorize("hasRole('ADMIN')")
  public void deleteJobOrder(@PathVariable UUID id) {
    jobOrderService.deleteJobOrder(id);
  }

  /**
   * Removes a material from the order and atomically unlinks every inventory item that pointed at
   * it.
   *
   * @param jobOrderId job-order id
   * @param materialId material id
   */
  @DeleteMapping("/{jobOrderId}/materials/{materialId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Unlink a material from a job order",
      description =
          "Removes the link between a material and a job order, and unlinks all associated"
              + " inventory items.")
  @io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "Material successfully unlinked"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden – insufficient role"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Job order or material not found")
  })
  @PreAuthorize("hasAnyRole('LOGISTICIAN', 'OFFICER', 'ADMIN')")
  public void unlinkMaterial(@PathVariable UUID jobOrderId, @PathVariable UUID materialId) {
    jobOrderService.unlinkMaterial(jobOrderId, materialId);
  }

  /**
   * Removes a single inventory item from the order — sets {@code jobOrderId=null} via Hibernate
   * dirty-checking (no bulk update, so the surrounding aggregate stays managed).
   *
   * @param jobOrderId job-order id
   * @param inventoryItemId inventory-item id
   */
  @DeleteMapping("/{jobOrderId}/inventory/{inventoryItemId}/unlink")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Unlink a single inventory item from a job order",
      description =
          "Removes the link between a single inventory item and a job order by setting jobOrderId"
              + " to null. Uses Hibernate dirty-checking (no bulk update).")
  @io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "Inventory item successfully unlinked"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden – insufficient role"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Job order or inventory item not found")
  })
  @PreAuthorize("hasAnyRole('LOGISTICIAN', 'OFFICER', 'ADMIN')")
  public void unlinkInventoryItem(
      @PathVariable UUID jobOrderId, @PathVariable UUID inventoryItemId) {
    jobOrderService.unlinkInventoryItem(jobOrderId, inventoryItemId);
  }

  /**
   * Adds a user as assignee. Self-assignment works for everyone; assigning someone else requires
   * LOGISTICIAN or above (enforced by {@link #verifyAssigneeAccess} at the HTTP boundary so the
   * service stays free of {@code SecurityContextHolder} reads).
   *
   * @param id job-order id
   * @param userId target user id
   * @param jwt caller's JWT
   * @return the persisted DTO
   */
  @PostMapping("/{id}/assignees/{userId}")
  @Operation(summary = "Add an assignee", description = "Adds a user to the job order.")
  @PreAuthorize("isAuthenticated()")
  public JobOrderDto addAssignee(
      @PathVariable UUID id, @PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
    verifyAssigneeAccess(jwt, userId);
    return jobOrderService.addAssignee(id, userId);
  }

  /**
   * Removes a user as assignee. Same self-or-logistician rule as {@link #addAssignee}.
   *
   * @param id job-order id
   * @param userId target user id
   * @param jwt caller's JWT
   * @return the persisted DTO
   */
  @DeleteMapping("/{id}/assignees/{userId}")
  @Operation(summary = "Remove an assignee", description = "Removes a user from the job order.")
  @PreAuthorize("isAuthenticated()")
  public JobOrderDto removeAssignee(
      @PathVariable UUID id, @PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
    verifyAssigneeAccess(jwt, userId);
    return jobOrderService.removeAssignee(id, userId);
  }

  /**
   * Enforces the self-or-logistician rule for assignee mutations. Throws {@link
   * AccessDeniedException} when a non-LOGISTICIAN caller tries to modify someone else's assignment.
   *
   * @param jwt caller's JWT
   * @param targetUserId user id being added/removed
   */
  private void verifyAssigneeAccess(Jwt jwt, UUID targetUserId) {
    UUID currentUserId = userService.getUserIdFromJwt(jwt);
    if (!currentUserId.equals(targetUserId) && !authHelperService.isLogisticianOrAbove()) {
      throw new AccessDeniedException("Not allowed to modify other users' assignments");
    }
  }
}

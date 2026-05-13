package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.HandoverReportPreviewRequestDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.UpdateJobOrderStatusDto;
import de.greluc.krt.iri.basetool.backend.service.AuthHelperService;
import de.greluc.krt.iri.basetool.backend.service.JobOrderHandoverReportService;
import de.greluc.krt.iri.basetool.backend.service.JobOrderHandoverService;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Operations related to job orders")
public class JobOrderController {
  private final JobOrderService jobOrderService;
  private final JobOrderHandoverService jobOrderHandoverService;
  private final JobOrderHandoverReportService jobOrderHandoverReportService;
  private final UserService userService;
  private final AuthHelperService authHelperService;

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

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Create a new job order",
      description = "Allows anyone to create a job order.")
  @PreAuthorize("permitAll()")
  public JobOrderDto createJobOrder(@RequestBody @Valid CreateJobOrderDto dto) {
    return jobOrderService.createJobOrder(dto);
  }

  @GetMapping
  @Operation(
      summary = "Get all job orders",
      description = "Returns a paginated list of job orders.")
  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public PageResponse<JobOrderDto> getAllJobOrders(
      @RequestParam(required = false) List<JobOrderStatus> status,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size,
      @RequestParam(required = false, defaultValue = "priority,asc") String sort) {
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page, size, sort, Set.of("priority", "createdAt"), "priority");
    Page<JobOrderDto> p = jobOrderService.getAllJobOrders(status, pageable);
    return new PageResponse<>(
        p.getContent(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  @GetMapping("/lookup")
  @Operation(
      summary = "Lookup active job orders",
      description = "Returns a reference list of active job orders.")
  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public List<de.greluc.krt.iri.basetool.backend.model.dto.JobOrderReferenceDto> lookupJobOrders() {
    return jobOrderService.findAllActiveReference();
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get job order by ID",
      description = "Returns a job order and calculates the current material stock.")
  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public JobOrderDto getJobOrderById(@PathVariable UUID id) {
    return jobOrderService.getJobOrderById(id);
  }

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

  @PutMapping("/{id}/status")
  @Operation(
      summary = "Update job order status",
      description =
          "Updates the status of a job order. For terminal statuses (COMPLETED, REJECTED), all linked inventory items are unlinked atomically. Requires the current version for optimistic locking.")
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

  @PutMapping("/{id}/priority")
  @Operation(
      summary = "Update job order priority",
      description = "Updates priority and shifts others.")
  @PreAuthorize("hasRole('LOGISTICIAN')")
  public JobOrderDto updateJobOrderPriority(@PathVariable UUID id, @RequestParam Integer priority) {
    return jobOrderService.updateJobOrderPriority(id, priority);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update job order", description = "Updates job order details and materials.")
  @PreAuthorize("hasRole('LOGISTICIAN')")
  public JobOrderDto updateJobOrder(
      @PathVariable UUID id, @RequestBody @Valid CreateJobOrderDto dto) {
    return jobOrderService.updateJobOrder(id, dto);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Delete a job order",
      description = "Deletes a job order and shifts priorities.")
  @PreAuthorize("hasRole('ADMIN')")
  public void deleteJobOrder(@PathVariable UUID id) {
    jobOrderService.deleteJobOrder(id);
  }

  @DeleteMapping("/{jobOrderId}/materials/{materialId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Unlink a material from a job order",
      description =
          "Removes the link between a material and a job order, and unlinks all associated inventory items.")
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

  @DeleteMapping("/{jobOrderId}/inventory/{inventoryItemId}/unlink")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Unlink a single inventory item from a job order",
      description =
          "Removes the link between a single inventory item and a job order by setting jobOrderId to null. Uses Hibernate dirty-checking (no bulk update).")
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

  @PostMapping("/{id}/assignees/{userId}")
  @Operation(summary = "Add an assignee", description = "Adds a user to the job order.")
  @PreAuthorize("isAuthenticated()")
  public JobOrderDto addAssignee(
      @PathVariable UUID id, @PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
    verifyAssigneeAccess(jwt, userId);
    return jobOrderService.addAssignee(id, userId);
  }

  @DeleteMapping("/{id}/assignees/{userId}")
  @Operation(summary = "Remove an assignee", description = "Removes a user from the job order.")
  @PreAuthorize("isAuthenticated()")
  public JobOrderDto removeAssignee(
      @PathVariable UUID id, @PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
    verifyAssigneeAccess(jwt, userId);
    return jobOrderService.removeAssignee(id, userId);
  }

  private void verifyAssigneeAccess(Jwt jwt, UUID targetUserId) {
    UUID currentUserId = userService.getUserIdFromJwt(jwt);
    if (!currentUserId.equals(targetUserId) && !authHelperService.isLogisticianOrAbove()) {
      throw new AccessDeniedException("Not allowed to modify other users' assignments");
    }
  }
}

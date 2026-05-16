package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.OperationMapper;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationFinanceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationPayoutDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationPayoutStatusUpdateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationUpdateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.service.OperationFinanceService;
import de.greluc.krt.iri.basetool.backend.service.OperationService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface over the Operation aggregate. CRUD + aggregated finance/payout endpoints. Mutations
 * require MISSION_MANAGER (which the JWT-to-authorities converter also grants users with {@code
 * app_user.is_mission_manager=true}, even without the Keycloak realm role). Delete is ADMIN-only.
 *
 * <p>The status transitions follow the {@code OperationStatus.canTransitionTo} state machine:
 * {@code PLANNED → {ACTIVE, CANCELED}}, {@code ACTIVE → {COMPLETED, CANCELED}}, terminal states are
 * sticky. Admins can bypass the gate — {@link #updateOperation} resolves the role at the HTTP
 * boundary and hands a boolean to the service to keep {@code SecurityContextHolder} out of the
 * service layer (the ArchUnit rule).
 */
@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
@Tag(
    name = "Operations",
    description =
        "Operation aggregate: groups multiple missions under one umbrella and exposes "
            + "aggregated finance and payout views.")
public class OperationController {

  // Whitelisted sort fields. Anything else from the request will cause
  // PaginationUtil to throw IllegalArgumentException, which the global
  // handler turns into a 400 — never let Sort accept arbitrary user input
  // (unstable ordering + information disclosure risk via column names).
  private static final Set<String> ALLOWED_SORT =
      Set.of("id", "name", "status", "description", "createdAt", "updatedAt");

  private final OperationService operationService;
  private final OperationMapper operationMapper;
  private final OperationFinanceService operationFinanceService;

  // hasRole('MISSION_MANAGER') below also matches users with app_user.is_mission_manager=true —
  // CustomJwtGrantedAuthoritiesConverter injects ROLE_MISSION_MANAGER from the DB flag at
  // JWT-decode time.

  /**
   * Returns paged operation DTOs (whitelist-enforced sort, {@code id} appended as tiebreaker).
   *
   * @return paged operation DTOs (whitelist-enforced sort, {@code id} appended as tiebreaker)
   */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "List all operations (paginated)",
      description =
          "Returns operations ordered by `sort` (default `createdAt,desc`). "
              + "Allowed sort fields: id, name, status, description, createdAt, updatedAt. "
              + "`id` is appended automatically as a stable tiebreaker.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Paginated list of operations."),
    @ApiResponse(responseCode = "400", description = "Unsupported sort field."),
    @ApiResponse(responseCode = "401", description = "Caller is not authenticated.")
  })
  public PageResponse<OperationDto> getAllOperations(
      @RequestParam(required = false, defaultValue = "0") Integer page,
      @RequestParam(required = false, defaultValue = "10") Integer size,
      @RequestParam(required = false, defaultValue = "createdAt,desc") String sort) {
    Pageable pageable =
        PaginationUtil.createPageRequest(page, size, sort, ALLOWED_SORT, "createdAt");
    Page<OperationDto> dtoPage =
        operationService.getAllOperations(pageable).map(operationMapper::toDto);
    return new PageResponse<>(
        dtoPage.getContent(),
        dtoPage.getNumber(),
        dtoPage.getSize(),
        dtoPage.getTotalElements(),
        dtoPage.getTotalPages(),
        PaginationUtil.toSortStrings(dtoPage.getSort()));
  }

  /**
   * Returns the operation DTO.
   *
   * @param id operation id
   * @return the operation DTO
   */
  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Get operation by ID")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Operation found."),
    @ApiResponse(responseCode = "401", description = "Caller is not authenticated."),
    @ApiResponse(responseCode = "404", description = "Operation not found.")
  })
  public OperationDto getOperationById(@PathVariable UUID id) {
    return operationMapper.toDto(operationService.getOperationById(id));
  }

  /**
   * Aggregated finance roll-up across all missions of the operation.
   *
   * @param id operation id
   * @return finance summary DTO
   */
  @GetMapping("/{id}/finances")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Get aggregated finances for an operation",
      description =
          "Sums income, expenses and refinery profit/loss across all missions "
              + "that belong to the operation. Refinery order profit is calculated as "
              + "`oreSales - expenses - otherExpenses`; null values are treated as 0.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Finance summary returned."),
    @ApiResponse(responseCode = "401", description = "Caller is not authenticated."),
    @ApiResponse(responseCode = "404", description = "Operation not found.")
  })
  public OperationFinanceDto getOperationFinances(@PathVariable UUID id) {
    return operationFinanceService.getOperationFinances(id);
  }

  /**
   * Per-participant payout breakdown: time-share percentage, the actual money number (expense
   * reimbursement + share-of-pool), and the mission-manager-set paid-out audit flag (DONATE in any
   * sub-mission is sticky for the whole operation).
   *
   * @param id operation id
   * @return payout rows sorted by participant name
   */
  @GetMapping("/{id}/payouts")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Get participation payout breakdown with amounts and paid-out status",
      description =
          "For each participant across all missions of the operation, returns the time-share "
              + "(percent), the personal out-of-pocket reimbursement (mission EXPENSE entries "
              + "they own + refinery `expenses + otherExpenses` they own), the per-share amount "
              + "(totalSum × percentage / 100, 0 for DONATE), the in-game banking transfer fee "
              + "deducted from the gross payout (`transferFee`, rate from the runtime-editable "
              + "`operation.transfer_fee_rate` system setting, default 0.005 = 0.5%) and the "
              + "resulting net payout amount (`payoutAmount = personalExpenses + shareAmount − "
              + "transferFee`). Also includes the paid-out flag set by mission managers "
              + "(`paidOut`, `paidOutAt`, `paidOutByName`) — absent flag rows are treated as "
              + "`paidOut=false`. A participant who chose DONATE in any mission is treated as "
              + "DONATE for the whole operation; their reimbursement is still paid (it is their "
              + "own money returned) but their share is contributed to the org.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Payout breakdown returned."),
    @ApiResponse(responseCode = "401", description = "Caller is not authenticated."),
    @ApiResponse(responseCode = "404", description = "Operation not found.")
  })
  public List<OperationPayoutDto> getOperationPayouts(@PathVariable UUID id) {
    return operationService.getOperationPayouts(id);
  }

  /**
   * Toggles the per-participant paid-out flag on the operation. Reserved for mission managers
   * (which the role hierarchy widens to admins and officers).
   *
   * @param id operation id
   * @param dto request body with the participant key and new paid-out value
   * @return the refreshed payout row for the participant, so the caller can patch a single row of
   *     its table without re-fetching the whole breakdown
   */
  @PutMapping("/{id}/payouts/paid-out")
  @PreAuthorize("hasRole('MISSION_MANAGER')")
  @Operation(
      summary = "Toggle the per-participant paid-out flag for an operation",
      description =
          "Records that a mission manager has marked the participant as paid out (or unset "
              + "it). Last-writer-wins: no client-supplied version is required because the "
              + "field is a boolean. The audit fields (`paidOutAt`, `paidOutByUser`) are "
              + "always refreshed when `paidOut=true`; setting `paidOut=false` keeps the last "
              + "audit fields as a historical record. The participantKey matches the opaque "
              + "key returned by `/payouts` (real user UUID stringified or `guest_<name>`).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Paid-out flag updated."),
    @ApiResponse(responseCode = "400", description = "Validation failed."),
    @ApiResponse(responseCode = "401", description = "Caller is not authenticated."),
    @ApiResponse(responseCode = "403", description = "Caller lacks the MISSION_MANAGER role."),
    @ApiResponse(
        responseCode = "404",
        description = "Operation not found, or participantKey is not part of the operation.")
  })
  public OperationPayoutDto setPayoutStatus(
      @PathVariable UUID id, @Valid @RequestBody OperationPayoutStatusUpdateDto dto) {
    return operationService.setPayoutStatus(id, dto.participantKey(), dto.paidOut());
  }

  /**
   * Creates a new operation.
   *
   * @param createDto create payload
   * @return the persisted DTO
   */
  @PostMapping
  @PreAuthorize("hasRole('MISSION_MANAGER')")
  @Operation(summary = "Create a new operation")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Operation created."),
    @ApiResponse(responseCode = "400", description = "Validation failed."),
    @ApiResponse(responseCode = "401", description = "Caller is not authenticated."),
    @ApiResponse(responseCode = "403", description = "Caller lacks the MISSION_MANAGER role.")
  })
  public OperationDto createOperation(@Valid @RequestBody OperationCreateDto createDto) {
    de.greluc.krt.iri.basetool.backend.model.Operation operation =
        operationMapper.toEntity(createDto);
    return operationMapper.toDto(operationService.createOperation(operation));
  }

  /**
   * Updates an existing operation with optimistic-lock + state-machine validation. Admin role is
   * resolved at the HTTP boundary and forwarded as a boolean so the service stays free of {@code
   * SecurityContextHolder} reads (ArchUnit rule).
   *
   * @param id operation id
   * @param updateDto update payload (carries expected version + new status)
   * @param authentication current Spring Security authentication
   * @return the persisted DTO
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('MISSION_MANAGER')")
  @Operation(
      summary = "Update an existing operation",
      description =
          "Requires the current `version` field in the body for optimistic locking. "
              + "A stale version triggers a 409 Conflict. Status changes are validated against "
              + "the state machine PLANNED -> {ACTIVE, CANCELED}, ACTIVE -> {COMPLETED, CANCELED}; "
              + "COMPLETED and CANCELED are terminal. Callers with ROLE_ADMIN bypass that gate.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Operation updated."),
    @ApiResponse(
        responseCode = "400",
        description = "Validation failed or invalid status transition."),
    @ApiResponse(responseCode = "401", description = "Caller is not authenticated."),
    @ApiResponse(responseCode = "403", description = "Caller lacks the MISSION_MANAGER role."),
    @ApiResponse(responseCode = "404", description = "Operation not found."),
    @ApiResponse(responseCode = "409", description = "Optimistic lock conflict — fetch and retry.")
  })
  public OperationDto updateOperation(
      @PathVariable UUID id,
      @Valid @RequestBody OperationUpdateDto updateDto,
      Authentication authentication) {
    // Resolve the "can override the state machine" flag here, at the HTTP boundary,
    // so the service stays pure business logic and does not have to read the
    // SecurityContextHolder itself (architecture rule enforced by ArchitectureTest).
    boolean canOverrideStatus =
        authentication != null
            && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    return operationMapper.toDto(
        operationService.updateOperation(id, updateDto, canOverrideStatus));
  }

  /**
   * Deletes the operation but keeps its missions alive (sets {@code mission.operation=null}).
   * ADMIN-only.
   *
   * @param id operation id
   * @return 204 No Content
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Delete an operation",
      description =
          "Unlinks every mission that belongs to the operation (sets "
              + "`mission.operation` to null) and then deletes the operation. Missions and "
              + "all their references (participants, finance entries, inventory items, "
              + "refinery orders) survive intact.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Operation deleted."),
    @ApiResponse(responseCode = "401", description = "Caller is not authenticated."),
    @ApiResponse(responseCode = "403", description = "Caller lacks the ADMIN role."),
    @ApiResponse(responseCode = "404", description = "Operation not found.")
  })
  public ResponseEntity<Void> deleteOperation(@PathVariable UUID id) {
    operationService.deleteOperation(id);
    return ResponseEntity.noContent().build();
  }
}

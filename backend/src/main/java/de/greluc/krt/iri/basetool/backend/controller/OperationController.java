package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.OperationMapper;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationFinanceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationPayoutDto;
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
import org.springframework.web.bind.annotation.*;

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

  @GetMapping("/{id}/payouts")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Get participation-time payout breakdown",
      description =
          "For each participant across all missions of the operation, returns the "
              + "share (in percent) of valid participation time. A participant who chose "
              + "DONATE in any mission is treated as DONATE for the whole operation.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Payout breakdown returned."),
    @ApiResponse(responseCode = "401", description = "Caller is not authenticated."),
    @ApiResponse(responseCode = "404", description = "Operation not found.")
  })
  public List<OperationPayoutDto> getOperationPayouts(@PathVariable UUID id) {
    return operationService.getOperationPayouts(id);
  }

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

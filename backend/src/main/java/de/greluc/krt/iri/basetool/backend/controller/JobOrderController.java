package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.service.JobOrderService;
import de.greluc.krt.iri.basetool.backend.service.JobOrderHandoverService;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverCreateDto;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Operations related to job orders")
public class JobOrderController {
    private final JobOrderService jobOrderService;
    private final JobOrderHandoverService jobOrderHandoverService;
    private final UserService userService;
    private final RoleHierarchy roleHierarchy;

    @PostMapping("/{id}/handovers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a job order handover", description = "Logs a handover of materials for this job order.")
    @PreAuthorize("hasRole('LOGISTICIAN') or hasRole('OFFICER') or hasRole('ADMIN')")
    public JobOrderHandoverDto createHandover(@PathVariable UUID id, @RequestBody @Valid JobOrderHandoverCreateDto dto) {
        return jobOrderHandoverService.createHandover(id, dto);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new job order", description = "Allows anyone to create a job order.")
    public JobOrderDto createJobOrder(@RequestBody @Valid CreateJobOrderDto dto) {
        return jobOrderService.createJobOrder(dto);
    }

    @GetMapping
    @Operation(summary = "Get all job orders", description = "Returns a paginated list of job orders.")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public PageResponse<JobOrderDto> getAllJobOrders(
            @RequestParam(required = false) List<JobOrderStatus> status,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "priority,asc") String sort) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("priority", "createdAt"), "priority");
        Page<JobOrderDto> p = jobOrderService.getAllJobOrders(status, pageable);
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @GetMapping("/lookup")
    @Operation(summary = "Lookup active job orders", description = "Returns a reference list of active job orders.")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<de.greluc.krt.iri.basetool.backend.model.dto.JobOrderReferenceDto> lookupJobOrders() {
        return jobOrderService.findAllActiveReference();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get job order by ID", description = "Returns a job order and calculates the current material stock.")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public JobOrderDto getJobOrderById(@PathVariable UUID id) {
        return jobOrderService.getJobOrderById(id);
    }

    @GetMapping("/{id}/materials/{matId}/inventory")
    @Operation(summary = "Get inventory items for a job order material", description = "Returns all inventory items linked to a specific material in a job order.")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemDto> getInventoryItemsForJobOrderMaterial(@PathVariable UUID id, @PathVariable UUID matId) {
        return jobOrderService.getInventoryItemsForJobOrderMaterial(id, matId);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update job order status", description = "Updates the status of a job order.")
    @PreAuthorize("hasRole('LOGISTICIAN')")
    public JobOrderDto updateJobOrderStatus(@PathVariable UUID id, @RequestParam JobOrderStatus status) {
        return jobOrderService.updateJobOrderStatus(id, status);
    }

    @PutMapping("/{id}/priority")
    @Operation(summary = "Update job order priority", description = "Updates priority and shifts others.")
    @PreAuthorize("hasRole('LOGISTICIAN')")
    public JobOrderDto updateJobOrderPriority(@PathVariable UUID id, @RequestParam Integer priority) {
        return jobOrderService.updateJobOrderPriority(id, priority);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update job order", description = "Updates job order details and materials.")
    @PreAuthorize("hasRole('LOGISTICIAN')")
    public JobOrderDto updateJobOrder(@PathVariable UUID id, @RequestBody @Valid CreateJobOrderDto dto) {
        return jobOrderService.updateJobOrder(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a job order", description = "Deletes a job order and shifts priorities.")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteJobOrder(@PathVariable UUID id) {
        jobOrderService.deleteJobOrder(id);
    }

    @PostMapping("/{id}/assignees/{userId}")
    @Operation(summary = "Add an assignee", description = "Adds a user to the job order.")
    @PreAuthorize("isAuthenticated()")
    public JobOrderDto addAssignee(
            @PathVariable UUID id, 
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt) {
        verifyAssigneeAccess(jwt, userId);
        return jobOrderService.addAssignee(id, userId);
    }

    @DeleteMapping("/{id}/assignees/{userId}")
    @Operation(summary = "Remove an assignee", description = "Removes a user from the job order.")
    @PreAuthorize("isAuthenticated()")
    public JobOrderDto removeAssignee(
            @PathVariable UUID id, 
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt) {
        verifyAssigneeAccess(jwt, userId);
        return jobOrderService.removeAssignee(id, userId);
    }

    private void verifyAssigneeAccess(Jwt jwt, UUID targetUserId) {
        UUID currentUserId = userService.getUserIdFromJwt(jwt);
        if (!currentUserId.equals(targetUserId)) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isAdminOrOfficer = false;
            if (auth != null) {
                Collection<? extends GrantedAuthority> reachableAuthorities = roleHierarchy.getReachableGrantedAuthorities(auth.getAuthorities());
                isAdminOrOfficer = reachableAuthorities.stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_LOGISTICIAN"));
            }
            if (!isAdminOrOfficer) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to modify other users' assignments");
            }
        }
    }
}

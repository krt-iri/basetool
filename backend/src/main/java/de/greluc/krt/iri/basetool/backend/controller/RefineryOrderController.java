package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderStoreDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderListDto;
import de.greluc.krt.iri.basetool.backend.mapper.RefineryOrderMapper;
import de.greluc.krt.iri.basetool.backend.service.RefineryOrderService;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
@RestController
@RequestMapping("/api/v1/refinery-orders")
@RequiredArgsConstructor
@Transactional
public class RefineryOrderController {
    private final RefineryOrderService refineryOrderService;
    private final UserService userService;
    private final RefineryOrderMapper mapper;
    private final RoleHierarchy roleHierarchy;

    // User endpoints

    @GetMapping("/my-orders")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public PageResponse<RefineryOrderListDto> getMyRefineryOrders(@AuthenticationPrincipal Jwt jwt,
                                                           @RequestParam(required = false) List<de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus> status,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer size,
                                                           @RequestParam(required = false) String sort) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("startedAt", "durationMinutes", "expenses", "id"), "startedAt");
        Page<RefineryOrder> p = refineryOrderService.getMyRefineryOrders(userService.getUserIdFromJwt(jwt), status, pageable);
        List<RefineryOrderListDto> dtoList = p.getContent().stream().map(mapper::toListDto).toList();
        return new PageResponse<>(dtoList, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public RefineryOrderDto getRefineryOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable @NotNull UUID id) {
        RefineryOrder order = refineryOrderService.getRefineryOrder(id);
        
        if (isLogisticianOrAbove() || (order.getOwner() != null && order.getOwner().getId().equals(userService.getUserIdFromJwt(jwt)))) {
            return mapper.toDto(order);
        }
        
        // For now, allow read access to everyone if they can see the list
        return mapper.toDto(order);
    }

    @GetMapping("/mission/{missionId}")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<RefineryOrderListDto> getMissionRefineryOrders(@AuthenticationPrincipal Jwt jwt, @PathVariable @NotNull UUID missionId) {
        boolean isLogistician = isLogisticianOrAbove();
        if (isLogistician) {
            return refineryOrderService.getMissionRefineryOrders(missionId).stream().map(mapper::toListDto).toList();
        }
        return refineryOrderService.getMissionRefineryOrders(missionId, userService.getUserIdFromJwt(jwt)).stream().map(mapper::toListDto).toList();
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public RefineryOrderDto createMyRefineryOrder(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid @NotNull RefineryOrderDto orderDto) {
        UUID userId = userService.getUserIdFromJwt(jwt);
        if (orderDto.owner() != null && orderDto.owner().id() != null) {
            if (isLogisticianOrAbove()) {
                userId = orderDto.owner().id();
            }
        }
        return mapper.toDto(refineryOrderService.createRefineryOrder(userId, mapper.toEntity(orderDto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public RefineryOrderDto updateMyRefineryOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable @NotNull UUID id, @RequestBody @Valid @NotNull RefineryOrderDto orderDto) {
        UUID callerId = userService.getUserIdFromJwt(jwt);
        RefineryOrder existing = refineryOrderService.getRefineryOrder(id);
        
        boolean isLogistician = isLogisticianOrAbove();
        
        UUID targetUserId = callerId;
        if (isLogistician) {
            if (orderDto.owner() != null && orderDto.owner().id() != null) {
                targetUserId = orderDto.owner().id();
            } else if (existing.getOwner() != null) {
                targetUserId = existing.getOwner().getId();
            }
        } else {
            // Normal user: must be the owner
            if (existing.getOwner() == null || !existing.getOwner().getId().equals(callerId)) {
                throw new AccessDeniedException("Access denied: You do not own this refinery order");
            }
        }

        return mapper.toDto(refineryOrderService.updateRefineryOrder(targetUserId, id, mapper.toEntity(orderDto), isLogistician));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public void deleteMyRefineryOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable @NotNull UUID id) {
        refineryOrderService.deleteRefineryOrder(userService.getUserIdFromJwt(jwt), id, isLogisticianOrAbove());
    }

    @PostMapping("/{id}/store")
    @PreAuthorize("isAuthenticated()")
    public void storeMyRefineryOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable @NotNull UUID id, @RequestBody @Valid @NotNull RefineryOrderStoreDto dto) {
        refineryOrderService.storeRefineryOrder(userService.getUserIdFromJwt(jwt), id, dto, isLogisticianOrAbove());
    }

    private boolean isLogisticianOrAbove() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            Collection<? extends GrantedAuthority> reachableAuthorities = roleHierarchy.getReachableGrantedAuthorities(auth.getAuthorities());
            return reachableAuthorities.stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_LOGISTICIAN") ||
                                   a.getAuthority().equals("ROLE_ADMIN") ||
                                   a.getAuthority().equals("ROLE_OFFICER"));
        }
        return false;
    }

    // Admin/Officer endpoints

    @GetMapping("/all")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public PageResponse<RefineryOrderListDto> getAllRefineryOrders(@RequestParam(required = false) List<de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus> status,
                                                            @RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size,
                                                            @RequestParam(required = false) String sort) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("startedAt", "durationMinutes", "expenses", "id"), "startedAt");
        Page<RefineryOrder> p = refineryOrderService.getAllRefineryOrders(status, pageable);
        List<RefineryOrderListDto> dtoList = p.getContent().stream().map(mapper::toListDto).toList();
        return new PageResponse<>(dtoList, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('LOGISTICIAN')")
    @Transactional(readOnly = true)
    public PageResponse<RefineryOrderListDto> getUserRefineryOrders(@PathVariable @NotNull UUID userId,
                                                             @RequestParam(required = false) Integer page,
                                                             @RequestParam(required = false) Integer size,
                                                             @RequestParam(required = false) String sort) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("startedAt", "durationMinutes", "expenses", "id"), "startedAt");
        Page<RefineryOrder> p = refineryOrderService.getMyRefineryOrders(userId, pageable);
        List<RefineryOrderListDto> dtoList = p.getContent().stream().map(mapper::toListDto).toList();
        return new PageResponse<>(dtoList, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @PostMapping("/users/{userId}")
    @PreAuthorize("hasRole('LOGISTICIAN')")
    public RefineryOrderDto createUserRefineryOrder(@PathVariable @NotNull UUID userId, @RequestBody @Valid @NotNull RefineryOrderDto orderDto) {
        return mapper.toDto(refineryOrderService.createRefineryOrder(userId, mapper.toEntity(orderDto)));
    }

    @PutMapping("/users/{userId}/{orderId}")
    @PreAuthorize("hasRole('LOGISTICIAN')")
    public RefineryOrderDto updateUserRefineryOrder(@PathVariable @NotNull UUID userId, @PathVariable @NotNull UUID orderId, @RequestBody @Valid @NotNull RefineryOrderDto orderDto) {
        return mapper.toDto(refineryOrderService.updateRefineryOrder(userId, orderId, mapper.toEntity(orderDto), true));
    }

    @DeleteMapping("/users/{userId}/{orderId}")
    @PreAuthorize("hasRole('LOGISTICIAN')")
    public void deleteUserRefineryOrder(@PathVariable @NotNull UUID userId, @PathVariable @NotNull UUID orderId) {
        refineryOrderService.deleteRefineryOrder(userId, orderId, true);
    }
}

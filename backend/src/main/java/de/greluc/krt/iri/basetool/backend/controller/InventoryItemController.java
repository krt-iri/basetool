package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.dto.*;
import de.greluc.krt.iri.basetool.backend.service.InventoryItemService;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryItemController {
    private final InventoryItemService inventoryItemService;
    private final UserService userService;
    private final RoleHierarchy roleHierarchy;

    @GetMapping("/aggregated")
    @Transactional(readOnly = true)
    public PageResponse<AggregatedInventoryDto> getAggregatedInventory(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "material.name,asc;quality,desc;amount,desc") String sort) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("amount", "quality", "material.name"), "material.name");
        Page<AggregatedInventoryDto> p = inventoryItemService.getAggregatedInventory(pageable);
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @GetMapping("/material/{materialId}")
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemDto> getInventoryByMaterial(
            @PathVariable @NotNull UUID materialId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "quality,desc;location.name,asc;amount,desc") String sort) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("amount", "quality", "id", "location.name"), "quality");
        Page<InventoryItemDto> p = inventoryItemService.getInventoryByMaterial(materialId, pageable);
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @GetMapping("/my-inventory")
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemDto> getMyInventory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "material.name,asc;quality,desc;amount,desc") String sort) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("amount", "quality", "id", "material.name"), "quality");
        Page<InventoryItemDto> p = inventoryItemService.getUserInventory(userService.getUserIdFromJwt(jwt), pageable);
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @GetMapping("/my-inventory/grouped")
    @Transactional(readOnly = true)
    public List<de.greluc.krt.iri.basetool.backend.model.dto.GroupedInventoryDto> getMyGroupedInventory(
            @AuthenticationPrincipal Jwt jwt) {
        return inventoryItemService.getMyAggregatedInventory(userService.getUserIdFromJwt(jwt));
    }

    @GetMapping("/all")
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemDto> getAllInventory(
            @RequestParam(required = false) List<UUID> materialIds,
            @RequestParam(required = false) Integer minQuality,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "material.name,asc;quality,desc;amount,desc") String sort) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("amount", "quality", "id", "material.name"), "quality");
        Page<InventoryItemDto> p = inventoryItemService.getAllInventory(materialIds, minQuality, pageable);
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @GetMapping("/all/grouped")
    @Transactional(readOnly = true)
    public List<de.greluc.krt.iri.basetool.backend.model.dto.GroupedInventoryDto> getAllGroupedInventory(
            @RequestParam(required = false) List<UUID> materialIds,
            @RequestParam(required = false) Integer minQuality) {
        return inventoryItemService.getAllAggregatedInventory(materialIds, minQuality);
    }

    @PostMapping
    public InventoryItemDto createInventoryItem(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid InventoryItemCreateDto dto) {
        boolean isLogistician = isLogisticianOrAbove();
        return inventoryItemService.createInventoryItem(dto, userService.getUserIdFromJwt(jwt), isLogistician);
    }

    @PostMapping("/{id}/book-out")
    public void bookOutInventoryItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @NotNull UUID id,
            @RequestBody @Valid InventoryItemBookOutDto dto) {
        boolean isLogistician = isLogisticianOrAbove();
        inventoryItemService.bookOutInventoryItem(id, dto, userService.getUserIdFromJwt(jwt), isLogistician);
    }

    private boolean isLogisticianOrAbove() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            Collection<? extends GrantedAuthority> reachableAuthorities = roleHierarchy.getReachableGrantedAuthorities(auth.getAuthorities());
            return reachableAuthorities.stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_LOGISTICIAN") ||
                                   a.getAuthority().equals("ROLE_ADMIN") ||
                                   a.getAuthority().equals("ROLE_OFFICER"));
        }
        return false;
    }
}

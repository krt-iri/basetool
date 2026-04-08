package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.ShipMapper;
import de.greluc.krt.iri.basetool.backend.model.Ship;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronShipOverviewDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.ShipDto;
import de.greluc.krt.iri.basetool.backend.model.dto.ShipRequestDto;
import de.greluc.krt.iri.basetool.backend.service.HangarService;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hangar")
@RequiredArgsConstructor
public class HangarController {
    private final HangarService hangarService;
    private final UserService userService;
    private final ShipMapper shipMapper;

    @GetMapping("/my-ships")
    @Transactional(readOnly = true)
    public PageResponse<ShipDto> getMyShips(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size,
                                         @RequestParam(required = false) String sort) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("name", "insurance", "fitted", "id"), "name");
        Page<Ship> p = hangarService.getMyShips(userService.getUserIdFromJwt(jwt), pageable);
        List<ShipDto> dtos = p.getContent().stream().map(shipMapper::toDto).toList();
        return new PageResponse<>(dtos, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @GetMapping("/ships")
    @PreAuthorize("hasAuthority('HANGAR_READ')")
    @Transactional(readOnly = true)
    public PageResponse<ShipDto> getAllShips(@RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size,
                                          @RequestParam(required = false) String sort) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("name", "insurance", "fitted", "id"), "name");
        Page<Ship> p = hangarService.getAllShips(pageable);
        List<ShipDto> dtos = p.getContent().stream().map(shipMapper::toDto).toList();
        return new PageResponse<>(dtos, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @GetMapping("/squadron-overview")
    @Transactional(readOnly = true)
    public PageResponse<SquadronShipOverviewDto> getSquadronOverview(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("shipType.name"), "shipType.name");
        Page<SquadronShipOverviewDto> p = hangarService.getSquadronOverview(pageable);
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @PostMapping("/ships")
    @Transactional
    public ShipDto addShip(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid ShipRequestDto shipRequest) {
        return shipMapper.toDto(hangarService.addShip(userService.getUserIdFromJwt(jwt), shipRequest));
    }

    @PutMapping("/ships/{id}")
    @Transactional
    public ShipDto updateMyShip(@AuthenticationPrincipal Jwt jwt, @PathVariable @NotNull UUID id, @RequestBody @Valid @NotNull ShipRequestDto shipRequest) {
        return shipMapper.toDto(hangarService.updateShip(userService.getUserIdFromJwt(jwt), id, shipRequest));
    }

    @DeleteMapping("/ships/{id}")
    public void deleteMyShip(@AuthenticationPrincipal Jwt jwt, @PathVariable @NotNull UUID id) {
        hangarService.deleteShip(userService.getUserIdFromJwt(jwt), id);
    }

    // Admin endpoints

    @GetMapping("/users/{userId}/ships")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<ShipDto> getUserShips(@PathVariable @NotNull UUID userId,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size,
                                           @RequestParam(required = false) String sort) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("name", "insurance", "fitted", "id"), "name");
        Page<Ship> p = hangarService.getMyShips(userId, pageable);
        List<ShipDto> dtos = p.getContent().stream().map(shipMapper::toDto).toList();
        return new PageResponse<>(dtos, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @PostMapping("/users/{userId}/ships")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ShipDto addUserShip(@PathVariable @NotNull UUID userId, @RequestBody @Valid ShipRequestDto shipRequest) {
        return shipMapper.toDto(hangarService.addShip(userId, shipRequest));
    }

    @PutMapping("/users/{userId}/ships/{shipId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ShipDto updateUserShip(@PathVariable @NotNull UUID userId, @PathVariable @NotNull UUID shipId, @RequestBody @Valid @NotNull ShipRequestDto shipRequest) {
        return shipMapper.toDto(hangarService.updateShip(userId, shipId, shipRequest));
    }

    @DeleteMapping("/users/{userId}/ships/{shipId}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUserShip(@PathVariable @NotNull UUID userId, @PathVariable @NotNull UUID shipId) {
        hangarService.deleteShip(userId, shipId);
    }

    @PostMapping("/ships/reset-fitted")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public void resetAllFittedStatus() {
        hangarService.resetAllFittedStatus();
    }
}

package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.model.dto.ShipTypeDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.mapper.ShipMapper;
import de.greluc.krt.iri.basetool.backend.service.ShipTypeService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ship-types")
@RequiredArgsConstructor
@Transactional
public class ShipTypeController {

    private final ShipTypeService shipTypeService;
    private final ShipMapper shipMapper;

    @GetMapping
    public PageResponse<ShipTypeDto> getAllShipTypes(@RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size,
                                                  @RequestParam(required = false) String sort,
                                                  @RequestParam(required = false, defaultValue = "false") boolean includeHidden) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("name", "id"), "name");
        Page<ShipType> p = shipTypeService.getAllShipTypes(pageable, includeHidden);
        List<ShipTypeDto> content = p.getContent().stream().map(shipMapper::shipTypeToDto).toList();
        return new PageResponse<>(content, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @GetMapping("/{id}")
    public ShipTypeDto getShipType(@PathVariable @NotNull UUID id) {
        return shipMapper.shipTypeToDto(shipTypeService.getShipType(id));
    }

    @PutMapping("/{id}/visibility")
    @PreAuthorize("hasRole('ADMIN')")
    public ShipTypeDto updateShipTypeVisibility(@PathVariable @NotNull UUID id, @RequestParam boolean hidden) {
        return shipMapper.shipTypeToDto(shipTypeService.updateShipTypeVisibility(id, hidden));
    }
}

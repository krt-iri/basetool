package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.model.dto.ManufacturerDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.mapper.ManufacturerMapper;
import de.greluc.krt.iri.basetool.backend.service.ManufacturerService;
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
@RequestMapping("/api/v1/manufacturers")
@RequiredArgsConstructor
@Transactional
public class ManufacturerController {

    private final ManufacturerService manufacturerService;
    private final ManufacturerMapper manufacturerMapper;

    @GetMapping
    public PageResponse<ManufacturerDto> getAllManufacturers(@RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size,
                                                          @RequestParam(required = false) String sort,
                                                          @RequestParam(required = false, defaultValue = "false") boolean includeHidden) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("name", "abbreviation", "id"), "name");
        Page<Manufacturer> p = manufacturerService.getAllManufacturers(pageable, includeHidden);
        List<ManufacturerDto> content = p.getContent().stream().map(manufacturerMapper::toDto).toList();
        return new PageResponse<>(content, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @GetMapping("/{id}")
    public ManufacturerDto getManufacturer(@PathVariable @NotNull UUID id) {
        return manufacturerMapper.toDto(manufacturerService.getManufacturer(id));
    }

    @PutMapping("/{id}/visibility")
    @PreAuthorize("hasRole('ADMIN')")
    public ManufacturerDto updateManufacturerVisibility(@PathVariable @NotNull UUID id, @RequestParam boolean hidden) {
        return manufacturerMapper.toDto(manufacturerService.updateManufacturerVisibility(id, hidden));
    }
}

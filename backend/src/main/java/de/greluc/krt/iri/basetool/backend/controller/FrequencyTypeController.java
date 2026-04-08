package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.FrequencyType;
import de.greluc.krt.iri.basetool.backend.model.dto.FrequencyTypeDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.mapper.FrequencyTypeMapper;
import de.greluc.krt.iri.basetool.backend.service.FrequencyTypeService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/frequency-types")
@RequiredArgsConstructor
@Transactional
public class FrequencyTypeController {

    private final FrequencyTypeService frequencyTypeService;
    private final FrequencyTypeMapper frequencyTypeMapper;

    @GetMapping
    public PageResponse<FrequencyTypeDto> getAllFrequencyTypes(@RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size,
                                                            @RequestParam(required = false) String sort,
                                                            @RequestParam(required = false) Boolean active) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("name", "id", "active", "sortIndex"), "sortIndex");
        Page<FrequencyType> p = frequencyTypeService.getAllFrequencyTypes(active, pageable);
        List<FrequencyTypeDto> content = p.getContent().stream().map(frequencyTypeMapper::toDto).toList();
        return new PageResponse<>(content, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @GetMapping("/{id}")
    public FrequencyTypeDto getFrequencyType(@PathVariable @NotNull UUID id) {
        return frequencyTypeMapper.toDto(frequencyTypeService.getFrequencyType(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
    public FrequencyTypeDto createFrequencyType(@RequestBody @NotNull FrequencyTypeDto frequencyType) {
        return frequencyTypeMapper.toDto(frequencyTypeService.createFrequencyType(frequencyTypeMapper.toEntity(frequencyType)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
    public FrequencyTypeDto updateFrequencyType(@PathVariable @NotNull UUID id, @RequestBody @NotNull FrequencyTypeDto frequencyType) {
        return frequencyTypeMapper.toDto(frequencyTypeService.updateFrequencyType(id, frequencyTypeMapper.toEntity(frequencyType)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
    public void deleteFrequencyType(@PathVariable @NotNull UUID id) {
        frequencyTypeService.deleteFrequencyType(id);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public void activateFrequencyType(@PathVariable @NotNull UUID id) {
        frequencyTypeService.activateFrequencyType(id);
    }

    @PostMapping("/reorder")
    @PreAuthorize("hasRole('ADMIN')")
    public void reorderFrequencyTypes(@RequestBody @NotNull List<UUID> ids) {
        frequencyTypeService.reorderFrequencyTypes(ids);
    }
}
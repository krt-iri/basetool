package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.RefiningMethod;
import de.greluc.krt.iri.basetool.backend.model.dto.RefiningMethodDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.mapper.RefiningMethodMapper;
import de.greluc.krt.iri.basetool.backend.service.RefiningMethodService;
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
@RequestMapping("/api/v1/refining-methods")
@RequiredArgsConstructor
@Transactional
public class RefiningMethodController {

    private final RefiningMethodService refiningMethodService;
    private final RefiningMethodMapper refiningMethodMapper;

    @GetMapping
    public PageResponse<RefiningMethodDto> getAllRefiningMethods(@RequestParam(required = false) Integer page,
                                                              @RequestParam(required = false) Integer size,
                                                              @RequestParam(required = false) String sort) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("name", "id"), "name");
        Page<RefiningMethod> p = refiningMethodService.getAllRefiningMethods(pageable);
        List<RefiningMethodDto> content = p.getContent().stream().map(refiningMethodMapper::toDto).toList();
        return new PageResponse<>(content, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @GetMapping("/{id}")
    public RefiningMethodDto getRefiningMethod(@PathVariable @NotNull UUID id) {
        return refiningMethodMapper.toDto(refiningMethodService.getRefiningMethod(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
    public RefiningMethodDto createRefiningMethod(@RequestBody @NotNull RefiningMethodDto refiningMethod) {
        return refiningMethodMapper.toDto(refiningMethodService.createRefiningMethod(refiningMethodMapper.toEntity(refiningMethod)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
    public RefiningMethodDto updateRefiningMethod(@PathVariable @NotNull UUID id, @RequestBody @NotNull RefiningMethodDto refiningMethod) {
        return refiningMethodMapper.toDto(refiningMethodService.updateRefiningMethod(id, refiningMethodMapper.toEntity(refiningMethod)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
    public void deleteRefiningMethod(@PathVariable @NotNull UUID id) {
        refiningMethodService.deleteRefiningMethod(id);
    }
}

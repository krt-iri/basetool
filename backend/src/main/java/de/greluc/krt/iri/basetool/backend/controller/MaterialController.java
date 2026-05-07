package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialMatrixItemDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialPriceOverviewDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialPriceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.TerminalDto;
import de.greluc.krt.iri.basetool.backend.mapper.TerminalMapper;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.mapper.MaterialMapper;
import de.greluc.krt.iri.basetool.backend.service.MaterialService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping("/api/v1/materials")
@RequiredArgsConstructor
@Transactional
public class MaterialController {

    private final MaterialService materialService;
    private final MaterialMapper materialMapper;
    private final TerminalMapper terminalMapper;

    @GetMapping
    public PageResponse<MaterialDto> getAllMaterials(@RequestParam(required = false, defaultValue = "false") Boolean hasTerminals,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size,
                                                  @RequestParam(required = false) String sort) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("name", "type", "id"), "name");
        Page<Material> p = Boolean.TRUE.equals(hasTerminals) ? materialService.getAllMaterialsWithPrices(pageable) : materialService.getAllMaterials(pageable);
        List<MaterialDto> content = p.getContent().stream().map(materialMapper::toDto).toList();
        return new PageResponse<>(content, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @Operation(summary = "Get all job-order materials", description = "Returns all materials marked as isJobOrder=true, sorted by name.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of job-order materials returned successfully")
    })
    @GetMapping("/job-order")
    public List<MaterialDto> getJobOrderMaterials() {
        return materialService.getAllJobOrderMaterials()
            .stream().map(materialMapper::toDto).toList();
    }

    @GetMapping("/lookup")
    public List<de.greluc.krt.iri.basetool.backend.model.dto.MaterialReferenceDto> lookupMaterials() {
        return materialService.findAllReference();
    }

    @GetMapping("/prices-overview")
    public PageResponse<MaterialPriceOverviewDto> getMaterialPriceOverview(@RequestParam(required = false) String name,
                                                                           @RequestParam(required = false) Integer page,
                                                                           @RequestParam(required = false) Integer size,
                                                                           @RequestParam(required = false) String sort) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("name", "id", "minPriceBuy", "maxPriceSell"), "name");
        Page<MaterialPriceOverviewDto> p = materialService.getMaterialPriceOverview(name, pageable);
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @GetMapping("/matrix")
    public PageResponse<MaterialMatrixItemDto> getMaterialMatrixItems(@RequestParam(required = false) Integer page,
                                                                      @RequestParam(required = false) Integer size,
                                                                      @RequestParam(required = false) String sort) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("material.name", "terminal.name", "id"), "material.name");
        Page<MaterialMatrixItemDto> p = materialService.getAllMatrixItems(pageable);
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @GetMapping("/{id}")
    public MaterialDto getMaterial(@PathVariable @NotNull UUID id) {
        return materialMapper.toDto(materialService.getMaterial(id));
    }

    @GetMapping("/{id}/prices")
    public PageResponse<MaterialPriceDto> getMaterialPrices(@PathVariable @NotNull UUID id,
                                                            @RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size,
                                                            @RequestParam(required = false) String sort) {
        Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, Set.of("terminal.name", "priceBuy", "priceSell", "id"), "terminal.name");
        Page<MaterialPriceDto> p = materialService.getMaterialPrices(id, pageable);
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), PaginationUtil.toSortStrings(p.getSort()));
    }

    @GetMapping("/{id}/terminals")
    public List<de.greluc.krt.iri.basetool.backend.model.dto.MaterialSellingTerminalDto> getMaterialTerminals(@PathVariable @NotNull UUID id) {
        return materialService.getMaterialTerminals(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public MaterialDto createMaterial(@RequestBody @NotNull MaterialDto material) {
        return materialMapper.toDto(materialService.createMaterial(materialMapper.toEntity(material)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public MaterialDto updateMaterial(@PathVariable @NotNull UUID id, @RequestBody @NotNull MaterialDto material) {
        return materialMapper.toDto(materialService.updateMaterial(id, materialMapper.toEntity(material)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public void deleteMaterial(@PathVariable @NotNull UUID id) {
        materialService.deleteMaterial(id);
    }
}

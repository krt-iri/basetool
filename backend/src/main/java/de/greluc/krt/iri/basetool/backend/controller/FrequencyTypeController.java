package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.FrequencyTypeMapper;
import de.greluc.krt.iri.basetool.backend.model.FrequencyType;
import de.greluc.krt.iri.basetool.backend.model.dto.FrequencyTypeDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.service.FrequencyTypeService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * REST surface for the frequency-type reference table. Supports drag-and-drop reorder via the
 * dedicated {@code /reorder} endpoint; mutations are OFFICER/ADMIN.
 */
@RestController
@RequestMapping("/api/v1/frequency-types")
@RequiredArgsConstructor
@Transactional
public class FrequencyTypeController {

  private final FrequencyTypeService frequencyTypeService;
  private final FrequencyTypeMapper frequencyTypeMapper;

  /**
   * Paged list with optional {@code active} filter. Default sort is {@code sortIndex} so the UI
   * dropdown reflects the admin-curated order.
   *
   * @return paged frequency-type DTOs
   */
  @GetMapping
  public PageResponse<FrequencyTypeDto> getAllFrequencyTypes(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) Boolean active) {
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page, size, sort, Set.of("name", "id", "active", "sortIndex"), "sortIndex");
    Page<FrequencyType> p = frequencyTypeService.getAllFrequencyTypes(active, pageable);
    List<FrequencyTypeDto> content =
        p.getContent().stream().map(frequencyTypeMapper::toDto).toList();
    return new PageResponse<>(
        content,
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  /**
   * @param id frequency type id
   * @return the DTO
   */
  @GetMapping("/{id}")
  public FrequencyTypeDto getFrequencyType(@PathVariable @NotNull UUID id) {
    return frequencyTypeMapper.toDto(frequencyTypeService.getFrequencyType(id));
  }

  /**
   * Creates a frequency type; the service assigns the next sort index.
   *
   * @param frequencyType create payload
   * @return the persisted DTO
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
  public FrequencyTypeDto createFrequencyType(
      @RequestBody @NotNull FrequencyTypeDto frequencyType) {
    return frequencyTypeMapper.toDto(
        frequencyTypeService.createFrequencyType(frequencyTypeMapper.toEntity(frequencyType)));
  }

  /**
   * Updates a frequency type. Sort index is preserved — use {@link #reorderFrequencyTypes} to
   * change the order.
   *
   * @param id frequency type id
   * @param frequencyType update payload
   * @return the persisted DTO
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
  public FrequencyTypeDto updateFrequencyType(
      @PathVariable @NotNull UUID id, @RequestBody @NotNull FrequencyTypeDto frequencyType) {
    return frequencyTypeMapper.toDto(
        frequencyTypeService.updateFrequencyType(id, frequencyTypeMapper.toEntity(frequencyType)));
  }

  /**
   * Soft-deletes a frequency type.
   *
   * @param id frequency type id
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
  public void deleteFrequencyType(@PathVariable @NotNull UUID id) {
    frequencyTypeService.deleteFrequencyType(id);
  }

  /**
   * Reverses a soft-delete. ADMIN-only.
   *
   * @param id frequency type id
   */
  @PostMapping("/{id}/activate")
  @PreAuthorize("hasRole('ADMIN')")
  public void activateFrequencyType(@PathVariable @NotNull UUID id) {
    frequencyTypeService.activateFrequencyType(id);
  }

  /**
   * Drag-and-drop reorder. Position of each id in the supplied list becomes the row's new sort
   * index. ADMIN-only.
   *
   * @param ids ids in the desired new order
   */
  @PostMapping("/reorder")
  @PreAuthorize("hasRole('ADMIN')")
  public void reorderFrequencyTypes(@RequestBody @NotNull List<UUID> ids) {
    frequencyTypeService.reorderFrequencyTypes(ids);
  }
}

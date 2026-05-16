package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.PoiMapper;
import de.greluc.krt.iri.basetool.backend.model.Poi;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PoiDto;
import de.greluc.krt.iri.basetool.backend.service.PoiService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-mostly REST surface over the POI catalogue. UEX owns the table content; the override
 * endpoints let admins/officers pin {@code hasLoadingDock} so the next UEX sweep cannot reset a
 * manual correction.
 */
@RestController
@RequestMapping("/api/v1/pois")
@RequiredArgsConstructor
@Transactional
@PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
public class PoiController {

  private final PoiService poiService;
  private final PoiMapper poiMapper;

  /**
   * Paged POI list with whitelist-enforced sort.
   *
   * @param page zero-based page index (optional)
   * @param size page size (optional)
   * @param sort sort spec, restricted to the whitelist
   * @return paged POI DTOs
   */
  @GetMapping
  public PageResponse<PoiDto> getAllPois(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page, size, sort, Set.of("name", "id", "starSystemName"), "name");
    Page<Poi> p = poiService.getAllPois(pageable);
    List<PoiDto> content = p.getContent().stream().map(poiMapper::toDto).toList();
    return new PageResponse<>(
        content,
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  /**
   * Returns a single POI DTO.
   *
   * @param id POI id
   * @return the POI DTO
   */
  @GetMapping("/{id}")
  public PoiDto getPoi(@PathVariable @NotNull UUID id) {
    return poiMapper.toDto(poiService.getPoi(id));
  }

  /**
   * Pins {@code hasLoadingDock} to {@code value} and marks it as admin-overridden so the next UEX
   * sweep skips writing the column.
   *
   * @param id POI id
   * @param value desired {@code hasLoadingDock} value
   * @return the persisted POI DTO
   */
  @PatchMapping("/{id}/loading-dock")
  @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
  public PoiDto setLoadingDockOverride(
      @PathVariable @NotNull UUID id, @RequestParam boolean value) {
    return poiMapper.toDto(poiService.setLoadingDockOverride(id, value));
  }

  /**
   * Clears the admin pin on the POI's {@code hasLoadingDock} flag. The value column keeps its
   * current state until the next UEX sweep restores the upstream value.
   *
   * @param id POI id
   * @return the persisted POI DTO
   */
  @DeleteMapping("/{id}/loading-dock-override")
  @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
  public PoiDto clearLoadingDockOverride(@PathVariable @NotNull UUID id) {
    return poiMapper.toDto(poiService.clearLoadingDockOverride(id));
  }
}

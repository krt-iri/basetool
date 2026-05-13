package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.StarSystemMapper;
import de.greluc.krt.iri.basetool.backend.model.StarSystem;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.StarSystemDto;
import de.greluc.krt.iri.basetool.backend.service.StarSystemService;
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

@RestController
@RequestMapping("/api/v1/star-systems")
@RequiredArgsConstructor
@Transactional
public class StarSystemController {

  private final StarSystemService starSystemService;
  private final StarSystemMapper starSystemMapper;

  @GetMapping
  public PageResponse<StarSystemDto> getAllStarSystems(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    Pageable pageable =
        PaginationUtil.createPageRequest(page, size, sort, Set.of("name", "id"), "name");
    Page<StarSystem> p = starSystemService.getAllStarSystems(pageable);
    List<StarSystemDto> content = p.getContent().stream().map(starSystemMapper::toDto).toList();
    return new PageResponse<>(
        content,
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  @GetMapping("/{id}")
  public StarSystemDto getStarSystem(@PathVariable @NotNull UUID id) {
    return starSystemMapper.toDto(starSystemService.getStarSystem(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
  public StarSystemDto createStarSystem(@RequestBody @NotNull StarSystemDto starSystem) {
    return starSystemMapper.toDto(
        starSystemService.createStarSystem(starSystemMapper.toEntity(starSystem)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
  public StarSystemDto updateStarSystem(
      @PathVariable @NotNull UUID id, @RequestBody @NotNull StarSystemDto starSystem) {
    return starSystemMapper.toDto(
        starSystemService.updateStarSystem(id, starSystemMapper.toEntity(starSystem)));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
  public void deleteStarSystem(@PathVariable @NotNull UUID id) {
    starSystemService.deleteStarSystem(id);
  }
}

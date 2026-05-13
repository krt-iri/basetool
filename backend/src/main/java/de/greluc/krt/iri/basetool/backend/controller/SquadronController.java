package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.SquadronMapper;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronDto;
import de.greluc.krt.iri.basetool.backend.service.SquadronService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/squadrons")
@RequiredArgsConstructor
@Transactional
public class SquadronController {

  private static final Set<String> ALLOWED_SORT = Set.of("name", "shorthand", "id");

  private final SquadronService squadronService;
  private final SquadronMapper squadronMapper;

  @GetMapping
  public PageResponse<SquadronDto> getAllSquadrons(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
    Pageable pageable = PaginationUtil.createPageRequest(page, size, sort, ALLOWED_SORT, "name");
    Page<Squadron> p = squadronService.getAllSquadrons(pageable, includeInactive);
    List<SquadronDto> content = p.getContent().stream().map(squadronMapper::toDto).toList();
    return new PageResponse<>(
        content,
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
  public SquadronDto createSquadron(@RequestBody @Valid SquadronDto squadron) {
    return squadronMapper.toDto(squadronService.createSquadron(squadronMapper.toEntity(squadron)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
  public SquadronDto updateSquadron(
      @PathVariable @NotNull UUID id, @RequestBody @Valid SquadronDto squadron) {
    return squadronMapper.toDto(squadronService.updateSquadron(id, squadron));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
  public void deleteSquadron(@PathVariable @NotNull UUID id) {
    squadronService.deleteSquadron(id);
  }

  @PostMapping("/{id}/activate")
  @PreAuthorize("hasRole('ADMIN')")
  public void activateSquadron(@PathVariable @NotNull UUID id) {
    squadronService.activateSquadron(id);
  }
}

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the squadron reference table. Mutations are OFFICER/ADMIN; activate is
 * ADMIN-only.
 */
@RestController
@RequestMapping("/api/v1/squadrons")
@RequiredArgsConstructor
@Transactional
public class SquadronController {

  private static final Set<String> ALLOWED_SORT = Set.of("name", "shorthand", "id");

  private final SquadronService squadronService;
  private final SquadronMapper squadronMapper;

  /**
   * Paged list with {@code includeInactive} for the admin view.
   *
   * @return paged squadron DTOs
   */
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

  /**
   * Creates a new squadron. Duplicate name → 409.
   *
   * @param squadron create payload
   * @return the persisted DTO
   */
  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public SquadronDto createSquadron(@RequestBody @Valid SquadronDto squadron) {
    return squadronMapper.toDto(squadronService.createSquadron(squadronMapper.toEntity(squadron)));
  }

  /**
   * Updates a squadron. Carries optimistic-lock version in the DTO body.
   *
   * @param id squadron id
   * @param squadron update payload
   * @return the persisted DTO
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public SquadronDto updateSquadron(
      @PathVariable @NotNull UUID id, @RequestBody @Valid SquadronDto squadron) {
    return squadronMapper.toDto(squadronService.updateSquadron(id, squadron));
  }

  /**
   * Soft-deletes a squadron.
   *
   * @param id squadron id
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public void deleteSquadron(@PathVariable @NotNull UUID id) {
    squadronService.deleteSquadron(id);
  }

  /**
   * Reverses a soft-delete. ADMIN-only.
   *
   * @param id squadron id
   */
  @PostMapping("/{id}/activate")
  @PreAuthorize("hasRole('ADMIN')")
  public void activateSquadron(@PathVariable @NotNull UUID id) {
    squadronService.activateSquadron(id);
  }

  /**
   * Per-squadron promotion-system feature toggle. Admins flip the flag to hide / re-expose the
   * promotion menu for a whole squadron without losing any data. ADMIN-only — the regular {@code
   * PUT /api/v1/squadrons/{id}} update path intentionally does NOT touch this flag so an accidental
   * description edit cannot disable the feature.
   *
   * @param id squadron id
   * @param body request payload {@code { "enabled": true|false }}
   * @return the updated squadron DTO with the new flag value
   */
  @PatchMapping("/{id}/promotion-enabled")
  @PreAuthorize("hasRole('ADMIN')")
  public SquadronDto setPromotionEnabled(
      @PathVariable @NotNull UUID id, @RequestBody @Valid SquadronPromotionToggleRequest body) {
    return squadronMapper.toDto(squadronService.setPromotionEnabled(id, body.enabled()));
  }

  /**
   * Request body for the per-squadron promotion-feature toggle endpoint. Carries a single boolean
   * so a future expansion (e.g. effective-from date) can be added without breaking the wire format.
   *
   * @param enabled new value of {@code Squadron.isPromotionEnabled}
   */
  public record SquadronPromotionToggleRequest(boolean enabled) {}
}

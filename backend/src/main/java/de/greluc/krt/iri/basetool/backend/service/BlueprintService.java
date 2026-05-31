package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.mapper.BlueprintMapper;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintDto;
import de.greluc.krt.iri.basetool.backend.model.scwiki.Blueprint;
import de.greluc.krt.iri.basetool.backend.repository.BlueprintRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read service for the admin blueprint page. Returns a paged, filtered view of the synced crafting
 * blueprints with their requirement-group stat graph. Blueprints are global SC Wiki reference data
 * (not org-unit-scoped), so this service deliberately does not wire {@code OwnerScopeService} —
 * visibility is the admin-only gate on the controller.
 *
 * <p>Mapping to DTOs happens inside this {@code readOnly} transaction so the lazy owned collections
 * (groups, modifiers, ingredients, summary, dismantle returns) initialize while the session is
 * open; their {@code @BatchSize} keeps the per-page load off the N+1 path.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlueprintService {

  private final BlueprintRepository blueprintRepository;
  private final BlueprintMapper blueprintMapper;

  /**
   * Returns one page of active (non-soft-deleted) blueprints, optionally filtered by a
   * case-insensitive substring of the output-item name or Wiki key, mapped to DTOs.
   *
   * @param search output-name / key substring; blank or {@code null} returns all active blueprints
   * @param pageable page request with a whitelisted sort
   * @return a page of blueprint DTOs
   */
  public Page<BlueprintDto> getBlueprints(@Nullable String search, @NotNull Pageable pageable) {
    boolean hasSearch = search != null && !search.isBlank();
    Page<Blueprint> page =
        hasSearch
            ? blueprintRepository.searchActive(search.trim(), pageable)
            : blueprintRepository.findByScwikiDeletedAtIsNull(pageable);
    return page.map(blueprintMapper::toDto);
  }
}

package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.dto.uex.UexCategoryDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.UexCategory;
import de.greluc.krt.iri.basetool.backend.repository.UexCategoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Syncs UEX Corp's {@code /categories} endpoint into the local {@code uex_category} reference
 * table.
 *
 * <p>The 98+ rows of UEX categories drive {@link UexItemSyncService}'s walk through {@code
 * /items?id_category=<n>}; this service is its prerequisite and runs once per UEX scheduler tick
 * before the item sync. Idempotent: matching is by UEX integer id (PK), so a re-run on an unchanged
 * catalogue is a no-op series of {@code SELECT}s followed by no-op {@code UPDATE}s. An empty UEX
 * response short-circuits without wiping local data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UexCategoryRefService {

  private final UexClient uexClient;
  private final UexCategoryRepository repository;

  /**
   * Pulls the category catalogue and upserts each row. Returns the persisted list so {@link
   * UexItemSyncService} can iterate without re-querying the DB.
   *
   * @return all categories after this sync run (game-related and otherwise)
   */
  @Transactional
  public List<UexCategory> syncCategories() {
    log.info("Starting synchronization of UEX categories...");
    List<UexCategoryDto> dtos = uexClient.getCategories();
    if (dtos.isEmpty()) {
      log.warn("No categories received from UEX API. Aborting synchronization.");
      return repository.findAll();
    }

    Instant now = Instant.now();
    int added = 0;
    int updated = 0;
    for (UexCategoryDto dto : dtos) {
      if (dto.id() == null || dto.section() == null || dto.name() == null) {
        log.debug("Skipping category with missing id/section/name: {}", dto);
        continue;
      }
      Optional<UexCategory> existingOpt = repository.findById(dto.id());
      UexCategory category = existingOpt.orElseGet(UexCategory::new);
      boolean isNew = existingOpt.isEmpty();
      if (isNew) {
        category.setId(dto.id());
      }
      category.setType(dto.type() == null ? "item" : dto.type());
      category.setSection(dto.section());
      category.setName(dto.name());
      category.setIsGameRelated(asBoolean(dto.isGameRelated()));
      category.setIsMining(asBoolean(dto.isMining()));
      category.setUexSyncedAt(now);
      category.setUexDeletedAt(null);
      repository.save(category);
      if (isNew) {
        added++;
      } else {
        updated++;
      }
    }

    log.info("Finished UEX category sync: {} added, {} updated", added, updated);
    return repository.findAll();
  }

  /**
   * Normalises UEX's integer 0/1 flag into a Java {@link Boolean}. A {@code null} input maps to
   * {@code false} — categories without a flag value are treated as "not set".
   *
   * @param flag UEX-style 0/1 integer
   * @return {@code true} iff {@code flag} equals 1
   */
  private static Boolean asBoolean(Integer flag) {
    return flag != null && flag == 1;
  }
}

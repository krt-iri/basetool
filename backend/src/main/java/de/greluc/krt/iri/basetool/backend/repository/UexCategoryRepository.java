package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.UexCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link UexCategory}. The PK is the UEX integer id (1..98+),
 * deterministic across runs.
 */
@Repository
public interface UexCategoryRepository extends JpaRepository<UexCategory, Integer> {

  /**
   * Lists categories whose {@code is_game_related} flag is set — the inner-loop input for the R2
   * {@code UexItemSyncService}. Sorted by id so the {@code /items?id_category=<n>} walk is
   * deterministic across runs.
   *
   * @return game-related categories sorted by integer id
   */
  List<UexCategory> findAllByIsGameRelatedTrueOrderByIdAsc();
}

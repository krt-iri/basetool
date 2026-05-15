package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.PromotionLevel;
import de.greluc.krt.iri.basetool.backend.model.PromotionLevelContent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link PromotionLevelContent} entries, providing category-scoped
 * lookups used when rendering the rank-level expectation table.
 */
@Repository
public interface PromotionLevelContentRepository
    extends JpaRepository<PromotionLevelContent, UUID> {

  /**
   * Returns every {@link PromotionLevelContent} attached to the given category ordered by {@link
   * PromotionLevel}, so the rank-progression view can be rendered without re-sorting.
   *
   * @param categoryId identifier of the parent {@link
   *     de.greluc.krt.iri.basetool.backend.model.PromotionCategory}
   * @return the category's level contents ordered by ascending {@link PromotionLevel}
   */
  List<PromotionLevelContent> findAllByCategoryIdOrderByLevel(UUID categoryId);

  /**
   * Looks up the single {@link PromotionLevelContent} entry that describes the expectations of the
   * given category at the given level.
   *
   * @param categoryId identifier of the parent category
   * @param level the {@link PromotionLevel} to resolve
   * @return the matching entry, or {@link Optional#empty()} if none exists
   */
  Optional<PromotionLevelContent> findByCategoryIdAndLevel(UUID categoryId, PromotionLevel level);
}

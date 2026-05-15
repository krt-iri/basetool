package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.PromotionCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link PromotionCategory} aggregates, providing topic-scoped
 * lookups used by the promotion UI and the eligibility engine.
 */
@Repository
public interface PromotionCategoryRepository extends JpaRepository<PromotionCategory, UUID> {

  /**
   * Returns every {@link PromotionCategory} attached to the given topic, ordered by {@code
   * sortOrder} ascending so the evaluation table can be rendered without re-sorting.
   *
   * @param topicId identifier of the parent {@link
   *     de.greluc.krt.iri.basetool.backend.model.PromotionTopic}
   * @return the topic's categories in display order
   */
  List<PromotionCategory> findAllByTopicIdOrderBySortOrderAsc(UUID topicId);

  /**
   * Returns a paginated slice of the {@link PromotionCategory} entries that belong to the given
   * topic.
   *
   * @param topicId identifier of the parent topic
   * @param pageable Spring Data paging and sorting parameters
   * @return a page of categories scoped to the topic
   */
  Page<PromotionCategory> findAllByTopicId(UUID topicId, Pageable pageable);
}

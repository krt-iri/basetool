package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.PromotionTopic;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link PromotionTopic} aggregates, the top-level grouping of the
 * promotion catalog.
 */
@Repository
public interface PromotionTopicRepository extends JpaRepository<PromotionTopic, UUID> {

  /**
   * Returns every {@link PromotionTopic} ordered by {@code sortOrder} ascending so the
   * promotion-system overview can be rendered without re-sorting.
   *
   * @return all promotion topics in display order
   */
  List<PromotionTopic> findAllByOrderBySortOrderAsc();
}

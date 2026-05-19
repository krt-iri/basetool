package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.PromotionTopic;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

  /**
   * Squadron-scoped unpaginated read for the promotion-system overview. When {@code
   * owningSquadronId} is {@code null} the result spans every squadron (admin "all squadrons" mode);
   * a non-null id restricts the result to a single squadron's topics. The {@code sortOrder ASC}
   * default matches {@link #findAllByOrderBySortOrderAsc()}.
   */
  @Query(
      "SELECT t FROM PromotionTopic t WHERE :owningSquadronId IS NULL OR t.owningSquadron.id ="
          + " :owningSquadronId ORDER BY t.sortOrder ASC")
  List<PromotionTopic> findAllScoped(@Param("owningSquadronId") UUID owningSquadronId);

  /**
   * Squadron-scoped paged read. Same {@code null}-means-no-filter contract as {@link
   * #findAllScoped(UUID)}; sort is delegated to {@link Pageable} so the caller can override.
   */
  @Query(
      "SELECT t FROM PromotionTopic t WHERE :owningSquadronId IS NULL OR t.owningSquadron.id ="
          + " :owningSquadronId")
  Page<PromotionTopic> findAllScoped(
      @Param("owningSquadronId") UUID owningSquadronId, Pageable pageable);
}

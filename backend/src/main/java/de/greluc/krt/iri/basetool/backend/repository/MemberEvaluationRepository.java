package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MemberEvaluation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link MemberEvaluation}. In addition to the standard CRUD finders it
 * exposes a JOIN-FETCH variant the eligibility service uses to evaluate a member's grades against a
 * {@code RankRequirement} without firing lazy-load queries per row.
 */
@Repository
public interface MemberEvaluationRepository extends JpaRepository<MemberEvaluation, UUID> {

  /**
   * Returns every evaluation belonging to the given JWT-sub, without eager joins. Used for the
   * user-facing "my evaluations" list where category / topic data is rendered separately.
   *
   * @param userId the JWT-sub identifier of the member
   * @return every evaluation row for that member, possibly empty
   */
  List<MemberEvaluation> findAllByUserId(String userId);

  /**
   * Paginated variant of {@link #findAllByUserId(String)} for the personal view.
   *
   * @param userId the JWT-sub identifier of the member
   * @param pageable Spring Data pagination/sort instructions
   * @return a page of evaluations for the member
   */
  Page<MemberEvaluation> findAllByUserId(String userId, Pageable pageable);

  /**
   * Looks up a single evaluation by its primary key components.
   *
   * @param userId the JWT-sub identifier of the member
   * @param categoryId the category the evaluation refers to
   * @return the evaluation if one exists for the pair, otherwise empty
   */
  Optional<MemberEvaluation> findByUserIdAndCategoryId(String userId, UUID categoryId);

  /**
   * Existence probe used by the upsert flow to decide between insert and update without loading the
   * row.
   *
   * @param userId the JWT-sub identifier of the member
   * @param categoryId the category the potential evaluation would refer to
   * @return {@code true} iff a row exists for the pair
   */
  boolean existsByUserIdAndCategoryId(String userId, UUID categoryId);

  /**
   * Returns every evaluation for a user with the parent {@code PromotionCategory} and its {@code
   * PromotionTopic} eagerly fetched. The eligibility evaluator uses this method to avoid N+1 lazy
   * loads when grouping evaluations by topic.
   *
   * @param userId the JWT-sub identifier of the member
   * @return evaluations with category+topic already populated, possibly empty
   */
  @Query(
      "SELECT e FROM MemberEvaluation e "
          + "JOIN FETCH e.category c "
          + "JOIN FETCH c.topic "
          + "WHERE e.userId = :userId")
  List<MemberEvaluation> findAllByUserIdWithCategoryAndTopic(String userId);
}

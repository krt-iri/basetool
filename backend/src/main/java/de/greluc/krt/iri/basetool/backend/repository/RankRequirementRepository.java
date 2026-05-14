package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.RankRequirement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link RankRequirement}. The custom finders below are tuned for the
 * eligibility evaluator, which needs every requirement that applies to a specific {@code (fromRank,
 * toRank)} transition and the distinct set of configured transitions.
 */
@Repository
public interface RankRequirementRepository extends JpaRepository<RankRequirement, UUID> {

  /**
   * Returns every requirement configured for a specific rank transition, ordered by id so the
   * eligibility result is deterministic across calls. Used by the eligibility service when
   * evaluating one transition.
   *
   * @param fromRank the rank the member currently holds
   * @param toRank the rank the member would be promoted to
   * @return all matching requirements, possibly empty
   */
  List<RankRequirement> findAllByFromRankAndToRankOrderByIdAsc(int fromRank, int toRank);

  /**
   * Paginated variant of {@link #findAllByFromRankAndToRankOrderByIdAsc(int, int)} for admin REST
   * endpoints.
   *
   * @param fromRank the rank the member currently holds
   * @param toRank the rank the member would be promoted to
   * @param pageable Spring Data pagination/sort instructions
   * @return a page of matching requirements
   */
  Page<RankRequirement> findAllByFromRankAndToRank(int fromRank, int toRank, Pageable pageable);

  /**
   * Returns the distinct list of {@code (fromRank, toRank)} pairs that have at least one
   * requirement configured, ordered by {@code fromRank} descending so the most senior transitions
   * appear first. Used by the eligibility service to evaluate every defined transition for one
   * member.
   *
   * @return one row per configured transition, each carrying {@code [fromRank, toRank]}
   */
  @Query(
      "SELECT DISTINCT r.fromRank, r.toRank FROM RankRequirement r ORDER BY r.fromRank DESC,"
          + " r.toRank DESC")
  List<Object[]> findDistinctRankTransitions();

  /**
   * JOIN-FETCH variant of {@link #findAllByFromRankAndToRankOrderByIdAsc(int, int)} used by the
   * eligibility evaluator so {@code topic}, {@code category} and {@code category.topic} are
   * hydrated up front and the per-requirement loop does not trigger lazy loads.
   *
   * <p>Two left joins on {@code topic} and {@code category} are required because each requirement
   * has at most one of them populated.
   *
   * @param fromRank the rank the member currently holds
   * @param toRank the rank the member would be promoted to
   * @return matching requirements with topic+category eagerly fetched
   */
  @Query(
      "SELECT r FROM RankRequirement r "
          + "LEFT JOIN FETCH r.topic "
          + "LEFT JOIN FETCH r.category c "
          + "LEFT JOIN FETCH c.topic "
          + "WHERE r.fromRank = :fromRank AND r.toRank = :toRank "
          + "ORDER BY r.id ASC")
  List<RankRequirement> findAllForRankTransitionWithRelations(int fromRank, int toRank);
}

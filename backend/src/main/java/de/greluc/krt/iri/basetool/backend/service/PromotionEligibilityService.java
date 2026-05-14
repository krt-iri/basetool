package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.MemberEvaluation;
import de.greluc.krt.iri.basetool.backend.model.PromotionCategory;
import de.greluc.krt.iri.basetool.backend.model.PromotionLevel;
import de.greluc.krt.iri.basetool.backend.model.RankRequirement;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionEligibilityResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionRequirementCheckResponse;
import de.greluc.krt.iri.basetool.backend.repository.MemberEvaluationRepository;
import de.greluc.krt.iri.basetool.backend.repository.RankRequirementRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates whether a member fulfils the promotion requirements for one or all configured rank
 * transitions.
 *
 * <p>Each {@code RankRequirement} is either:
 *
 * <ul>
 *   <li><strong>category-scoped</strong> (a specific {@code PromotionCategory} must reach at least
 *       {@code minimumLevel}); or
 *   <li><strong>topic-scoped</strong> ({@code requiredCount} categories belonging to the same
 *       {@code PromotionTopic} must each reach at least {@code minimumLevel}); or
 *   <li><strong>global</strong> (neither topic nor category set – {@code requiredCount} categories
 *       anywhere must reach the level; used as a fallback).
 * </ul>
 *
 * <p>The rules from the squadron's promotion concept ("Beförderung IRI") combine freely – a
 * category-scoped check on "Anwesenheit = LEVEL_A" and a topic-scoped check on "Grundlagen requires
 * 2× LEVEL_A" both inspect the same {@code member_evaluation} rows, so a LEVEL_A in Anwesenheit
 * simultaneously satisfies both rules. The evaluator therefore does <em>not</em> consume
 * evaluations; it simply counts how many categories in scope already meet the minimum and marks
 * each rule satisfied independently.
 *
 * <p>Read-only by design: the service never persists state. Personal queries are filtered by JWT
 * sub at the call site; the {@code *ForUser} methods that take an arbitrary userId are gated by
 * {@code ADMIN} or {@code OFFICER}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PromotionEligibilityService {

  private final RankRequirementRepository rankRequirementRepository;
  private final MemberEvaluationRepository memberEvaluationRepository;

  /**
   * Evaluates the eligibility of the given user for one specific rank transition.
   *
   * <p>Returns a response with {@code eligible == false} and {@code hasConfiguredRules == false}
   * when no requirement is configured for the transition, so the UI can distinguish "missing
   * configuration" from "configured but not met".
   *
   * @param userId the JWT-sub identifier of the member being evaluated
   * @param fromRank the rank the member currently holds
   * @param toRank the rank the member would be promoted to
   * @return the per-rule outcome plus an aggregate {@code eligible} flag
   */
  public PromotionEligibilityResponse evaluateForRanks(
      @NotNull String userId, int fromRank, int toRank) {
    List<RankRequirement> requirements =
        rankRequirementRepository.findAllForRankTransitionWithRelations(fromRank, toRank);
    Map<UUID, PromotionLevel> levelByCategory = loadAssignedLevels(userId);
    Map<UUID, UUID> topicByCategory = loadCategoryToTopicIndex(userId);

    List<PromotionRequirementCheckResponse> checks = new ArrayList<>(requirements.size());
    boolean allSatisfied = true;

    for (RankRequirement req : requirements) {
      PromotionRequirementCheckResponse check =
          evaluateRequirement(req, levelByCategory, topicByCategory);
      checks.add(check);
      if (!check.satisfied()) {
        allSatisfied = false;
      }
    }

    boolean hasRules = !requirements.isEmpty();
    boolean eligible = hasRules && allSatisfied;
    return new PromotionEligibilityResponse(userId, fromRank, toRank, eligible, hasRules, checks);
  }

  /**
   * Evaluates the user against every {@code (fromRank, toRank)} transition that has at least one
   * configured requirement. The result is ordered the same way as {@link
   * RankRequirementRepository#findDistinctRankTransitions()} – senior transitions first – so the UI
   * can show the next reachable promotion at the top.
   *
   * @param userId the JWT-sub identifier of the member being evaluated
   * @return eligibility entries for every configured transition, possibly empty
   */
  public List<PromotionEligibilityResponse> evaluateAllForUser(@NotNull String userId) {
    List<Object[]> transitions = rankRequirementRepository.findDistinctRankTransitions();
    List<PromotionEligibilityResponse> result = new ArrayList<>(transitions.size());
    for (Object[] row : transitions) {
      int from = ((Number) row[0]).intValue();
      int to = ((Number) row[1]).intValue();
      result.add(evaluateForRanks(userId, from, to));
    }
    return result;
  }

  /**
   * Admin/officer view: same as {@link #evaluateAllForUser(String)} but explicitly callable for any
   * user id. Authorisation is enforced via {@code @PreAuthorize} so personal views keep using
   * {@link #evaluateAllForUser(String)} without a role check.
   *
   * @param userId the JWT-sub identifier of the member being evaluated
   * @return eligibility entries for every configured transition, possibly empty
   */
  @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
  public List<PromotionEligibilityResponse> evaluateAllForUserAsAdmin(@NotNull String userId) {
    return evaluateAllForUser(userId);
  }

  private Map<UUID, PromotionLevel> loadAssignedLevels(@NotNull String userId) {
    Map<UUID, PromotionLevel> levels = new HashMap<>();
    for (MemberEvaluation evaluation :
        memberEvaluationRepository.findAllByUserIdWithCategoryAndTopic(userId)) {
      if (evaluation.getAssignedLevel() != null && evaluation.getCategory() != null) {
        levels.put(evaluation.getCategory().getId(), evaluation.getAssignedLevel());
      }
    }
    return levels;
  }

  private Map<UUID, UUID> loadCategoryToTopicIndex(@NotNull String userId) {
    Map<UUID, UUID> map = new HashMap<>();
    for (MemberEvaluation evaluation :
        memberEvaluationRepository.findAllByUserIdWithCategoryAndTopic(userId)) {
      PromotionCategory category = evaluation.getCategory();
      if (category != null && category.getTopic() != null) {
        map.put(category.getId(), category.getTopic().getId());
      }
    }
    return map;
  }

  private PromotionRequirementCheckResponse evaluateRequirement(
      @NotNull RankRequirement req,
      @NotNull Map<UUID, PromotionLevel> levelByCategory,
      @NotNull Map<UUID, UUID> topicByCategory) {
    PromotionLevel minimum = req.getMinimumLevel();
    if (req.getCategory() != null) {
      return evaluateCategoryRequirement(req, minimum, levelByCategory);
    }
    if (req.getTopic() != null) {
      return evaluateTopicRequirement(req, minimum, levelByCategory, topicByCategory);
    }
    return evaluateGlobalRequirement(req, minimum, levelByCategory);
  }

  private PromotionRequirementCheckResponse evaluateCategoryRequirement(
      @NotNull RankRequirement req,
      @NotNull PromotionLevel minimum,
      @NotNull Map<UUID, PromotionLevel> levelByCategory) {
    PromotionCategory category = req.getCategory();
    PromotionLevel userLevel = levelByCategory.get(category.getId());
    boolean satisfied = userLevel != null && userLevel.isAtLeast(minimum);
    UUID topicId = category.getTopic() != null ? category.getTopic().getId() : null;
    String topicName = category.getTopic() != null ? category.getTopic().getName() : null;
    return new PromotionRequirementCheckResponse(
        req.getId(),
        topicId,
        topicName,
        category.getId(),
        category.getName(),
        minimum,
        1,
        satisfied ? 1 : 0,
        satisfied,
        req.getDescription());
  }

  private PromotionRequirementCheckResponse evaluateTopicRequirement(
      @NotNull RankRequirement req,
      @NotNull PromotionLevel minimum,
      @NotNull Map<UUID, PromotionLevel> levelByCategory,
      @NotNull Map<UUID, UUID> topicByCategory) {
    UUID topicId = req.getTopic().getId();
    int achieved = 0;
    for (Map.Entry<UUID, PromotionLevel> entry : levelByCategory.entrySet()) {
      UUID owningTopicId = topicByCategory.get(entry.getKey());
      if (owningTopicId != null
          && owningTopicId.equals(topicId)
          && entry.getValue().isAtLeast(minimum)) {
        achieved++;
      }
    }
    boolean satisfied = achieved >= req.getRequiredCount();
    return new PromotionRequirementCheckResponse(
        req.getId(),
        topicId,
        req.getTopic().getName(),
        null,
        null,
        minimum,
        req.getRequiredCount(),
        achieved,
        satisfied,
        req.getDescription());
  }

  private PromotionRequirementCheckResponse evaluateGlobalRequirement(
      @NotNull RankRequirement req,
      @NotNull PromotionLevel minimum,
      @NotNull Map<UUID, PromotionLevel> levelByCategory) {
    int achieved = 0;
    for (PromotionLevel level : levelByCategory.values()) {
      if (level.isAtLeast(minimum)) {
        achieved++;
      }
    }
    boolean satisfied = achieved >= req.getRequiredCount();
    return new PromotionRequirementCheckResponse(
        req.getId(),
        null,
        null,
        null,
        null,
        minimum,
        req.getRequiredCount(),
        achieved,
        satisfied,
        req.getDescription());
  }
}

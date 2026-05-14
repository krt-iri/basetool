package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.MemberEvaluation;
import de.greluc.krt.iri.basetool.backend.model.PromotionCategory;
import de.greluc.krt.iri.basetool.backend.model.PromotionLevel;
import de.greluc.krt.iri.basetool.backend.model.PromotionTopic;
import de.greluc.krt.iri.basetool.backend.model.RankRequirement;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionEligibilityResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionRequirementCheckResponse;
import de.greluc.krt.iri.basetool.backend.repository.MemberEvaluationRepository;
import de.greluc.krt.iri.basetool.backend.repository.RankRequirementRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link PromotionEligibilityService}. Covers the three requirement scopes
 * (category, topic, global), missing-level fallback and the "Anwesenheit-A counts towards 2A" rule
 * that motivated the design.
 */
@ExtendWith(MockitoExtension.class)
class PromotionEligibilityServiceTest {

  @Mock private RankRequirementRepository rankRequirementRepository;
  @Mock private MemberEvaluationRepository memberEvaluationRepository;

  @InjectMocks private PromotionEligibilityService service;

  private static final String USER = "user-A";

  @Test
  void evaluateForRanks_shouldReturnNotEligibleWithNoRules_whenNoRequirementsConfigured() {
    when(rankRequirementRepository.findAllForRankTransitionWithRelations(anyInt(), anyInt()))
        .thenReturn(List.of());
    when(memberEvaluationRepository.findAllByUserIdWithCategoryAndTopic(USER))
        .thenReturn(List.of());

    PromotionEligibilityResponse result = service.evaluateForRanks(USER, 20, 19);

    assertEquals(20, result.fromRank());
    assertEquals(19, result.toRank());
    assertFalse(result.eligible(), "no rules => not eligible");
    assertFalse(result.hasConfiguredRules(), "no rules => no rules flag");
    assertTrue(result.checks().isEmpty());
  }

  @Test
  void evaluateForRanks_shouldSatisfyCategoryRequirement_whenMemberHasMatchingLevel() {
    // Given – "Anwesenheit must be at least LEVEL_A" rule for 20 -> 19
    PromotionTopic grundlagen = topic("Grundlagen");
    PromotionCategory anwesenheit = category(grundlagen, "Anwesenheit");
    RankRequirement req = categoryRule(anwesenheit, PromotionLevel.LEVEL_A, "Anwesenheit A");

    when(rankRequirementRepository.findAllForRankTransitionWithRelations(20, 19))
        .thenReturn(List.of(req));
    when(memberEvaluationRepository.findAllByUserIdWithCategoryAndTopic(USER))
        .thenReturn(List.of(evaluation(USER, anwesenheit, PromotionLevel.LEVEL_A)));

    // When
    PromotionEligibilityResponse result = service.evaluateForRanks(USER, 20, 19);

    // Then
    assertTrue(result.eligible());
    assertTrue(result.hasConfiguredRules());
    assertEquals(1, result.checks().size());
    PromotionRequirementCheckResponse check = result.checks().get(0);
    assertTrue(check.satisfied());
    assertEquals(1, check.requiredCount());
    assertEquals(1, check.achievedCount());
    assertEquals(anwesenheit.getId(), check.categoryId());
  }

  @Test
  void evaluateForRanks_shouldFailCategoryRequirement_whenLevelTooLow() {
    PromotionTopic grundlagen = topic("Grundlagen");
    PromotionCategory anwesenheit = category(grundlagen, "Anwesenheit");
    // Demand LEVEL_B but member has LEVEL_A
    RankRequirement req = categoryRule(anwesenheit, PromotionLevel.LEVEL_B, "Anwesenheit B");

    when(rankRequirementRepository.findAllForRankTransitionWithRelations(19, 18))
        .thenReturn(List.of(req));
    when(memberEvaluationRepository.findAllByUserIdWithCategoryAndTopic(USER))
        .thenReturn(List.of(evaluation(USER, anwesenheit, PromotionLevel.LEVEL_A)));

    PromotionEligibilityResponse result = service.evaluateForRanks(USER, 19, 18);

    assertFalse(result.eligible());
    assertFalse(result.checks().get(0).satisfied());
    assertEquals(0, result.checks().get(0).achievedCount());
  }

  @Test
  void evaluateForRanks_shouldCountCategoriesInTopic_whenTopicScopedRequirement() {
    // Given – 2× LEVEL_A in topic "Grundlagen"
    PromotionTopic grundlagen = topic("Grundlagen");
    PromotionCategory flug = category(grundlagen, "Flug Kenntnisse");
    PromotionCategory schiff = category(grundlagen, "Schiffsbetrieb");
    PromotionCategory anwesenheit = category(grundlagen, "Anwesenheit");
    RankRequirement req = topicRule(grundlagen, PromotionLevel.LEVEL_A, 2, "2A in Grundlagen");

    when(rankRequirementRepository.findAllForRankTransitionWithRelations(20, 19))
        .thenReturn(List.of(req));
    when(memberEvaluationRepository.findAllByUserIdWithCategoryAndTopic(USER))
        .thenReturn(
            List.of(
                evaluation(USER, flug, PromotionLevel.LEVEL_A),
                evaluation(USER, schiff, PromotionLevel.LEVEL_B),
                evaluation(USER, anwesenheit, PromotionLevel.LEVEL_A)));

    PromotionEligibilityResponse result = service.evaluateForRanks(USER, 20, 19);

    // 3 of 3 categories reach at least LEVEL_A (B counts for A); requirement is 2 so satisfied.
    assertTrue(result.eligible());
    PromotionRequirementCheckResponse check = result.checks().get(0);
    assertEquals(3, check.achievedCount());
    assertEquals(2, check.requiredCount());
    assertEquals(grundlagen.getId(), check.topicId());
    assertNull(check.categoryId(), "topic-scoped check carries no category");
  }

  @Test
  void evaluateForRanks_shouldFailTopicRequirement_whenNotEnoughCategoriesReachLevel() {
    PromotionTopic grundlagen = topic("Grundlagen");
    PromotionCategory flug = category(grundlagen, "Flug Kenntnisse");
    // Want 3× LEVEL_B but only one category reaches it.
    RankRequirement req = topicRule(grundlagen, PromotionLevel.LEVEL_B, 3, "3B in Grundlagen");

    when(rankRequirementRepository.findAllForRankTransitionWithRelations(19, 18))
        .thenReturn(List.of(req));
    when(memberEvaluationRepository.findAllByUserIdWithCategoryAndTopic(USER))
        .thenReturn(List.of(evaluation(USER, flug, PromotionLevel.LEVEL_B)));

    PromotionEligibilityResponse result = service.evaluateForRanks(USER, 19, 18);

    assertFalse(result.eligible());
    assertEquals(1, result.checks().get(0).achievedCount());
    assertEquals(3, result.checks().get(0).requiredCount());
  }

  @Test
  void evaluateForRanks_shouldCountAnwesenheitAIntoTopicAggregate() {
    // The motivating scenario from the requirements:
    // "Grundlagen mindestens 2A und die darin befindliche Kategorie Anwesenheit muss
    // mindestens A sein – Anwesenheit-A zählt in die 2A hinein."
    PromotionTopic grundlagen = topic("Grundlagen");
    PromotionCategory flug = category(grundlagen, "Flug Kenntnisse");
    PromotionCategory anwesenheit = category(grundlagen, "Anwesenheit");

    RankRequirement topicRule =
        topicRule(grundlagen, PromotionLevel.LEVEL_A, 2, "2A in Grundlagen");
    RankRequirement categoryRule =
        categoryRule(anwesenheit, PromotionLevel.LEVEL_A, "Anwesenheit A");

    when(rankRequirementRepository.findAllForRankTransitionWithRelations(20, 19))
        .thenReturn(List.of(topicRule, categoryRule));
    // Member has exactly 2 A-level entries: one for Flug, one for Anwesenheit
    when(memberEvaluationRepository.findAllByUserIdWithCategoryAndTopic(USER))
        .thenReturn(
            List.of(
                evaluation(USER, flug, PromotionLevel.LEVEL_A),
                evaluation(USER, anwesenheit, PromotionLevel.LEVEL_A)));

    PromotionEligibilityResponse result = service.evaluateForRanks(USER, 20, 19);

    // Both rules must succeed – Anwesenheit-A satisfies the category rule AND counts
    // towards the topic-aggregate "2A in Grundlagen".
    assertTrue(result.eligible(), "Anwesenheit A counts towards the 2A topic aggregate");
    assertEquals(2, result.checks().size());
    for (PromotionRequirementCheckResponse check : result.checks()) {
      assertTrue(check.satisfied());
    }
  }

  @Test
  void evaluateForRanks_shouldFailWhenCategoryRuleMissesEvenIfTopicAggregateMet() {
    // The category rule binds Anwesenheit specifically. Two A's elsewhere don't help.
    PromotionTopic grundlagen = topic("Grundlagen");
    PromotionCategory flug = category(grundlagen, "Flug Kenntnisse");
    PromotionCategory schiff = category(grundlagen, "Schiffsbetrieb");
    PromotionCategory anwesenheit = category(grundlagen, "Anwesenheit");

    RankRequirement topicRule =
        topicRule(grundlagen, PromotionLevel.LEVEL_A, 2, "2A in Grundlagen");
    RankRequirement anwesenheitRule =
        categoryRule(anwesenheit, PromotionLevel.LEVEL_A, "Anwesenheit A");

    when(rankRequirementRepository.findAllForRankTransitionWithRelations(20, 19))
        .thenReturn(List.of(topicRule, anwesenheitRule));
    when(memberEvaluationRepository.findAllByUserIdWithCategoryAndTopic(USER))
        .thenReturn(
            List.of(
                evaluation(USER, flug, PromotionLevel.LEVEL_A),
                evaluation(USER, schiff, PromotionLevel.LEVEL_A)
                // Anwesenheit unset
                ));

    PromotionEligibilityResponse result = service.evaluateForRanks(USER, 20, 19);

    assertFalse(result.eligible(), "Anwesenheit rule fails even when 2A elsewhere is met");
    PromotionRequirementCheckResponse topicCheck =
        result.checks().stream().filter(c -> c.categoryId() == null).findFirst().orElseThrow();
    assertTrue(topicCheck.satisfied(), "topic aggregate still meets 2A");
    PromotionRequirementCheckResponse categoryCheck =
        result.checks().stream().filter(c -> c.categoryId() != null).findFirst().orElseThrow();
    assertFalse(categoryCheck.satisfied(), "category-specific rule fails – Anwesenheit unset");
  }

  @Test
  void evaluateForRanks_shouldRespectLevelOrdering_AisLowestCisHighest() {
    // A member with LEVEL_C should satisfy a "minimum LEVEL_A" rule (higher counts for lower).
    PromotionTopic grundlagen = topic("Grundlagen");
    PromotionCategory flug = category(grundlagen, "Flug Kenntnisse");
    RankRequirement req = categoryRule(flug, PromotionLevel.LEVEL_A, "Flug A");

    when(rankRequirementRepository.findAllForRankTransitionWithRelations(20, 19))
        .thenReturn(List.of(req));
    when(memberEvaluationRepository.findAllByUserIdWithCategoryAndTopic(USER))
        .thenReturn(List.of(evaluation(USER, flug, PromotionLevel.LEVEL_C)));

    PromotionEligibilityResponse result = service.evaluateForRanks(USER, 20, 19);

    assertTrue(result.eligible(), "C counts as 'at least A'");
  }

  @Test
  void evaluateAllForUser_shouldReturnOneEntryPerConfiguredTransition() {
    when(rankRequirementRepository.findDistinctRankTransitions())
        .thenReturn(List.of(new Object[] {20, 19}, new Object[] {19, 18}));
    // Both transitions resolve to empty rules => not eligible / hasConfiguredRules=false
    when(rankRequirementRepository.findAllForRankTransitionWithRelations(anyInt(), anyInt()))
        .thenReturn(List.of());
    when(memberEvaluationRepository.findAllByUserIdWithCategoryAndTopic(USER))
        .thenReturn(List.of());

    List<PromotionEligibilityResponse> result = service.evaluateAllForUser(USER);

    assertEquals(2, result.size());
    assertEquals(20, result.get(0).fromRank());
    assertEquals(19, result.get(0).toRank());
    assertEquals(19, result.get(1).fromRank());
    assertEquals(18, result.get(1).toRank());
  }

  // ---------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------

  private PromotionTopic topic(String name) {
    PromotionTopic t = PromotionTopic.builder().name(name).sortOrder(0).build();
    t.setId(UUID.randomUUID());
    return t;
  }

  private PromotionCategory category(PromotionTopic topic, String name) {
    PromotionCategory c = PromotionCategory.builder().topic(topic).name(name).sortOrder(0).build();
    c.setId(UUID.randomUUID());
    return c;
  }

  private MemberEvaluation evaluation(
      String userId, PromotionCategory category, PromotionLevel level) {
    MemberEvaluation e =
        MemberEvaluation.builder().userId(userId).category(category).assignedLevel(level).build();
    e.setId(UUID.randomUUID());
    return e;
  }

  private RankRequirement categoryRule(
      PromotionCategory category, PromotionLevel min, String description) {
    RankRequirement r =
        RankRequirement.builder()
            .fromRank(20)
            .toRank(19)
            .category(category)
            .minimumLevel(min)
            .requiredCount(1)
            .description(description)
            .build();
    r.setId(UUID.randomUUID());
    return r;
  }

  private RankRequirement topicRule(
      PromotionTopic topic, PromotionLevel min, int count, String description) {
    RankRequirement r =
        RankRequirement.builder()
            .fromRank(20)
            .toRank(19)
            .topic(topic)
            .minimumLevel(min)
            .requiredCount(count)
            .description(description)
            .build();
    r.setId(UUID.randomUUID());
    return r;
  }
}

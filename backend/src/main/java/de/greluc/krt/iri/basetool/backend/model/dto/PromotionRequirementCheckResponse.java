package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PromotionLevel;
import java.util.UUID;

/**
 * Result of evaluating a single {@code RankRequirement} against a member's evaluations.
 *
 * <p>A check is either topic-scoped (then {@code topicId} is set and {@code categoryId} is {@code
 * null}) or category-scoped (then {@code categoryId} is set and {@code topicId} carries the parent
 * topic for display only). {@link #achievedCount} is the number of categories that already satisfy
 * {@link #minimumLevel} – for topic-scoped checks it is compared against {@link #requiredCount},
 * for category-scoped checks it is either {@code 0} (unsatisfied) or {@code 1} (satisfied) so the
 * same record can drive both cases in the UI.
 *
 * @param requirementId the persistent id of the underlying {@code RankRequirement}
 * @param topicId the topic this check belongs to (always populated when known)
 * @param topicName human-readable topic name for display
 * @param categoryId the specific category the check targets, or {@code null} for topic-wide
 * @param categoryName human-readable category name, or {@code null} for topic-wide
 * @param minimumLevel the minimum {@link PromotionLevel} the check demands
 * @param requiredCount how many categories must reach {@link #minimumLevel} (1 for category-wide)
 * @param achievedCount how many categories currently reach {@link #minimumLevel}
 * @param satisfied {@code true} iff {@link #achievedCount} reaches {@link #requiredCount}
 * @param description the requirement's free-text description for tooltips and rows
 */
public record PromotionRequirementCheckResponse(
    UUID requirementId,
    UUID topicId,
    String topicName,
    UUID categoryId,
    String categoryName,
    PromotionLevel minimumLevel,
    int requiredCount,
    int achievedCount,
    boolean satisfied,
    String description) {}

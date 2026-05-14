package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend's {@code PromotionRequirementCheckResponse}. {@code minimumLevel}
 * is kept as a {@link String} – the page templates only need to render its name and never compare
 * grades client-side.
 *
 * @param requirementId the persistent id of the underlying rank requirement
 * @param topicId the topic this check belongs to, {@code null} for global rules
 * @param topicName display name of {@code topicId}
 * @param categoryId the specific category targeted, or {@code null} for topic-wide rules
 * @param categoryName display name of {@code categoryId}
 * @param minimumLevel the minimum level (LEVEL_A/LEVEL_B/LEVEL_C) the rule demands
 * @param requiredCount how many categories must reach the minimum (1 for category-wide)
 * @param achievedCount how many currently reach the minimum
 * @param satisfied {@code true} iff {@code achievedCount >= requiredCount}
 * @param description free-text description rendered next to the rule
 */
public record PromotionRequirementCheckDto(
    UUID requirementId,
    UUID topicId,
    String topicName,
    UUID categoryId,
    String categoryName,
    String minimumLevel,
    int requiredCount,
    int achievedCount,
    boolean satisfied,
    String description) {}

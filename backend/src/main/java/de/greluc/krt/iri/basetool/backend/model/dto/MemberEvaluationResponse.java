package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PromotionLevel;
import java.time.Instant;
import java.util.UUID;

/**
 * Read DTO for {@code MemberEvaluation}. Note: {@code userId} is included for admin views; personal
 * views should filter by JWT sub.
 */
public record MemberEvaluationResponse(
    UUID id,
    Long version,
    String userId,
    UUID categoryId,
    String categoryName,
    UUID topicId,
    String topicName,
    PromotionLevel assignedLevel,
    Instant createdAt,
    Instant updatedAt) {}

package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PromotionLevel;
import java.time.Instant;
import java.util.UUID;

/** Read DTO for {@code RankRequirement}. */
public record RankRequirementResponse(
    UUID id,
    Long version,
    int fromRank,
    int toRank,
    UUID topicId,
    String topicName,
    UUID categoryId,
    String categoryName,
    PromotionLevel minimumLevel,
    int requiredCount,
    String description,
    Instant createdAt,
    Instant updatedAt) {}

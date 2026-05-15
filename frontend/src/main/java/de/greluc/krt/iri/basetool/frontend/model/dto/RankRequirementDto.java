package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.UUID;

/** Frontend DTO for a rank requirement entry. */
public record RankRequirementDto(
    UUID id,
    Long version,
    int fromRank,
    int toRank,
    UUID topicId,
    String topicName,
    UUID categoryId,
    String categoryName,
    String minimumLevel,
    int requiredCount,
    String description,
    Instant createdAt,
    Instant updatedAt) {}

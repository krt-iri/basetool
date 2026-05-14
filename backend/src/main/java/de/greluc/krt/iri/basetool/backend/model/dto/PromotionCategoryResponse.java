package de.greluc.krt.iri.basetool.backend.model.dto;

import java.time.Instant;
import java.util.UUID;

/** Read DTO for {@code PromotionCategory}. */
public record PromotionCategoryResponse(
    UUID id,
    Long version,
    UUID topicId,
    String topicName,
    String name,
    String description,
    int sortOrder,
    Instant createdAt,
    Instant updatedAt) {}

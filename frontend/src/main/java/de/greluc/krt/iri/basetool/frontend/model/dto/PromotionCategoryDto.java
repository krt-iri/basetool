package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.UUID;

/** Frontend DTO for a promotion category, mirroring the backend response. */
public record PromotionCategoryDto(
    UUID id,
    Long version,
    UUID topicId,
    String topicName,
    String name,
    String description,
    int sortOrder,
    Instant createdAt,
    Instant updatedAt) {}

package de.greluc.krt.iri.basetool.backend.model.dto;

import java.time.Instant;
import java.util.UUID;

/** Read DTO for {@code PromotionTopic}. */
public record PromotionTopicResponse(
    UUID id,
    Long version,
    String name,
    String description,
    int sortOrder,
    Instant createdAt,
    Instant updatedAt) {}

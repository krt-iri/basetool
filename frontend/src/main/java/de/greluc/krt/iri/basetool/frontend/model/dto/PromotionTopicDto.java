package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.UUID;

/** Frontend DTO for a promotion topic, mirroring the backend response. */
public record PromotionTopicDto(
    UUID id,
    Long version,
    String name,
    String description,
    int sortOrder,
    Instant createdAt,
    Instant updatedAt) {}

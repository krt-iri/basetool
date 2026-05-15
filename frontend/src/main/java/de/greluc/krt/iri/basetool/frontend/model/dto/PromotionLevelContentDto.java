package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.UUID;

/** Frontend DTO for a promotion level content entry. */
public record PromotionLevelContentDto(
    UUID id,
    Long version,
    UUID categoryId,
    String categoryName,
    String level,
    String description,
    Instant createdAt,
    Instant updatedAt) {}

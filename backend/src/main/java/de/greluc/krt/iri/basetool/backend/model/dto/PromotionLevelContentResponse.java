package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PromotionLevel;
import java.time.Instant;
import java.util.UUID;

/** Read DTO for {@code PromotionLevelContent}. */
public record PromotionLevelContentResponse(
    UUID id,
    Long version,
    UUID categoryId,
    String categoryName,
    PromotionLevel level,
    String description,
    Instant createdAt,
    Instant updatedAt) {}

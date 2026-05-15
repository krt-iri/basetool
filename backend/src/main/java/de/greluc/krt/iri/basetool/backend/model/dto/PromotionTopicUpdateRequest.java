package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Write DTO for updating a {@code PromotionTopic}. Includes {@code version} for optimistic locking.
 */
public record PromotionTopicUpdateRequest(
    @NotNull Long version,
    @NotBlank @Size(max = 120) String name,
    @Size(max = 2000) String description,
    @NotNull Integer sortOrder) {}

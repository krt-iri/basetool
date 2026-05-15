package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Write DTO for creating a {@code PromotionCategory}. */
public record PromotionCategoryCreateRequest(
    @NotNull UUID topicId,
    @NotBlank @Size(max = 120) String name,
    @Size(max = 2000) String description,
    @NotNull Integer sortOrder) {}

package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PromotionLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Write DTO for updating a {@code PromotionLevelContent}. Includes {@code version} for optimistic
 * locking.
 */
public record PromotionLevelContentUpdateRequest(
    @NotNull Long version,
    @NotNull UUID categoryId,
    @NotNull PromotionLevel level,
    @NotBlank @Size(max = 4000) String description) {}

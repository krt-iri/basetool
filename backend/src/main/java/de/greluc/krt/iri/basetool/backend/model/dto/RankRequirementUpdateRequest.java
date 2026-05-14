package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PromotionLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Write DTO for updating a {@code RankRequirement}. Includes {@code version} for optimistic
 * locking.
 */
public record RankRequirementUpdateRequest(
    @NotNull Long version,
    @NotNull @Min(0) Integer fromRank,
    @NotNull @Min(0) Integer toRank,
    UUID topicId,
    UUID categoryId,
    @NotNull PromotionLevel minimumLevel,
    @NotNull @Min(1) Integer requiredCount,
    @Size(max = 2000) String description) {}

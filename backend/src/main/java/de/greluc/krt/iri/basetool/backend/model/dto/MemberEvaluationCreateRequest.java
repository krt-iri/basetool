package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PromotionLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Write DTO for creating or upserting a {@code MemberEvaluation}. The {@code userId} is set
 * server-side from the JWT for self-assignments, or provided by ADMIN/OFFICER for managing other
 * users.
 */
public record MemberEvaluationCreateRequest(
    @NotBlank @Size(max = 64) String userId,
    @NotNull UUID categoryId,
    PromotionLevel assignedLevel) {}

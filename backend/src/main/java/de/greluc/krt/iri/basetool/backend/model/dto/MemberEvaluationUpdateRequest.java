package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PromotionLevel;

/**
 * Write DTO for updating a {@code MemberEvaluation}. Includes {@code version} for optimistic
 * locking.
 */
public record MemberEvaluationUpdateRequest(Long version, PromotionLevel assignedLevel) {}

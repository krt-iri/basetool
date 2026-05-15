package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.UUID;

/** Frontend DTO for a member evaluation entry. */
public record MemberEvaluationDto(
    UUID id,
    Long version,
    String userId,
    UUID categoryId,
    String categoryName,
    UUID topicId,
    String topicName,
    String assignedLevel,
    Instant createdAt,
    Instant updatedAt) {}

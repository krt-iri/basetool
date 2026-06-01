package de.greluc.krt.iri.basetool.backend.model.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Boundary DTO for one of the caller's owned blueprints (#327). The internal {@code ownerSub} is
 * intentionally never exposed.
 *
 * @param id entry primary key
 * @param productKey normalized product identity
 * @param productName display name of the owned product
 * @param outputItemId resolved output {@code game_item} id, or {@code null} if unresolved
 * @param acquiredAt optional in-game acquisition time
 * @param note optional free-form note
 * @param version optimistic-lock version
 * @param createdAt row creation timestamp
 * @param updatedAt row last-update timestamp
 */
public record PersonalBlueprintResponse(
    UUID id,
    String productKey,
    String productName,
    UUID outputItemId,
    Instant acquiredAt,
    String note,
    Long version,
    Instant createdAt,
    Instant updatedAt) {}

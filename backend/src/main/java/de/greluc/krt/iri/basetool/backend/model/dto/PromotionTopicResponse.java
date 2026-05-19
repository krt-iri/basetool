package de.greluc.krt.iri.basetool.backend.model.dto;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Read DTO for {@code PromotionTopic}. Includes a {@link SquadronReferenceDto} mini-record for the
 * owning squadron so the admin / officer UI can render the squadron column without a dedicated
 * lookup; {@code null} only on legacy rows that pre-date the squadron stamp (post-V88 every row
 * carries a value via the IRIDIUM backfill).
 */
public record PromotionTopicResponse(
    UUID id,
    Long version,
    String name,
    String description,
    int sortOrder,
    @Nullable SquadronReferenceDto owningSquadron,
    Instant createdAt,
    Instant updatedAt) {}

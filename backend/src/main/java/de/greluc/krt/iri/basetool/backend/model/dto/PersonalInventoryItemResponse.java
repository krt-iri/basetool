package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PersonalInventoryLocationType;
import java.time.Instant;
import java.util.UUID;

/**
 * Read DTO for a personal inventory entry. The owner identifier ({@code ownerSub}) is intentionally
 * NOT exposed: clients only ever see their own items via the user-scoped endpoints, and the admin
 * endpoints already provide the owner sub via the URL path.
 */
public record PersonalInventoryItemResponse(
    UUID id,
    String name,
    String note,
    Integer locationUexId,
    PersonalInventoryLocationType locationType,
    String locationName,
    Integer quantity,
    Long version,
    Instant createdAt,
    Instant updatedAt) {}

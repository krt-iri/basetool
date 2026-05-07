package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Read DTO mirroring {@code PersonalInventoryItemResponse} from the backend module.
 */
public record PersonalInventoryItemDto(
        UUID id,
        String name,
        String note,
        Integer locationUexId,
        PersonalInventoryLocationType locationType,
        String locationName,
        Integer quantity,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}

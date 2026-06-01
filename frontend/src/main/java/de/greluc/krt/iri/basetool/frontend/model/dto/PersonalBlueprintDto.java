package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Read DTO mirroring the backend {@code PersonalBlueprintResponse} (#327): one blueprint the
 * calling user owns. {@code ownerSub} is intentionally absent — it is never exposed at the
 * boundary.
 *
 * @param id entry id
 * @param productKey normalized product key
 * @param productName canonical display name
 * @param outputItemId resolved output {@code game_item} id, or {@code null}
 * @param acquiredAt in-game acquisition time, or {@code null}
 * @param note free-form note, or {@code null}
 * @param version optimistic-lock version echoed back on update
 * @param createdAt row creation timestamp
 * @param updatedAt row last-update timestamp
 */
public record PersonalBlueprintDto(
    UUID id,
    String productKey,
    String productName,
    UUID outputItemId,
    Instant acquiredAt,
    String note,
    Long version,
    Instant createdAt,
    Instant updatedAt) {}

package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One import preview row mirroring the backend {@code BlueprintImportEntryDto} (#327): an external
 * name plus how it resolved against the master product list.
 *
 * @param externalName the SCMDB {@code productName} exactly as uploaded
 * @param status the resolution outcome for this name
 * @param productKey normalized key of the resolved product, or {@code null} when unresolved
 * @param productName display name of the resolved product, or {@code null} when unresolved
 * @param outputItemId resolved output {@code game_item} id, or {@code null}
 * @param suggestedAcquiredAt acquisition time derived from the earliest SCMDB timestamp, or {@code
 *     null}
 * @param suggestions fuzzy candidates (highest score first); empty unless {@code status} is {@link
 *     BlueprintImportStatus#SUGGESTED}
 */
public record BlueprintImportEntryDto(
    String externalName,
    BlueprintImportStatus status,
    String productKey,
    String productName,
    UUID outputItemId,
    Instant suggestedAcquiredAt,
    List<BlueprintImportSuggestionDto> suggestions) {}

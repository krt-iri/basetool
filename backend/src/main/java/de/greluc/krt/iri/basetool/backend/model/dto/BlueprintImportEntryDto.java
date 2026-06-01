package de.greluc.krt.iri.basetool.backend.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One preview row of an SCMDB blueprint import (#327, Phase 4): an external name plus how it
 * resolved against the master product list. For {@link BlueprintImportStatus#MATCHED}, {@link
 * BlueprintImportStatus#MATCHED_BY_ALIAS} and {@link BlueprintImportStatus#ALREADY_OWNED} the
 * resolved-product fields are populated; for {@link BlueprintImportStatus#SUGGESTED} the {@link
 * #suggestions} list carries the fuzzy candidates and the resolved fields are {@code null}; {@link
 * BlueprintImportStatus#UNMATCHED} carries neither.
 *
 * @param externalName the SCMDB {@code productName} exactly as it appeared in the upload
 * @param status the resolution outcome for this name
 * @param productKey normalized key of the resolved product, or {@code null} when unresolved
 * @param productName display name of the resolved product, or {@code null} when unresolved
 * @param outputItemId resolved output {@code game_item} id, or {@code null} when none / unresolved
 * @param suggestedAcquiredAt acquisition time derived from the earliest SCMDB {@code ts}, or {@code
 *     null} if the export carried no timestamp
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

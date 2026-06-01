package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/**
 * Import preview result mirroring the backend {@code BlueprintImportPreviewDto} (#327): one row per
 * unique external name plus per-status counts for the summary banner.
 *
 * @param total number of unique external names parsed from the upload
 * @param matched count of directly matched rows
 * @param matchedByAlias count of alias-matched rows
 * @param suggested count of fuzzy-suggested rows
 * @param unmatched count of unmatched rows
 * @param alreadyOwned count of already-owned rows
 * @param entries the per-name preview rows, in upload order
 */
public record BlueprintImportPreviewDto(
    int total,
    int matched,
    int matchedByAlias,
    int suggested,
    int unmatched,
    int alreadyOwned,
    List<BlueprintImportEntryDto> entries) {}

package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.List;

/**
 * Result of an SCMDB blueprint import preview (#327, Phase 4): one {@link BlueprintImportEntryDto}
 * per unique external name plus per-status counts for the summary banner. No rows are persisted by
 * the preview step — the user reviews and resolves, then the frontend posts an apply request.
 *
 * @param total number of unique external names parsed from the upload
 * @param matched count of {@link BlueprintImportStatus#MATCHED} rows
 * @param matchedByAlias count of {@link BlueprintImportStatus#MATCHED_BY_ALIAS} rows
 * @param suggested count of {@link BlueprintImportStatus#SUGGESTED} rows
 * @param unmatched count of {@link BlueprintImportStatus#UNMATCHED} rows
 * @param alreadyOwned count of {@link BlueprintImportStatus#ALREADY_OWNED} rows
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

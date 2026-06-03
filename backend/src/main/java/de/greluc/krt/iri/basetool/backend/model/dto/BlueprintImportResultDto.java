package de.greluc.krt.iri.basetool.backend.model.dto;

/**
 * Summary of an applied blueprint import (#327, Phase 4). The counters partition the supplied
 * resolutions: every non-skipped resolution lands in exactly one of {@link #added} or {@link
 * #alreadyOwned}; {@link #skipped} counts blank / unresolvable rows; {@link #aliasesLearned} is an
 * independent tally of how many manual picks were persisted as future auto-matches; {@link
 * #acquiredAtUpdated} counts already-owned rows whose stored acquisition time a re-import pulled
 * earlier (a subset of {@link #alreadyOwned}).
 *
 * @param added number of new {@code personal_blueprint} rows created
 * @param aliasesLearned number of new {@code blueprint_external_alias} rows persisted
 * @param skipped number of resolutions skipped (blank choice or unresolvable product key)
 * @param alreadyOwned number of resolutions whose product the caller already owned
 * @param acquiredAtUpdated number of already-owned rows whose acquisition time this import pulled
 *     earlier (subset of {@link #alreadyOwned})
 */
public record BlueprintImportResultDto(
    int added, int aliasesLearned, int skipped, int alreadyOwned, int acquiredAtUpdated) {}

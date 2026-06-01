package de.greluc.krt.iri.basetool.backend.model.dto;

/**
 * Summary of an applied SCMDB blueprint import (#327, Phase 4). The four counters partition the
 * supplied resolutions: every non-skipped resolution lands in exactly one of {@link #added} or
 * {@link #alreadyOwned}; {@link #skipped} counts blank / unresolvable rows; {@link #aliasesLearned}
 * is an independent tally of how many manual picks were persisted as future auto-matches.
 *
 * @param added number of new {@code personal_blueprint} rows created
 * @param aliasesLearned number of new {@code blueprint_external_alias} rows persisted
 * @param skipped number of resolutions skipped (blank choice or unresolvable product key)
 * @param alreadyOwned number of resolutions whose product the caller already owned
 */
public record BlueprintImportResultDto(
    int added, int aliasesLearned, int skipped, int alreadyOwned) {}

package de.greluc.krt.iri.basetool.backend.model.dto;

/**
 * Outcome summary of a multi-select batch add (#327). A batch never fails as a whole: every key is
 * either added or counted into one of the skip buckets.
 *
 * @param added number of blueprints newly added to the owned set
 * @param skippedAlreadyOwned number of keys skipped because the caller already owned them (or the
 *     same key appeared twice in the request)
 * @param skippedUnresolved number of keys skipped because they are blank or match no active product
 */
public record PersonalBlueprintBatchResult(
    int added, int skippedAlreadyOwned, int skippedUnresolved) {}

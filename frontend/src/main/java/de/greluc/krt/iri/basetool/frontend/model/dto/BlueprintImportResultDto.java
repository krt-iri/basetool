package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Applied-import summary mirroring the backend {@code BlueprintImportResultDto} (#327), surfaced to
 * the user as a toast.
 *
 * @param added number of new owned-blueprint rows created
 * @param aliasesLearned number of new SCMDB aliases persisted
 * @param skipped number of resolutions skipped (blank / unresolvable)
 * @param alreadyOwned number of resolutions whose product was already owned
 */
public record BlueprintImportResultDto(
    int added, int aliasesLearned, int skipped, int alreadyOwned) {}

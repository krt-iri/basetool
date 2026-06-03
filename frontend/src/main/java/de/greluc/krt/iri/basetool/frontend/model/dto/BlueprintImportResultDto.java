package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Applied-import summary mirroring the backend {@code BlueprintImportResultDto} (#327), surfaced to
 * the user as a toast.
 *
 * @param added number of new owned-blueprint rows created
 * @param aliasesLearned number of new aliases persisted
 * @param skipped number of resolutions skipped (blank / unresolvable)
 * @param alreadyOwned number of resolutions whose product was already owned
 * @param acquiredAtUpdated number of already-owned rows whose acquisition time this import pulled
 *     earlier
 */
public record BlueprintImportResultDto(
    int added, int aliasesLearned, int skipped, int alreadyOwned, int acquiredAtUpdated) {}

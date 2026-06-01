package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Read DTO mirroring the backend {@code PersonalBlueprintBatchResult} (#327): the outcome of a
 * multi-select add, surfaced to the user as a toast.
 *
 * @param added number of blueprints newly added to the owned set
 * @param skippedAlreadyOwned number of keys skipped because they were already owned
 * @param skippedUnresolved number of keys skipped because they matched no active product
 */
public record PersonalBlueprintBatchResultDto(
    int added, int skippedAlreadyOwned, int skippedUnresolved) {}

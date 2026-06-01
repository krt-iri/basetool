package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Write payload for the multi-select add: a batch of normalized product keys to add to the caller's
 * owned set in one call (#327). Already-owned or unresolvable keys are skipped, not rejected — see
 * {@link PersonalBlueprintBatchResult}.
 *
 * @param productKeys the normalized product keys to add; at least one, each non-blank
 */
public record PersonalBlueprintBatchCreateRequest(@NotEmpty List<@NotBlank String> productKeys) {}

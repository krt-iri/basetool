package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * One resolved row of an SCMDB import apply request (#327, Phase 4): the user's decision for a
 * single external name. A blank / {@code null} {@link #productKey} means "skip this name". When the
 * chosen product does not already match the external name by normalization, the apply step learns a
 * {@code blueprint_external_alias} so future imports auto-resolve it.
 *
 * @param externalName the SCMDB {@code productName} this decision applies to (from the preview)
 * @param productKey normalized key of the chosen product, or blank / {@code null} to skip
 * @param acquiredAt optional acquisition time to stamp (typically the preview's suggestion)
 * @param note optional free-form note (max 2000 chars)
 */
public record BlueprintImportResolutionDto(
    @NotBlank String externalName,
    String productKey,
    Instant acquiredAt,
    @Size(max = 2000) String note) {}

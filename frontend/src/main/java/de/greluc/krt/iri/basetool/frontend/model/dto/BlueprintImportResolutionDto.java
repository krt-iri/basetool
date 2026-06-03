package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;

/**
 * One resolved import row mirroring the backend {@code BlueprintImportResolutionDto} (#327): the
 * user's decision for a single external name. A blank {@code productKey} means "skip".
 *
 * @param externalName the external name this decision applies to
 * @param productKey normalized key of the chosen product, or blank to skip
 * @param acquiredAt optional acquisition time to stamp
 * @param note optional free-form note
 */
public record BlueprintImportResolutionDto(
    String externalName, String productKey, Instant acquiredAt, String note) {}

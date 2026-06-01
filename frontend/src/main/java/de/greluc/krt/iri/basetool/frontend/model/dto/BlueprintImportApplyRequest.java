package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/**
 * Outbound write DTO mirroring the backend {@code BlueprintImportApplyRequest} (#327): the user's
 * per-name resolutions in one batch, posted to the backend import-apply endpoint.
 *
 * @param resolutions the per-name decisions
 */
public record BlueprintImportApplyRequest(List<BlueprintImportResolutionDto> resolutions) {}

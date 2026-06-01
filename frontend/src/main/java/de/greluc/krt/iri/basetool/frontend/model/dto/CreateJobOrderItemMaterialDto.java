package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend {@code CreateJobOrderItemMaterialDto}: the requester's
 * per-material quality choice for one item line. {@code quality} is the {@code QualityRequirement}
 * name ({@code GOOD} or {@code NONE}) sent as a string.
 *
 * @param materialId the material the choice applies to
 * @param quality the requested quality ({@code GOOD} = 700+, {@code NONE} = no floor)
 */
public record CreateJobOrderItemMaterialDto(UUID materialId, String quality) {}

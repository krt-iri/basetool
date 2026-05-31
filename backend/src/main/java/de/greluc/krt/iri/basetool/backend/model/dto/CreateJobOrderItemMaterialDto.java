package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.QualityRequirement;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Per-material quality choice the requester makes for one ordered item line. The server re-derives
 * the required quantity from the blueprint (authoritative) and applies this {@code quality} to the
 * matching derived material; a material the client omits falls back to the blueprint-derived
 * default (GOOD when the ingredient's {@code minQuality} is 700+, else NONE).
 *
 * @param materialId the material this choice applies to
 * @param quality the requested quality floor ({@code GOOD} = 700+, {@code NONE} = no floor)
 */
public record CreateJobOrderItemMaterialDto(
    @NotNull UUID materialId, @NotNull QualityRequirement quality) {}

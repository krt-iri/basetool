package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Write payload for applying an SCMDB blueprint import (#327, Phase 4): the user's per-name
 * resolutions in one batch (multi-row resolve). Names with a blank product key are skipped; the
 * remainder are added to the caller's owned set and, where the choice was manual, recorded as an
 * alias. The outcome is summarized in {@link BlueprintImportResultDto}.
 *
 * @param resolutions the per-name decisions; never {@code null}, may be empty (a no-op apply)
 */
public record BlueprintImportApplyRequest(
    @NotNull @Valid List<BlueprintImportResolutionDto> resolutions) {}

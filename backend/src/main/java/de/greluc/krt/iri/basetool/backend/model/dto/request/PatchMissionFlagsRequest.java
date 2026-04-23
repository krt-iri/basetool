package de.greluc.krt.iri.basetool.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO fuer ein partielles Update der Flags-Section eines Einsatzes (z.B. isInternal).
 *
 * <p>Section-Patches erlauben es mehreren Nutzern parallel an verschiedenen Bereichen der
 * Einsatz-Detailseite zu arbeiten, ohne dass Sub-Aenderungen (Teilnehmer, Units, Finanzen)
 * dank {@code @OptimisticLock(excluded = true)} zu einer Parent-Version-Kollision fuehren.
 */
public record PatchMissionFlagsRequest(
        @NotNull Boolean isInternal,
        @NotNull Long version
) {
}
